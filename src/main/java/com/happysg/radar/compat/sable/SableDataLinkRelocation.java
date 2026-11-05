package com.happysg.radar.compat.sable;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

import java.util.*;

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
    private static final Map<ServerLevel, List<PendingMountMove>> PENDING_MOUNT_MOVES = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> MOVING_FROM = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> MOVING_ENDPOINTS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, BlockPos>> CONFIRMED_ENDPOINT_MOVES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, BlockPos>> PREPARED_FILTERER_MOVES = new WeakHashMap<>();

    private record PreparedLink(BlockPos source, BlockPos target, Block sourceBlock, Block targetBlock, DataLinkBlock.LinkStyle style, DataLinkBlockEntity.WeaponEndpointType type) {}
    private static final Map<ServerLevel, Map<BlockPos, PreparedLink>> PREPARED_LINKS = new WeakHashMap<>();

    private SableDataLinkRelocation() {}

    public static void prepareEndpointMove(ServerLevel level, BlockPos oldPos) {
        MOVING_ENDPOINTS.computeIfAbsent(level, ignored -> new HashSet<>()).add(oldPos.immutable());
    }

    public static void prepare(ServerLevel level, BlockPos oldPos) {
        if (!(level.getBlockEntity(oldPos) instanceof DataLinkBlockEntity link)) {
            return;
        }

        if (!link.getBlockState().hasProperty(DataLinkBlock.LINK_STYLE)) {
            return;
        }

        BlockPos source = link.getSourcePosition().immutable();
        BlockPos target = link.getTargetPosition().immutable();

        PREPARED_LINKS
                .computeIfAbsent(level, ignored -> new HashMap<>())
                .put(oldPos.immutable(), new PreparedLink(
                        source,
                        target,
                        level.getBlockState(source).getBlock(),
                        level.getBlockState(target).getBlock(),
                        link.getBlockState().getValue(DataLinkBlock.LINK_STYLE),
                        link.getWeaponEndpointType()
                ));

        MOVING_FROM.computeIfAbsent(level, ignored -> new HashSet<>()).add(oldPos.immutable());
    }

    public static void prepareFiltererMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        if (originLevel != resultingLevel || oldPos.equals(newPos)) {
            return;
        }

        prepareEndpointMove(originLevel, oldPos);

        PREPARED_FILTERER_MOVES.computeIfAbsent(resultingLevel, ignored -> new HashMap<>()).put(newPos.immutable(), oldPos.immutable());
    }

    public static BlockPos getPreparedFiltererSource(ServerLevel level, BlockPos newPos) {
        Map<BlockPos, BlockPos> moves = PREPARED_FILTERER_MOVES.get(level);
        return moves == null ? null : moves.get(newPos);
    }

    public static void finishPreparedFiltererMove(ServerLevel level, BlockPos newPos) {
        Map<BlockPos, BlockPos> moves = PREPARED_FILTERER_MOVES.get(level);

        if (moves == null) {
            return;
        }

        moves.remove(newPos);

        if (moves.isEmpty()) {
            PREPARED_FILTERER_MOVES.remove(level);
        }
    }

    public static void recordEndpointMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        if (originLevel != resultingLevel || oldPos.equals(newPos)) {
            return;
        }

        CONFIRMED_ENDPOINT_MOVES.computeIfAbsent(resultingLevel, ignored -> new HashMap<>()).put(oldPos.immutable(), newPos.immutable());
    }

    private static BlockPos confirmedEndpointDestination(ServerLevel level, BlockPos oldPos) {
        Map<BlockPos, BlockPos> moves = CONFIRMED_ENDPOINT_MOVES.get(level);
        return moves == null ? null : moves.get(oldPos);
    }

    private static PreparedLink takePreparedLink(ServerLevel level, BlockPos oldPos) {
        Map<BlockPos, PreparedLink> prepared = PREPARED_LINKS.get(level);
        if (prepared == null) {
            return null;
        }

        PreparedLink result = prepared.remove(oldPos);

        if (prepared.isEmpty()) {
            PREPARED_LINKS.remove(level);
        }

        return result;
    }

    public static void capture(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        PreparedLink prepared = takePreparedLink(originLevel, oldPos);
        if (!(resultingLevel.getBlockEntity(newPos) instanceof DataLinkBlockEntity newLink)) {
            return;
        }

        if (prepared == null || originLevel != resultingLevel) {
            newLink.clearSavedConnection();
            LOGGER.warn("Sable Data Link {} -> {} has no valid source snapshot; disconnected", oldPos, newPos);
            return;
        }

        BlockPos delta = newPos.subtract(oldPos);

        PendingMove move = new PendingMove(
                originLevel,
                oldPos.immutable(),
                newPos.immutable(),
                prepared.source(),
                newLink.getSourcePosition().immutable(),
                prepared.target(),
                prepared.target().offset(delta),
                prepared.sourceBlock(),
                prepared.targetBlock(),
                prepared.style(),
                prepared.type()
        );

        PENDING.computeIfAbsent(resultingLevel, ignored -> new ArrayList<>()).add(move);
        newLink.markAssemblyRelocated();
    }

    public static void captureMountMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldMount, BlockPos newMount) {
        if (oldMount.equals(newMount)) {
            return;
        }

        if (originLevel != resultingLevel) {
            LOGGER.warn("Cannot relocate weapon mount link across ServerLevel instances: {} -> {}", oldMount, newMount);
            return;
        }

        if (!(resultingLevel.getBlockEntity(newMount) instanceof CannonMountBlockEntity)) {
            return;
        }

        boolean radarOwned = NetworkData.get(originLevel).getFiltererForWeaponMount(originLevel.dimension(), oldMount) != null;
        boolean weaponLinked = WeaponNetworkRuntime.get(originLevel).hasExplicitLinksForMount(oldMount);

        if (!radarOwned && !weaponLinked) {
            return;
        }

        PENDING_MOUNT_MOVES.computeIfAbsent(resultingLevel, ignored -> new ArrayList<>()).add(new PendingMountMove(originLevel, oldMount.immutable(), newMount.immutable()));
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

        if (moves == null) {
            moves = new ArrayList<>();
        }

        List<PendingMove> resolvedMoves = new ArrayList<>(moves.size());

        for (PendingMove move : moves) {
            resolvedMoves.add(resolveMovedEndpoints(level, move));
        }

        List<PendingMove> validMoves = new ArrayList<>();

        for (PendingMove move : resolvedMoves) {
            if (movedTogether(level, move)) {
                validMoves.add(move);
            } else {
                disconnectRejectedMove(level, move);
                clearMovingMarker(move);
            }
        }

        moves = validMoves;
        disconnectStationaryLinksForMovedEndpoints(level, moves);

        PENDING_MOUNT_MOVES.remove(level);

        // Controller links establish the canonical mount relocation before a
        // radar-style link migrates its saved endpoint and DataLink position.
        moves.sort(Comparator.comparingInt(move -> isWeaponControllerMove(move) ? 0 : 1));

        Map<WeaponMoveKey, List<PendingMove>> weaponMoves = new LinkedHashMap<>();
        List<PendingMove> otherMoves = new ArrayList<>();
        for (PendingMove move : moves) {
            if (isWeaponControllerMove(move)) {
                WeaponMoveKey key = new WeaponMoveKey(move.originLevel(), move.oldTarget(), move.newTarget());
                weaponMoves.computeIfAbsent(key, ignored -> new ArrayList<>()).add(move);
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

        relocateConfirmedRadars(level, moves);

        MOVING_FROM.remove(level);
        MOVING_ENDPOINTS.remove(level);
        CONFIRMED_ENDPOINT_MOVES.remove(level);
        PREPARED_LINKS.remove(level);
    }

    private static void processMountOnly(ServerLevel level, PendingMountMove move) {

        if (move.originLevel() != level || !level.hasChunkAt(move.newMount()) || !(level.getBlockEntity(move.newMount()) instanceof CannonMountBlockEntity)) {
            return;
        }

        NetworkData data = NetworkData.get(level);
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);

        BlockPos oldMount = move.oldMount();
        BlockPos newMount = move.newMount();

        NetworkData.WeaponMountReferenceRelocation relocation = data.relocateWeaponMountReferences(level.dimension(), oldMount, newMount);
        if (relocation.result() == NetworkData.WeaponRelocationResult.NOT_FOUND && !runtime.hasExplicitLinksForMount(oldMount)) {
            return;
        }

        if (relocation.result() == NetworkData.WeaponRelocationResult.CONFLICT) {
            LOGGER.warn("Mount-only relocation conflicts with existing ownership: {} -> {}", oldMount, newMount);
            return;
        }

        boolean moved;

        try {
            moved = runtime.relocateMountGroup(oldMount, newMount, Map.of());
        } catch (RuntimeException exception) {
            data.rollbackWeaponMountReferences(relocation);
            throw exception;
        }

        if (!moved) {
            data.rollbackWeaponMountReferences(relocation);

            LOGGER.warn("Mount-only relocation rejected; previous links restored: {} -> {}", oldMount, newMount);
            return;
        }

        invalidateControllers(level, newMount);

        LOGGER.debug("Relocated stationary weapon links from {} to {}", oldMount, newMount);
    }

    private static void disconnectStationaryLinksForMovedEndpoints(ServerLevel level, List<PendingMove> dataLinkMoves) {
        Map<BlockPos, BlockPos> moved = CONFIRMED_ENDPOINT_MOVES.get(level);
        if (moved == null || moved.isEmpty()) {
            return;
        }

        var dim = level.dimension();
        NetworkData network = NetworkData.get(level);
        ARADData arad = ARADData.get(level);
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);

        Set<BlockPos> movingLinks = new HashSet<>();
        for (PendingMove move : dataLinkMoves) {
            movingLinks.add(move.oldDataLink());
            movingLinks.add(move.newDataLink());
        }

        Set<BlockPos> linksToDisconnect = new HashSet<>();

        for (NetworkData.Group group : List.copyOf(network.getGroups().values())) {
            if (!dim.equals(group.key.dim())) {
                continue;
            }

            boolean filtererMoved = moved.containsKey(group.key.filtererPos()) || moved.containsValue(group.key.filtererPos());
            for (BlockPos linkPos : List.copyOf(group.dataLinks)) {
                if (movingLinks.contains(linkPos)) {
                    continue;
                }

                BlockPos savedEndpoint = network.peekEndpointForDataLink(dim, linkPos);
                boolean split = filtererMoved || savedEndpoint != null && (moved.containsKey(savedEndpoint) || moved.containsValue(savedEndpoint));
                if (level.isLoaded(linkPos) && level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link) {
                    split |= moved.containsKey(link.getSourcePosition()) || moved.containsKey(link.getTargetPosition());
                }

                if (split) {
                    linksToDisconnect.add(linkPos);
                }
            }
        }

        for (BlockPos oldEndpoint : moved.keySet()) {
            for (Direction direction : Direction.values()) {
                BlockPos linkPos = oldEndpoint.relative(direction);

                if (movingLinks.contains(linkPos) || !level.isLoaded(linkPos)) {
                    continue;
                }

                if (level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link && oldEndpoint.equals(link.getSourcePosition())) {
                    linksToDisconnect.add(linkPos);
                }
            }
        }


        for (Map.Entry<BlockPos, BlockPos> movement : moved.entrySet()) {
            for (BlockPos endpoint : List.of(movement.getKey(), movement.getValue())) {
                ARADData.Group rwrGroup = arad.getGroup(dim, endpoint);

                if (rwrGroup != null) {
                    linksToDisconnect.addAll(rwrGroup.dataLinks);
                }

                BlockPos rwrPos = arad.getRwrForMonitor(dim, endpoint);
                if (rwrPos == null) {
                    rwrPos = arad.getRwrForJammer(dim, endpoint);
                }

                if (rwrPos == null) {
                    continue;
                }

                ARADData.Group group = arad.getGroup(dim, rwrPos);
                if (group == null) {
                    continue;
                }

                for (BlockPos linkPos : List.copyOf(group.dataLinks)) {
                    if (endpoint.equals(arad.peekEndpointForDataLink(dim, linkPos))) {
                        linksToDisconnect.add(linkPos);
                    }
                }
            }
        }

        // Disconnect only Data Links that did not move.
        for (BlockPos linkPos : linksToDisconnect) {
            if (movingLinks.contains(linkPos) || isRelocating(level, linkPos)) {
                continue;
            }

            if (!level.isLoaded(linkPos)) {
                LOGGER.warn("Cannot yet disconnect unloaded stationary Data Link {}", linkPos);
                continue;
            }

            DataLinkBlockEntity link = level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity found ? found : null;
            BlockPos endpoint = network.peekEndpointForDataLink(dim, linkPos);

            if (endpoint == null && link != null) {
                endpoint = link.getWeaponEndpointType() == DataLinkBlockEntity.WeaponEndpointType.PITCH ? link.getSourcePosition() : link.getTargetPosition();
            }

            network.removeDataLinkAndCleanup(dim, linkPos, level, endpoint);
            arad.removeDataLinkAndCleanup(dim, linkPos, level);
            runtime.unregister(linkPos);

            if (link != null) {
                link.clearSavedConnection();
            }

            LOGGER.debug("Disconnected Data Link {} left behind during Sable movement", linkPos);
        }


        for (BlockPos oldMount : moved.keySet()) {
            for (NetworkData.Group group : List.copyOf(network.getGroups().values())) {
                if (!dim.equals(group.key.dim())) {
                    continue;
                }

                for (BlockPos controller : List.copyOf(group.weaponEndpoints)) {
                    if (moved.containsKey(controller)) {
                        continue;
                    }

                    if (oldMount.equals(network.getWeaponMountForController(dim, controller))) {
                        network.onEndpointRemoved(level, controller);
                    }
                }
            }
        }
    }

    private static void relocateConfirmedRadars(ServerLevel level, List<PendingMove> processedLinks) {
        Map<BlockPos, BlockPos> confirmed =
                CONFIRMED_ENDPOINT_MOVES.get(level);

        if (confirmed == null || confirmed.isEmpty()) {
            return;
        }

        NetworkData network = NetworkData.get(level);
        var dim = level.dimension();

        for (Map.Entry<BlockPos, BlockPos> entry : confirmed.entrySet()) {
            BlockPos oldPos = entry.getKey();
            BlockPos newPos = entry.getValue();

            if (oldPos.equals(newPos) || !(level.getBlockEntity(newPos) instanceof IRadar)) {
                continue;
            }

            List<NetworkData.Group> owners = network.getGroups()
                    .values()
                    .stream()
                    .filter(group -> group.key.dim().equals(dim)
                            && group.radarEndpoints.contains(oldPos))
                    .toList();

            if (owners.isEmpty()) {
                continue;
            }


            boolean allOwnersMoved = owners.stream().allMatch(group -> confirmed.containsKey(group.key.filtererPos()) || confirmed.containsValue(group.key.filtererPos()));
            if (!allOwnersMoved) {
                continue;
            }

            boolean explicitLink = network.getEndpointOrigin(dim, oldPos) == NetworkData.LinkOrigin.DATALINK || owners.stream().anyMatch(group ->
                    group.dataLinks.stream().anyMatch(linkPos -> oldPos.equals(network.peekEndpointForDataLink(dim, linkPos))));
            boolean movedWithLink = processedLinks.stream().anyMatch(move -> network.getFiltererForDataLink(dim, move.newDataLink()) != null && (oldPos.equals(move.oldTarget()) && newPos.equals(move.newTarget()) || oldPos.equals(move.oldSource()) && newPos.equals(move.newSource())));

            if (explicitLink && !movedWithLink) {
                continue;
            }

            if (!network.updateRadarPosition(level, oldPos, newPos)) {
                LOGGER.debug("Could not immediately relocate confirmed Sable radar {} -> {}", oldPos, newPos);
            }
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
    }

    private static boolean isWeaponControllerMove(PendingMove move) {
        return DataLinkBlock.isWeaponLink(move.style(), move.endpointType());
    }

    private static PendingMove resolveMovedEndpoints(ServerLevel level, PendingMove move) {
        if (!(level.getBlockEntity(move.newDataLink()) instanceof DataLinkBlockEntity link)) {
            return move;
        }

        BlockPos newSource = link.getSourcePosition().immutable();
        BlockPos mappedTarget = confirmedEndpointDestination(level, move.oldTarget());
        BlockPos newTarget = mappedTarget != null ? mappedTarget : move.newTarget();

        return new PendingMove(
                move.originLevel(),
                move.oldDataLink(),
                move.newDataLink(),
                move.oldSource(),
                newSource,
                move.oldTarget(),
                newTarget,
                move.oldSourceBlock(),
                move.oldTargetBlock(),
                move.style(),
                move.endpointType()
        );
    }

    private static boolean movedTogether(
            ServerLevel level,
            PendingMove move
    ) {
        if (move.originLevel() != level) {
            return false;
        }

        BlockPos movedSource = confirmedEndpointDestination(
                level, move.oldSource()
        );

        BlockPos movedTarget = confirmedEndpointDestination(
                level, move.oldTarget()
        );

        return movedSource != null
                && movedTarget != null
                && movedSource.equals(move.newSource())
                && movedTarget.equals(move.newTarget())
                && level.getBlockEntity(move.newDataLink())
                instanceof DataLinkBlockEntity
                && matchesMovedBlock(
                level, move.newSource(), move.oldSourceBlock()
        )
                && matchesMovedBlock(
                level, move.newTarget(), move.oldTargetBlock()
        );
    }

    private static void disconnectRejectedMove(ServerLevel level, PendingMove move) {
        NetworkData network = NetworkData.get(level);
        ARADData arad = ARADData.get(level);
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);

        var dimension = level.dimension();

        network.removeDataLinkAndCleanup(dimension, move.oldDataLink(), level, move.oldTarget());
        arad.removeDataLinkAndCleanup(dimension, move.oldDataLink(), level);
        network.removeDataLinkAndCleanup(dimension, move.newDataLink(), level, move.newTarget());
        arad.removeDataLinkAndCleanup(dimension, move.newDataLink(), level);

        runtime.unregister(move.oldDataLink());
        runtime.unregister(move.newDataLink());

        if (level.getBlockEntity(move.newDataLink()) instanceof DataLinkBlockEntity link) {
            link.clearSavedConnection();
        }

        LOGGER.debug("Disconnected incomplete Sable Data Link move {} -> {}", move.oldDataLink(), move.newDataLink());
    }

    private static boolean matchesMovedBlock(ServerLevel level, BlockPos pos, Block expected) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (expected == Blocks.AIR) {
            return level.getBlockEntity(pos) != null;
        }

        return level.getBlockState(pos).getBlock() == expected;
    }

    public static void clear(ServerLevel level) {
        PENDING_MOUNT_MOVES.remove(level);
        PENDING.remove(level);
        PREPARED_FILTERER_MOVES.remove(level);
        MOVING_FROM.remove(level);
        MOVING_ENDPOINTS.remove(level);
        CONFIRMED_ENDPOINT_MOVES.remove(level);
        PREPARED_LINKS.remove(level);
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
            entry.link().finishAssemblyRelocation(entry.move().newTarget());
            finishRelocatedIndexes(level, entry.move(), entry.link(), results.get(entry.move()));
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
        if (!matchesMovedBlock(level, move.newSource(), move.oldSourceBlock()) || !matchesMovedBlock(level, move.newTarget(), move.oldTargetBlock())) {
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
            LOGGER.warn("Relocated DataLink at {} was unavailable; topology remains detached", move.newDataLink());
            return;
        }
        if (!matchesMovedBlock(level, move.newSource(), move.oldSourceBlock()) || !matchesMovedBlock(level, move.newTarget(), move.oldTargetBlock())) {
            LOGGER.warn("Relocated DataLink {} did not retain its source/target blocks; " + "refusing topology migration", move.newDataLink());
            return;
        }

        NetworkData networkData = NetworkData.get(level);
        NetworkData.WeaponRelocationResult weaponResult =
                NetworkData.WeaponRelocationResult.NOT_FOUND;

        if (isWeaponControllerMove(move)) {
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
                runtimeRelocated = WeaponNetworkRuntime.get(level).relocateMountGroup(move.oldTarget(), move.newTarget(), newLink, move.newSource());
            } catch (RuntimeException exception) {
                rollbackSavedWeaponRelocation(networkData, level, move, weaponResult);
                throw exception;
            }
            if (!runtimeRelocated) {
                rollbackSavedWeaponRelocation(networkData, level, move, weaponResult);
                LOGGER.warn("Refused ambiguous weapon group relocation from mount {} to {}", move.oldTarget(), move.newTarget());
                return;
            }
            invalidateControllers(level, move.newTarget());
        } else {
            BlockPos savedEndpoint = networkData.peekEndpointForDataLink(level.dimension(), move.oldDataLink());
            if (savedEndpoint == null) {
                savedEndpoint = networkData.peekEndpointForDataLink(level.dimension(), move.newDataLink());
            }
            if (move.oldTarget().equals(savedEndpoint)) {
                BlockPos savedMount = networkData.getWeaponMountForController(level.dimension(), move.oldTarget());

                if (savedMount != null) {
                    weaponResult = networkData.relocateWeaponEndpoint(
                            level.dimension(),
                            move.oldTarget(),
                            move.newTarget(),
                            savedMount,
                            savedMount.offset(move.newDataLink().subtract(move.oldDataLink()))
                    );
                } else {
                    weaponResult = networkData.relocatePendingWeaponEndpoint(level.dimension(), move.oldTarget(), move.newTarget());
                }
            }

            if (weaponResult == NetworkData.WeaponRelocationResult.CONFLICT) {
                WeaponNetworkRuntime.get(level).unregister(newLink.getBlockPos());

                LOGGER.warn("Refused conflicting pending controller relocation {} -> {}", move.oldTarget(), move.newTarget());
                return;
            }

            newLink.finishAssemblyRelocation(move.newTarget());
        }

        newLink.finishAssemblyRelocation(move.newTarget());
        finishRelocatedIndexes(level, move, newLink, weaponResult);
    }

    private static void finishRelocatedIndexes(
            ServerLevel level,
            PendingMove move,
            DataLinkBlockEntity newLink,
            NetworkData.WeaponRelocationResult weaponResult
    ) {
        NetworkData networkData = NetworkData.get(level);
        boolean networkLinkMoved = networkData.updateDataLinkPosition(level.dimension(), move.oldDataLink(), move.newDataLink());
        ARADData.get(level).updateDataLinkPosition(level.dimension(), move.oldDataLink(), move.newDataLink());

        BlockPos filtererPos = networkData.getFiltererForDataLink(level.dimension(), move.newDataLink());
        if (filtererPos == null && weaponResult == NetworkData.WeaponRelocationResult.UPDATED) {
            filtererPos = networkData.getFiltererForEndpoint(level.dimension(), move.newSource());
        }
        if (filtererPos != null && level.getBlockEntity(filtererPos)
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

    private record PendingMountMove(ServerLevel originLevel, BlockPos oldMount, BlockPos newMount) {}

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
