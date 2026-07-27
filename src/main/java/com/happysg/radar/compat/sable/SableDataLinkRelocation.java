package com.happysg.radar.compat.sable;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bridges Sable's block-copy move into the position-keyed weapon network.
 *
 * <p>This class intentionally has no Sable API types in its signature so core
 * block removal and level lifecycle code can reference it when Sable is not
 * installed.</p>
 */
public final class SableDataLinkRelocation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, List<PendingMove>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> MOVING_FROM = new WeakHashMap<>();

    private SableDataLinkRelocation() {
    }

    public static void capture(ServerLevel originLevel, ServerLevel resultingLevel,
                               BlockPos oldPos, BlockPos newPos) {
        if (!(originLevel.getBlockEntity(oldPos) instanceof DataLinkBlockEntity oldLink)
                || !(resultingLevel.getBlockEntity(newPos) instanceof DataLinkBlockEntity newLink)) {
            return;
        }
        if (!oldLink.getBlockState().hasProperty(DataLinkBlock.LINK_STYLE)) {
            return;
        }

        BlockPos delta = newPos.subtract(oldPos);
        BlockPos oldSource = oldLink.getSourcePosition().immutable();
        BlockPos oldTarget = oldLink.getTargetPosition().immutable();
        Block oldSourceBlock = originLevel.getBlockState(oldSource).getBlock();
        Block oldTargetBlock = originLevel.getBlockState(oldTarget).getBlock();
        DataLinkBlock.LinkStyle style = oldLink.getBlockState().getValue(DataLinkBlock.LINK_STYLE);

        PendingMove move = new PendingMove(
                originLevel,
                oldPos.immutable(),
                newPos.immutable(),
                oldSource,
                oldSource.offset(delta),
                oldTarget,
                oldTarget.offset(delta),
                oldSourceBlock,
                oldTargetBlock,
                style,
                oldLink.getWeaponEndpointType()
        );
        PENDING.computeIfAbsent(resultingLevel, ignored -> new ArrayList<>()).add(move);
        MOVING_FROM.computeIfAbsent(originLevel, ignored -> new HashSet<>()).add(oldPos.immutable());
        newLink.markAssemblyRelocated();
    }

    public static boolean isRelocating(ServerLevel level, BlockPos position) {
        Set<BlockPos> moving = MOVING_FROM.get(level);
        return moving != null && moving.contains(position);
    }

    public static void process(ServerLevel level) {
        List<PendingMove> moves = PENDING.remove(level);
        if (moves == null || moves.isEmpty()) {
            return;
        }

        // Controller links establish the canonical mount relocation before a
        // radar-style link migrates its saved endpoint and DataLink position.
        moves.sort(Comparator.comparingInt(move ->
                move.style() == DataLinkBlock.LinkStyle.CONTROLLER ? 0 : 1));

        for (PendingMove move : moves) {
            try {
                processMove(level, move);
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to migrate DataLink topology from {} to {}",
                        move.oldDataLink(), move.newDataLink(), exception);
            } finally {
                Set<BlockPos> moving = MOVING_FROM.get(move.originLevel());
                if (moving != null) {
                    moving.remove(move.oldDataLink());
                    if (moving.isEmpty()) {
                        MOVING_FROM.remove(move.originLevel());
                    }
                }
            }
        }
    }

    public static void clear(ServerLevel level) {
        PENDING.remove(level);
        MOVING_FROM.remove(level);
        for (List<PendingMove> moves : PENDING.values()) {
            moves.removeIf(move -> move.originLevel() == level);
        }
    }

    private static void processMove(ServerLevel level, PendingMove move) {
        if (!(level.getBlockEntity(move.newDataLink()) instanceof DataLinkBlockEntity newLink)) {
            LOGGER.warn("Relocated DataLink at {} was unavailable; topology remains detached",
                    move.newDataLink());
            return;
        }
        if (level.getBlockState(move.newSource()).getBlock() != move.oldSourceBlock()
                || level.getBlockState(move.newTarget()).getBlock() != move.oldTargetBlock()) {
            LOGGER.warn("Relocated DataLink {} did not retain its source/target blocks; "
                            + "refusing topology migration",
                    move.newDataLink());
            return;
        }

        NetworkData networkData = NetworkData.get(level);
        NetworkData.WeaponRelocationResult weaponResult =
                NetworkData.WeaponRelocationResult.NOT_FOUND;

        if (move.style() == DataLinkBlock.LinkStyle.CONTROLLER
                && move.endpointType() != DataLinkBlockEntity.WeaponEndpointType.NONE) {
            weaponResult = networkData.relocateWeaponEndpoint(
                    level.dimension(),
                    move.oldSource(),
                    move.newSource(),
                    move.oldTarget(),
                    move.newTarget()
            );
            if (weaponResult == NetworkData.WeaponRelocationResult.CONFLICT) {
                WeaponNetworkRuntime.get(level).unregister(move.newDataLink());
                LOGGER.warn("Refused foreign-owned weapon relocation from mount {} to {}",
                        move.oldTarget(), move.newTarget());
                return;
            }

            if (!WeaponNetworkRuntime.get(level).relocateMountGroup(
                    move.oldTarget(), move.newTarget(), newLink, move.newSource())) {
                LOGGER.warn("Refused ambiguous weapon group relocation from mount {} to {}",
                        move.oldTarget(), move.newTarget());
                return;
            }
            invalidateControllers(level, move.newTarget());
        } else {
            BlockPos savedEndpoint = networkData.peekEndpointForDataLink(
                    level.dimension(), move.oldDataLink());
            if (savedEndpoint == null) {
                savedEndpoint = networkData.peekEndpointForDataLink(
                        level.dimension(), move.newDataLink());
            }
            if (move.oldTarget().equals(savedEndpoint)) {
                BlockPos savedMount = networkData.getWeaponMountForController(
                        level.dimension(), move.oldTarget());
                if (savedMount != null) {
                    weaponResult = networkData.relocateWeaponEndpoint(
                            level.dimension(),
                            move.oldTarget(),
                            move.newTarget(),
                            savedMount,
                            savedMount.offset(move.newDataLink().subtract(move.oldDataLink()))
                    );
                }
            }
            newLink.finishAssemblyRelocation(move.newTarget());
        }

        boolean networkLinkMoved = networkData.updateDataLinkPosition(
                level.dimension(), move.oldDataLink(), move.newDataLink());
        ARADData.get(level).updateDataLinkPosition(
                level.dimension(), move.oldDataLink(), move.newDataLink());

        BlockPos filtererPos = networkData.getFiltererForDataLink(
                level.dimension(), move.newDataLink());
        if (filtererPos == null && weaponResult == NetworkData.WeaponRelocationResult.UPDATED) {
            filtererPos = networkData.getFiltererForEndpoint(
                    level.dimension(), move.newSource());
        }
        if (filtererPos != null
                && level.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity filterer) {
            filterer.onWeaponTopologyChanged();
        }

        if (networkLinkMoved) {
            newLink.markAssemblyRelocated();
        }
    }

    private static void invalidateControllers(ServerLevel level, BlockPos mountPos) {
        WeaponNetworkRuntime.WeaponGroupView group =
                WeaponNetworkRuntime.get(level).getWeaponGroupView(mountPos);
        if (group == null) {
            return;
        }
        for (BlockPos endpoint : group.endpoints()) {
            BlockEntity blockEntity = level.getBlockEntity(endpoint);
            if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.markMountDirtyExternal();
                if (pitch.firingControl != null) {
                    pitch.firingControl.refreshControllers();
                }
            } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
                yaw.markMountDirtyExternal();
            }
        }
    }

    private record PendingMove(
            ServerLevel originLevel,
            BlockPos oldDataLink,
            BlockPos newDataLink,
            BlockPos oldSource,
            BlockPos newSource,
            BlockPos oldTarget,
            BlockPos newTarget,
            Block oldSourceBlock,
            Block oldTargetBlock,
            DataLinkBlock.LinkStyle style,
            DataLinkBlockEntity.WeaponEndpointType endpointType
    ) {
    }
}
