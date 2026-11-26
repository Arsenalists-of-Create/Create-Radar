package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RadarNetworkTrackView {
    private RadarNetworkTrackView() {}

    public static void mergeNewest(Map<String, RadarTrack> tracks, RadarTrack track) {
        if (tracks == null || track == null || track.getId() == null || track.position() == null) return;
        tracks.merge(track.getId(), track, (existing, incoming) -> incoming.scannedTime() > existing.scannedTime() ? incoming : existing);
    }

    public static boolean isOwnVs2Contact(RadarTrack track, Set<Long> connectedShipIds) {
        if (track == null || track.trackCategory() != TrackCategory.VS2 || connectedShipIds == null || connectedShipIds.isEmpty()) return false;
        try {
            return connectedShipIds.contains(Long.parseLong(track.getId()));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static Set<Long> connectedShipIds(ServerLevel level, NetworkData.Group group) {
        if (level == null || group == null || !Mods.VALKYRIENSKIES.isLoaded()) return Set.of();
        Set<Long> result = new HashSet<>();
        Set<BlockPos> sources = new HashSet<>(group.radarEndpoints.keySet());
        for (BlockPos endpoint : group.weaponEndpoints) {
            if (!level.hasChunkAt(endpoint)) continue;
            BlockPos mount = WeaponNetworkRuntime.getMountForController(level, endpoint);
            if (mount != null) sources.add(mount);
        }
        for (BlockPos source : sources) {
            if (!level.hasChunkAt(source)) continue;
            for (UUID ship : PhysicsHandler.getConnectedSublevelIds(level, source)) {
                result.add(ship.getLeastSignificantBits());
            }
        }
        return result;
    }
}
