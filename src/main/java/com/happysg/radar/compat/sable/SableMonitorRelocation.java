
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

    public static void capture(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        if (!(originLevel.getBlockEntity(oldPos) instanceof MonitorBlockEntity oldMonitor) || !(resultingLevel.getBlockEntity(newPos) instanceof MonitorBlockEntity)) {
            return;
        }

        int size = oldMonitor.getSize();
        if (size < 1 || size > RadarConfig.server().monitorMaxSize.get()) {
            return;
        }

        BlockPos controller = oldMonitor.getControllerPos().immutable();
        Direction facing = oldMonitor.getBlockState().getValue(MonitorBlock.FACING);

        PENDING.computeIfAbsent(resultingLevel, ignored -> new ArrayList<>()).add(new Move(
                originLevel,
                oldPos.immutable(),
                newPos.immutable(),
                controller,
                size,
                facing
        ));

        Set<BlockPos> moving = MOVING_FROM.computeIfAbsent(originLevel, ignored -> new HashSet<>());

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                BlockPos member = controller.above(y).relative(facing.getClockWise(), x);

                if (originLevel.getBlockEntity(member) instanceof MonitorBlockEntity monitor && controller.equals(monitor.getControllerPos())) {
                    moving.add(member.immutable());
                }
            }
        }
    }

    public static boolean isRelocating(ServerLevel level, BlockPos pos) {
        Set<BlockPos> moving = MOVING_FROM.get(level);
        return moving != null && moving.contains(pos);
    }

    public static void process(ServerLevel level) {
        List<Move> moves = PENDING.remove(level);

        if (moves == null || moves.isEmpty()) {
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

                for (int y = 0; y < move.size(); y++) {
                    for (int x = 0; x < move.size(); x++) {
                        moving.remove(move.oldController().above(y).relative(move.facing().getClockWise(), x));
                    }
                }

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
        if (first.originLevel() == level) {
            disconnectLeftBehindMonitorLinks(level, oldController);
        }
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
            LOGGER.warn("Could not relocate ARAD monitor {} -> {}", oldController, newController);
            return;
        }

        MonitorMultiBlockHelper.restoreAfterSableMove(level, newController, size);
    }

    public static void clear(ServerLevel level) {
        PENDING.remove(level);
        MOVING_FROM.remove(level);

        for (List<Move> moves : PENDING.values()) {
            moves.removeIf(move -> move.originLevel() == level);
        }
    }

    private static void disconnectLeftBehindMonitorLinks(ServerLevel level, BlockPos oldController) {
        var dim = level.dimension();

        NetworkData network = NetworkData.get(level);
        NetworkData.Group networkGroup = network.getGroupForEndpoint(dim, oldController);

        if (networkGroup != null) {
            for (BlockPos linkPos : List.copyOf(networkGroup.dataLinks)) {
                if (oldController.equals(network.peekEndpointForDataLink(dim, linkPos))) {
                    disconnectIfLeftBehind(level, linkPos, oldController);
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
                        disconnectIfLeftBehind(level, linkPos, oldController);
                    }
                }
            }
        }
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
