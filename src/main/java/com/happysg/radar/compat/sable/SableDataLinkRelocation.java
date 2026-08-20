package com.happysg.radar.compat.sable;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final Map<ServerLevel, Set<BlockPos>> MOVING_ENDPOINTS =
            new WeakHashMap<>();

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
        MOVING_ENDPOINTS.computeIfAbsent(originLevel,
                ignored -> new HashSet<>()).add(oldSource);
        newLink.markAssemblyRelocated();
    }

    public static boolean isRelocating(ServerLevel level, BlockPos position) {
        Set<BlockPos> moving = MOVING_FROM.get(level);
        return moving != null && moving.contains(position);
    }

    public static boolean isRelocatingEndpoint(ServerLevel level,
                                               BlockPos position) {
        Set<BlockPos> moving = MOVING_ENDPOINTS.get(level);
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

        Map<WeaponMoveKey, List<PendingMove>> weaponMoves =
                new LinkedHashMap<>();
        List<PendingMove> otherMoves = new ArrayList<>();
        for (PendingMove move : moves) {
            if (isWeaponControllerMove(move)) {
                WeaponMoveKey key = new WeaponMoveKey(move.originLevel(),
                        move.oldTarget(), move.newTarget());
                weaponMoves.computeIfAbsent(key,
                        ignored -> new ArrayList<>()).add(move);
            } else {
                otherMoves.add(move);
            }
        }

        for (List<PendingMove> group : weaponMoves.values()) {
            processMoveGroup(level, group, true);
        }
        for (PendingMove move : otherMoves) {
            processMoveGroup(level, List.of(move), false);
        }
    }

    private static void processMoveGroup(ServerLevel level,
                                         List<PendingMove> moves,
                                         boolean weaponGroup) {
        PendingMove representative = moves.getFirst();
        try {
            if (weaponGroup) {
                processWeaponGroup(level, moves);
            } else {
                processMove(level, representative);
            }
        } catch (RuntimeException exception) {
            DiagnosticRecorder.error("sable", "datalink_relocation",
                    "topology_migration_failed", exception, level,
                    representative.newDataLink(), "sable");
            LOGGER.error("Failed to migrate DataLink topology from {} to {}",
                    representative.oldDataLink(),
                    representative.newDataLink(), exception);
        } finally {
            for (PendingMove move : moves) {
                clearMovingMarker(move);
            }
        }
    }

    private static void clearMovingMarker(PendingMove move) {
        Set<BlockPos> moving = MOVING_FROM.get(move.originLevel());
        if (moving != null) {
            moving.remove(move.oldDataLink());
            if (moving.isEmpty()) {
                MOVING_FROM.remove(move.originLevel());
            }
        }

        Set<BlockPos> endpoints = MOVING_ENDPOINTS.get(move.originLevel());
        if (endpoints != null) {
            endpoints.remove(move.oldSource());
            if (endpoints.isEmpty()) {
                MOVING_ENDPOINTS.remove(move.originLevel());
            }
        }
    }

    private static boolean isWeaponControllerMove(PendingMove move) {
        return move.style() == DataLinkBlock.LinkStyle.CONTROLLER
                && move.endpointType()
                != DataLinkBlockEntity.WeaponEndpointType.NONE;
    }

    public static void clear(ServerLevel level) {
        PENDING.remove(level);
        MOVING_FROM.remove(level);
        MOVING_ENDPOINTS.remove(level);
        for (List<PendingMove> moves : PENDING.values()) {
            moves.removeIf(move -> move.originLevel() == level);
        }
    }

    private static void processWeaponGroup(ServerLevel level,
                                           List<PendingMove> moves) {
        List<PreparedWeaponMove> prepared = new ArrayList<>();
        for (PendingMove move : moves) {
            DataLinkBlockEntity newLink = relocatedLink(level, move);
            if (newLink == null) {
                detachRelocatedLinks(level, prepared);
                return;
            }
            prepared.add(new PreparedWeaponMove(move, newLink));
        }

        NetworkData networkData = NetworkData.get(level);
        Map<PendingMove, NetworkData.WeaponRelocationResult> results =
                new LinkedHashMap<>();
        try {
            for (PreparedWeaponMove entry : prepared) {
                PendingMove move = entry.move();
                NetworkData.WeaponRelocationResult result =
                        networkData.relocateWeaponEndpoint(
                                level.dimension(), move.oldSource(),
                                move.newSource(), move.oldTarget(),
                                move.newTarget());
                results.put(move, result);
                if (result == NetworkData.WeaponRelocationResult.CONFLICT) {
                    rollbackSavedWeaponRelocations(networkData, level,
                            prepared, results);
                    detachRelocatedLinks(level, prepared);
                    LOGGER.warn("Refused foreign-owned weapon relocation from mount {} to {}",
                            move.oldTarget(), move.newTarget());
                    return;
                }
            }
        } catch (RuntimeException exception) {
            rollbackSavedWeaponRelocations(networkData, level,
                    prepared, results);
            detachRelocatedLinks(level, prepared);
            throw exception;
        }

        PendingMove first = moves.getFirst();
        NetworkData.WeaponMountReferenceRelocation stationaryRelocation;
        try {
            stationaryRelocation = networkData.relocateWeaponMountReferences(
                    level.dimension(), first.oldTarget(), first.newTarget());
        } catch (RuntimeException exception) {
            rollbackSavedWeaponRelocations(networkData, level,
                    prepared, results);
            detachRelocatedLinks(level, prepared);
            throw exception;
        }
        if (stationaryRelocation.result()
                == NetworkData.WeaponRelocationResult.CONFLICT) {
            rollbackSavedWeaponRelocations(networkData, level,
                    prepared, results);
            detachRelocatedLinks(level, prepared);
            LOGGER.warn("Refused foreign-owned stationary controller relocation from mount {} to {}",
                    first.oldTarget(), first.newTarget());
            return;
        }

        Map<DataLinkBlockEntity, BlockPos> movedEndpoints =
                new LinkedHashMap<>();
        for (PreparedWeaponMove entry : prepared) {
            movedEndpoints.put(entry.link(), entry.move().newSource());
        }

        boolean runtimeRelocated;
        try {
            runtimeRelocated = WeaponNetworkRuntime.get(level)
                    .relocateMountGroup(first.oldTarget(), first.newTarget(),
                            movedEndpoints);
        } catch (RuntimeException exception) {
            networkData.rollbackWeaponMountReferences(
                    stationaryRelocation);
            rollbackSavedWeaponRelocations(networkData, level,
                    prepared, results);
            detachRelocatedLinks(level, prepared);
            throw exception;
        }
        if (!runtimeRelocated) {
            networkData.rollbackWeaponMountReferences(
                    stationaryRelocation);
            rollbackSavedWeaponRelocations(networkData, level,
                    prepared, results);
            detachRelocatedLinks(level, prepared);
            LOGGER.warn("Refused ambiguous weapon group relocation from mount {} to {}",
                    first.oldTarget(), first.newTarget());
            return;
        }

        invalidateControllers(level, first.newTarget());
        for (PreparedWeaponMove entry : prepared) {
            finishRelocatedIndexes(level, entry.move(), entry.link(),
                    results.get(entry.move()));
        }
    }

    private static DataLinkBlockEntity relocatedLink(ServerLevel level,
                                                      PendingMove move) {
        if (!(level.getBlockEntity(move.newDataLink())
                instanceof DataLinkBlockEntity newLink)) {
            WeaponNetworkRuntime.get(level).unregister(move.newDataLink());
            LOGGER.warn("Relocated DataLink at {} was unavailable; topology remains detached",
                    move.newDataLink());
            return null;
        }
        if (level.getBlockState(move.newSource()).getBlock()
                != move.oldSourceBlock()
                || level.getBlockState(move.newTarget()).getBlock()
                != move.oldTargetBlock()) {
            WeaponNetworkRuntime.get(level).unregister(move.newDataLink());
            LOGGER.warn("Relocated DataLink {} did not retain its source/target blocks; "
                            + "refusing topology migration",
                    move.newDataLink());
            return null;
        }
        return newLink;
    }

    private static void rollbackSavedWeaponRelocations(
            NetworkData networkData,
            ServerLevel level,
            List<PreparedWeaponMove> prepared,
            Map<PendingMove, NetworkData.WeaponRelocationResult> results
    ) {
        for (int index = prepared.size() - 1; index >= 0; index--) {
            PendingMove move = prepared.get(index).move();
            rollbackSavedWeaponRelocation(networkData, level, move,
                    results.get(move));
        }
    }

    private static void detachRelocatedLinks(
            ServerLevel level,
            List<PreparedWeaponMove> prepared
    ) {
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
        for (PreparedWeaponMove entry : prepared) {
            runtime.unregister(entry.link().getBlockPos());
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

            boolean runtimeRelocated;
            try {
                runtimeRelocated = WeaponNetworkRuntime.get(level)
                        .relocateMountGroup(move.oldTarget(), move.newTarget(),
                                newLink, move.newSource());
            } catch (RuntimeException exception) {
                rollbackSavedWeaponRelocation(networkData, level, move,
                        weaponResult);
                throw exception;
            }
            if (!runtimeRelocated) {
                rollbackSavedWeaponRelocation(networkData, level, move,
                        weaponResult);
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

        finishRelocatedIndexes(level, move, newLink, weaponResult);
    }

    private static void finishRelocatedIndexes(
            ServerLevel level,
            PendingMove move,
            DataLinkBlockEntity newLink,
            NetworkData.WeaponRelocationResult weaponResult
    ) {
        NetworkData networkData = NetworkData.get(level);
        boolean networkLinkMoved = networkData.updateDataLinkPosition(
                level.dimension(), move.oldDataLink(), move.newDataLink());
        ARADData.get(level).updateDataLinkPosition(
                level.dimension(), move.oldDataLink(), move.newDataLink());

        BlockPos filtererPos = networkData.getFiltererForDataLink(
                level.dimension(), move.newDataLink());
        if (filtererPos == null
                && weaponResult == NetworkData.WeaponRelocationResult.UPDATED) {
            filtererPos = networkData.getFiltererForEndpoint(
                    level.dimension(), move.newSource());
        }
        if (filtererPos != null
                && level.getBlockEntity(filtererPos)
                instanceof NetworkFiltererBlockEntity filterer) {
            filterer.onWeaponTopologyChanged();
        }

        if (networkLinkMoved) {
            newLink.markAssemblyRelocated();
        }
    }

    private static void rollbackSavedWeaponRelocation(
            NetworkData networkData,
            ServerLevel level,
            PendingMove move,
            NetworkData.WeaponRelocationResult appliedResult
    ) {
        if (appliedResult != NetworkData.WeaponRelocationResult.UPDATED) {
            return;
        }
        NetworkData.WeaponRelocationResult rollbackResult =
                networkData.relocateWeaponEndpoint(
                        level.dimension(),
                        move.newSource(),
                        move.oldSource(),
                        move.newTarget(),
                        move.oldTarget()
                );
        if (rollbackResult != NetworkData.WeaponRelocationResult.UPDATED) {
            throw new IllegalStateException(
                    "Could not roll back saved weapon relocation from "
                            + move.newTarget() + " to " + move.oldTarget()
                            + ": " + rollbackResult);
        }
    }

    private static void invalidateControllers(ServerLevel level, BlockPos mountPos) {
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
        Set<BlockPos> endpoints = new HashSet<>();
        WeaponNetworkRuntime.WeaponGroupView group =
                runtime.getWeaponGroupView(mountPos);
        if (group != null) {
            endpoints.addAll(group.endpoints());
        }

        WeaponNetworkRuntime.WeaponControlView controlView =
                runtime.getWeaponControlViewForMount(mountPos);
        if (controlView != null) {
            endpoints.add(controlView.pitchPos());
            for (WeaponNetworkRuntime.MountChannelView channel
                    : controlView.channels()) {
                if (channel.yawPos() != null) {
                    endpoints.add(channel.yawPos());
                }
                if (channel.firingPos() != null) {
                    endpoints.add(channel.firingPos());
                }
            }
        }

        for (BlockPos endpoint : endpoints) {
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

    private record WeaponMoveKey(
            ServerLevel originLevel,
            BlockPos oldMount,
            BlockPos newMount
    ) {
    }

    private record PreparedWeaponMove(
            PendingMove move,
            DataLinkBlockEntity link
    ) {
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
