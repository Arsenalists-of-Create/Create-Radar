package com.happysg.radar.api.network;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class RadarNetworkApi {
    private RadarNetworkApi() {}

    /**
     * Resolves the filterer/controller for either the filterer itself or one of its linked endpoints.
     */
    public static Optional<BlockPos> getNetworkController(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) {
            return Optional.empty();
        }

        if (level.getBlockEntity(networkPos) instanceof NetworkFiltererBlockEntity) {
            return Optional.of(networkPos.immutable());
        }

        BlockPos filterer = NetworkData.get(level).getFiltererForEndpoint(level.dimension(), networkPos);
        return Optional.ofNullable(filterer);
    }

    public static Optional<RadarContact> getSelectedContact(ServerLevel level, BlockPos networkPos) {
        NetworkFiltererBlockEntity filterer = resolveFilterer(level, networkPos).orElse(null);

        if (filterer == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(filterer.getActiveRadarContact());
    }

    public static Optional<Vec3> getSelectedTargetPosition(ServerLevel level, BlockPos networkPos) {
        return getSelectedContact(level, networkPos).map(RadarContact::getPosition);
    }

    public static Collection<? extends RadarContact> getContacts(ServerLevel level, BlockPos networkPos) {
        NetworkFiltererBlockEntity filterer = resolveFilterer(level, networkPos).orElse(null);

        if (filterer == null) {
            return List.of();
        }

        return filterer.getRadarContacts();
    }

    private static Optional<NetworkFiltererBlockEntity> resolveFilterer(ServerLevel level, BlockPos networkPos) {
        BlockPos filtererPos = getNetworkController(level, networkPos).orElse(null);

        if (filtererPos == null) {
            return Optional.empty();
        }

        BlockEntity be = level.getBlockEntity(filtererPos);
        return be instanceof NetworkFiltererBlockEntity filterer ? Optional.of(filterer) : Optional.empty();
    }

    public static Collection<BlockPos> getRadarPositions(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) {
            return List.of();
        }

        BlockPos filtererPos = getNetworkController(level, networkPos)
                .orElse(null);

        if (filtererPos == null) {
            return List.of();
        }

        NetworkData.Group group = NetworkData.get(level).getGroup(level.dimension(), filtererPos);

        if (group == null) {
            return List.of();
        }

        return group.getRadarEndpoints()
                .stream()
                .map(NetworkData.RadarEndpoint::pos)
                .map(BlockPos::immutable)
                .toList();
    }

    public static boolean isLinked(ServerLevel level, BlockPos endpointPos) {
        if (level == null || endpointPos == null) {
            return false;
        }

        return getNetworkController(level, endpointPos).isPresent();
    }

    public static boolean isRadarLinked(ServerLevel level, BlockPos radarPos) {
        if (level == null || radarPos == null) {
            return false;
        }

        BlockPos filtererPos = NetworkData.get(level).getFiltererForEndpoint(level.dimension(), radarPos);

        if (filtererPos == null) {
            return false;
        }

        NetworkData.Group group = NetworkData.get(level).getGroup(level.dimension(), filtererPos);

        if (group == null) {
            return false;
        }

        for (NetworkData.RadarEndpoint endpoint : group.getRadarEndpoints()) {
            if (radarPos.equals(endpoint.pos())) {
                return true;
            }
        }

        return false;
    }

    public static Optional<RadarNetworkView> getNetworkView(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) {
            return Optional.empty();
        }

        BlockPos filtererPos = getNetworkController(level, networkPos).orElse(null);

        if (filtererPos == null) {
            return Optional.empty();
        }

        NetworkData.Group group = NetworkData.get(level).getGroup(level.dimension(), filtererPos);

        if (group == null) {
            return Optional.empty();
        }

        List<BlockPos> radars = group.getRadarEndpoints()
                .stream()
                .map(NetworkData.RadarEndpoint::pos)
                .map(BlockPos::immutable)
                .toList();

        List<BlockPos> monitors = group.monitorEndpoints
                .stream()
                .map(BlockPos::immutable)
                .toList();

        List<BlockPos> weapons = group.weaponEndpoints
                .stream()
                .map(BlockPos::immutable)
                .toList();

        BlockPos controller = filtererPos.immutable();

        return Optional.of(
                new RadarNetworkView() {
                    @Override
                    public BlockPos controllerPos() { return controller; }

                    @Override
                    public Collection<BlockPos> radarPositions() { return radars; }

                    @Override
                    public Collection<BlockPos> monitorPositions() { return monitors; }

                    @Override
                    public Collection<BlockPos> weaponEndpointPositions() { return weapons; }
                }
        );
    }

    /**
     * Updates a linked radar endpoint after it moves to a new canonical block position.
     *
     * @return true if an existing linked radar endpoint was moved
     */
    public static boolean moveRadarEndpoint(ServerLevel level, BlockPos oldPos, BlockPos newPos) {
        if (level == null || oldPos == null || newPos == null) {
            return false;
        }

        if (oldPos.equals(newPos)) {
            return false;
        }

        return NetworkData.get(level).updateRadarPosition(level, oldPos, newPos);
    }

    /**
     * Updates a linked monitor endpoint after it moves to a new canonical block position.
     *
     * @return true if an existing linked monitor endpoint was moved
     */
    public static boolean moveMonitorEndpoint(ServerLevel level, BlockPos oldPos, BlockPos newPos) {
        if (level == null || oldPos == null || newPos == null) {
            return false;
        }

        if (oldPos.equals(newPos)) {
            return false;
        }

        return NetworkData.get(level).updateMonitorPosition(level.dimension(), oldPos, newPos);
    }
}