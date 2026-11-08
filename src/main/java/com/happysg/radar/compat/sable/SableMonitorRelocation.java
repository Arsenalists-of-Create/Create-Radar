
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorMultiBlockHelper;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import java.util.*;

public final class SableMonitorRelocation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, List<Move>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> MOVING_FROM = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, PreparedMonitor>> PREPARED = new WeakHashMap<>();

    private record PreparedMonitor(BlockPos controller, int size, Direction facing) {}
    private record Move(
            ServerLevel originLevel,
            BlockPos oldPos,
            BlockPos newPos,
            BlockPos oldController,
            int size,
            Direction facing
    ) {}

    private record GroupKey(ServerLevel originLevel, BlockPos oldController) {}
    private SableMonitorRelocation() {}

    public static void prepare(ServerLevel level, BlockPos oldPos) {
        if (!(level.getBlockEntity(oldPos) instanceof MonitorBlockEntity monitor)) {
            return;
        }

        int size = monitor.getSize();
        if (size < 1 || size > RadarConfig.server().monitorMaxSize.get()) {
            return;
        }

        BlockPos controller = monitor.getControllerPos().immutable();
        Direction facing = monitor.getBlockState().getValue(MonitorBlock.FACING);

        PREPARED.computeIfAbsent(level, ignored -> new HashMap<>()).put(oldPos.immutable(), new PreparedMonitor(controller, size, facing));
        MOVING_FROM.computeIfAbsent(level, ignored -> new HashSet<>()).add(oldPos.immutable());
    }

    public static void capture(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        Map<BlockPos, PreparedMonitor> preparedMap = PREPARED.get(originLevel);
        PreparedMonitor prepared = preparedMap == null ? null : preparedMap.remove(oldPos);
        if (preparedMap != null && preparedMap.isEmpty()) {
            PREPARED.remove(originLevel);
        }

        if (prepared == null) {
            LOGGER.warn("Missing prepared monitor snapshot for Sable move {} -> {}", oldPos, newPos);
            return;
        }

        if (!(resultingLevel.getBlockEntity(newPos) instanceof MonitorBlockEntity)) {
            LOGGER.warn("Destination monitor block entity unavailable during Sable move {} -> {}", oldPos, newPos);
            return;
        }

        PENDING.computeIfAbsent(resultingLevel, ignored -> new ArrayList<>()).add(new Move(originLevel, oldPos.immutable(), newPos.immutable(), prepared.controller(), prepared.size(), prepared.facing()));
        MOVING_FROM.computeIfAbsent(originLevel, ignored -> new HashSet<>()).add(oldPos.immutable());
    }

    public static boolean isRelocating(ServerLevel level, BlockPos pos) {
        Set<BlockPos> moving = MOVING_FROM.get(level);
        return moving != null && moving.contains(pos);
    }

    public static void process(ServerLevel level) {
        List<Move> moves = PENDING.remove(level);

        if (moves == null || moves.isEmpty()) {
            MOVING_FROM.remove(level);
            return;
        }

        try {
            Map<GroupKey, List<Move>> groups = new LinkedHashMap<>();
            for (Move move : moves) {
                groups.computeIfAbsent(new GroupKey(move.originLevel(), move.oldController()), ignored -> new ArrayList<>()).add(move);
            }

            for (List<Move> group : groups.values()) {
                try {
                    restoreGroup(level, group);
                } catch (RuntimeException exception) {
                    LOGGER.error("Failed to restore relocated monitor at {}", group.getFirst().oldController(), exception);
                }
            }
        } finally {
            for (Move move : moves) {
                Set<BlockPos> moving = MOVING_FROM.get(move.originLevel());
                if (moving == null) {
                    continue;
                }

                moving.remove(move.oldPos());

                if (moving.isEmpty()) {
                    MOVING_FROM.remove(move.originLevel());
                }
            }
        }
    }

    private static void restoreGroup(ServerLevel level, List<Move> moves) {
        Map<BlockPos, Move> byOldPosition = new HashMap<>();
        for (Move move : moves) {
            byOldPosition.put(move.oldPos(), move);
        }

        Move first = moves.getFirst();
        Move controllerMove = byOldPosition.get(first.oldController());

        if (controllerMove == null) {
            LOGGER.warn("Monitor relocation at {} has no moved controller", first.oldController());
            return;
        }

        int size = controllerMove.size();
        BlockPos oldController = controllerMove.oldController();
        BlockPos newController = controllerMove.newPos();
        Set<BlockPos> leftBehindLinks = first.originLevel() == level && !oldController.equals(newController) ? collectLeftBehindMonitorLinks(level, oldController) : Set.of();
        Direction oldFacing = controllerMove.facing();
        Direction oldRight = oldFacing.getClockWise();

        BlockState destinationState = level.getBlockState(newController);

        if (!destinationState.is(ModBlocks.MONITOR.get())) {
            LOGGER.warn("Relocated monitor controller missing at {}", newController);
            return;
        }

        Direction newFacing = destinationState.getValue(MonitorBlock.FACING);
        Direction newRight = newFacing.getClockWise();

        if (moves.size() != size * size) {
            LOGGER.warn("Incomplete monitor relocation at {}: {} of {} blocks", oldController, moves.size(), size * size);
            return;
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                BlockPos oldMember = oldController.above(y).relative(oldRight, x);
                BlockPos newMember = newController.above(y).relative(newRight, x);
                Move moved = byOldPosition.get(oldMember);

                if (moved == null || !newMember.equals(moved.newPos())) {
                    LOGGER.warn("Monitor relocation geometry mismatch at {}", oldMember);
                    return;
                }

                BlockState state = level.getBlockState(newMember);
                if (!state.is(ModBlocks.MONITOR.get()) || state.getValue(MonitorBlock.FACING) != newFacing || !(level.getBlockEntity(newMember) instanceof MonitorBlockEntity)) {
                    LOGGER.warn("Relocated monitor piece missing at {}", newMember);
                    return;
                }
            }
        }

        NetworkData network = NetworkData.get(level);
        ARADData arad = ARADData.get(level);
        var dimension = level.dimension();
        boolean networkLinked = network.isEndpointLinked(dimension, oldController);
        boolean aradLinked = arad.isMonitorLinked(dimension, oldController);

        if (!oldController.equals(newController)) {
            boolean networkConflict = networkLinked
                    && network.isEndpointLinked(dimension, newController)
                    && !Objects.equals(network.getFiltererForEndpoint(dimension, oldController), network.getFiltererForEndpoint(dimension, newController));

            boolean aradConflict = aradLinked
                    && arad.isMonitorLinked(dimension, newController)
                    && !Objects.equals(arad.getRwrForMonitor(dimension, oldController), arad.getRwrForMonitor(dimension, newController));

            if (networkConflict || aradConflict) {
                LOGGER.warn("Monitor relocation destination owned by another network: {}", newController);
                return;
            }
        }

        if (networkLinked && !network.updateMonitorPosition(dimension, oldController, newController)) {
            LOGGER.warn("Could not relocate monitor network {} -> {}", oldController, newController);
            return;
        }

        if (aradLinked && !arad.updateMonitorPosition(dimension, oldController, newController)) {
            if (networkLinked && !network.updateMonitorPosition(dimension, newController, oldController)) {
                LOGGER.error("Could not roll back monitor network relocation {} -> {}", newController, oldController);
            }

            LOGGER.warn("Could not relocate ARAD monitor {} -> {}", oldController, newController);
            return;
        }

        MonitorMultiBlockHelper.restoreAfterSableMove(level, newController, size);
        for (BlockPos linkPos : leftBehindLinks) {
            disconnectIfLeftBehind(level, linkPos, oldController);
        }

        if (level.getBlockEntity(newController) instanceof MonitorBlockEntity monitor && monitor.isController()) {
            monitor.refreshAfterSableMove();
        }
    }

    public static void clear(ServerLevel level) {
        PENDING.remove(level);
        MOVING_FROM.remove(level);
        PREPARED.remove(level);

        for (Map<BlockPos, PreparedMonitor> prepared : PREPARED.values()) {
            prepared.clear();
        }

        for (List<Move> moves : PENDING.values()) {
            moves.removeIf(move -> move.originLevel() == level);
        }
    }

    private static Set<BlockPos> collectLeftBehindMonitorLinks(ServerLevel level, BlockPos oldController) {
        var dim = level.dimension();
        Set<BlockPos> links = new HashSet<>();

        NetworkData network = NetworkData.get(level);
        NetworkData.Group networkGroup = network.getGroupForEndpoint(dim, oldController);

        if (networkGroup != null) {
            for (BlockPos linkPos : List.copyOf(networkGroup.dataLinks)) {
                if (oldController.equals(network.peekEndpointForDataLink(dim, linkPos))) {
                    links.add(linkPos);
                }
            }
        }

        ARADData arad = ARADData.get(level);
        BlockPos rwrPos = arad.getRwrForMonitor(dim, oldController);

        if (rwrPos != null) {
            ARADData.Group aradGroup = arad.getGroup(dim, rwrPos);

            if (aradGroup != null) {
                for (BlockPos linkPos : List.copyOf(aradGroup.dataLinks)) {
                    if (oldController.equals(arad.peekEndpointForDataLink(dim, linkPos))) {
                        links.add(linkPos);
                    }
                }
            }
        }

        return links;
    }

    private static void disconnectIfLeftBehind(ServerLevel level, BlockPos linkPos, BlockPos oldController) {
        if (SableDataLinkRelocation.isRelocating(level, linkPos) || !level.hasChunkAt(linkPos)) {
            return;
        }

        if (!(level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link)) {
            return;
        }

        if (link.getBlockState().getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.RADAR) {
            return;
        }

        BlockPos sourcePos = link.getSourcePosition();
        if (!level.hasChunkAt(sourcePos)) {
            return;
        }

        if (level.getBlockEntity(sourcePos) instanceof MonitorBlockEntity monitor && oldController.equals(monitor.getControllerPos())) {
            return;
        }

        DataLinkBlock.disconnectSavedLink(level, linkPos);
        LOGGER.debug("Disconnected stationary Data Link {} after monitor relocation {}", linkPos, oldController);
    }
}
