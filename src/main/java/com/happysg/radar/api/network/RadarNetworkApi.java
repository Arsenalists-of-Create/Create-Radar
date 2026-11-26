package com.happysg.radar.api.network;

import com.happysg.radar.api.tracking.BasicRadarContact;
import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarContactCategory;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.RadarNetworkTrackView;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RadarNetworkApi {
    private RadarNetworkApi() {}

    public static Optional<BlockPos> getNetworkController(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) return Optional.empty();
        if (level.hasChunkAt(networkPos) && level.getBlockEntity(networkPos) instanceof NetworkFiltererBlockEntity) {
            return Optional.of(networkPos.immutable());
        }
        return Optional.ofNullable(NetworkData.get(level).getFiltererForEndpoint(level.dimension(), networkPos));
    }

    public static Optional<RadarContact> getSelectedContact(ServerLevel level, BlockPos networkPos) {
        NetworkFiltererBlockEntity filterer = resolveFilterer(level, networkPos).orElse(null);
        return Optional.ofNullable(filterer == null ? null : contact(filterer.activeTrackCache));
    }

    public static Optional<Vec3> getSelectedTargetPosition(ServerLevel level, BlockPos networkPos) {
        return getSelectedContact(level, networkPos).map(RadarContact::getPosition);
    }

    public static Collection<? extends RadarContact> getContacts(ServerLevel level, BlockPos networkPos) {
        NetworkData.Group group = group(level, networkPos);
        if (group == null) return List.of();
        LinkedHashMap<String, RadarTrack> tracksById = new LinkedHashMap<>();
        Set<Long> connectedShipIds = null;
        for (BlockPos radarPos : group.radarEndpoints.keySet()) {
            if (!level.hasChunkAt(radarPos)) continue;
            BlockEntity be = level.getBlockEntity(radarPos);
            if (!(be instanceof IRadar radar) || !radar.isRunning()) continue;
            Collection<RadarTrack> tracks = radar.getTracks();
            if (tracks == null) continue;
            for (RadarTrack track : tracks) {
                if (track == null) continue;
                if (track.trackCategory() == TrackCategory.VS2) {
                    if (connectedShipIds == null) connectedShipIds = RadarNetworkTrackView.connectedShipIds(level, group);
                    if (RadarNetworkTrackView.isOwnVs2Contact(track, connectedShipIds)) continue;
                }
                RadarNetworkTrackView.mergeNewest(tracksById, track);
            }
        }
        return tracksById.values().stream().map(RadarNetworkApi::contact).toList();
    }

    private static RadarContact contact(RadarTrack track) {
        if (track == null) return null;
        RadarContactCategory category;
        try { category = RadarContactCategory.valueOf(track.trackCategory().name()); }
        catch (IllegalArgumentException error) { category = RadarContactCategory.MISC; }
        return new BasicRadarContact(track.getId(), track.getPosition(), track.getVelocity(), category);
    }

    private static Optional<NetworkFiltererBlockEntity> resolveFilterer(ServerLevel level, BlockPos networkPos) {
        BlockPos pos = getNetworkController(level, networkPos).orElse(null);
        if (pos == null || !level.hasChunkAt(pos)) return Optional.empty();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof NetworkFiltererBlockEntity filterer ? Optional.of(filterer) : Optional.empty();
    }

    private static NetworkData.Group group(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) return null;
        BlockPos pos = getNetworkController(level, networkPos).orElse(null);
        return pos == null ? null : NetworkData.get(level).getGroup(level.dimension(), pos);
    }

    public static Collection<BlockPos> getRadarPositions(ServerLevel level, BlockPos networkPos) {
        NetworkData.Group group = group(level, networkPos);
        return group == null ? List.of() : List.copyOf(group.radarEndpoints.keySet());
    }

    public static boolean isLinked(ServerLevel level, BlockPos endpointPos) {
        return getNetworkController(level, endpointPos).isPresent();
    }

    public static boolean isRadarLinked(ServerLevel level, BlockPos radarPos) {
        NetworkData.Group group = group(level, radarPos);
        return group != null && group.radarEndpoints.containsKey(radarPos);
    }

    public static Optional<RadarNetworkView> getNetworkView(ServerLevel level, BlockPos networkPos) {
        if (level == null || networkPos == null) return Optional.empty();
        BlockPos filtererPos = getNetworkController(level, networkPos).orElse(null);
        if (filtererPos == null) return Optional.empty();
        NetworkData.Group group = NetworkData.get(level).getGroup(level.dimension(), filtererPos);
        if (group == null) return Optional.empty();
        List<BlockPos> radars = group.radarEndpoints.keySet().stream().map(BlockPos::immutable).toList();
        List<BlockPos> monitors = group.monitorEndpoints.stream().map(BlockPos::immutable).toList();
        List<BlockPos> weapons = group.weaponEndpoints.stream().map(BlockPos::immutable).toList();
        BlockPos controller = filtererPos.immutable();
        return Optional.of(new RadarNetworkView() {
            @Override public BlockPos controllerPos() { return controller; }
            @Override public Collection<BlockPos> radarPositions() { return radars; }
            @Override public Collection<BlockPos> monitorPositions() { return monitors; }
            @Override public Collection<BlockPos> weaponEndpointPositions() { return weapons; }
        });
    }

    public static boolean moveRadarEndpoint(ServerLevel level, BlockPos oldPos, BlockPos newPos) {
        if (level == null || oldPos == null || newPos == null || oldPos.equals(newPos)) return false;
        return NetworkData.get(level).updateRadarPosition(level.dimension(), oldPos, newPos);
    }

    public static boolean moveMonitorEndpoint(ServerLevel level, BlockPos oldPos, BlockPos newPos) {
        if (level == null || oldPos == null || newPos == null || oldPos.equals(newPos)) return false;
        return NetworkData.get(level).updateMonitorPosition(level.dimension(), oldPos, newPos);
    }

    public static Optional<BlockPos> getWeaponMountForController(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) return Optional.empty();
        return Optional.ofNullable(WeaponNetworkRuntime.getMountForController(level, controllerPos));
    }

    public static Optional<BlockPos> getNetworkControllerForWeaponMount(ServerLevel level, BlockPos mountPos) {
        if (level == null || mountPos == null) return Optional.empty();
        return Optional.ofNullable(NetworkData.get(level).getFiltererForWeaponMount(level.dimension(), mountPos));
    }

    public static Optional<BlockPos> getNetworkControllerForDataLink(ServerLevel level, BlockPos dataLinkPos) {
        if (level == null || dataLinkPos == null) return Optional.empty();
        return Optional.ofNullable(NetworkData.get(level).getFiltererForDataLink(level.dimension(), dataLinkPos));
    }
}
