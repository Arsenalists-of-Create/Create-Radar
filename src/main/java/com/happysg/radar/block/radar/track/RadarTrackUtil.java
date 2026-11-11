package com.happysg.radar.block.radar.track;

import com.happysg.radar.api.physics.RadarPhysics;
import com.happysg.radar.compat.sable.SableAssemblyRegistry;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import com.happysg.radar.compat.Mods;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.*;

public class RadarTrackUtil {

    public static RadarTrack getRadarTrack(SubLevelAccess subLevel, Level level) {
        return new RadarTrack(String.valueOf(subLevel.getUniqueId()), getPosition(subLevel), getVelocity(subLevel, level), level.getGameTime(),
                TrackCategory.SABLE, "Sable:ship", getShipSize(subLevel));
    }

    public static float getShipSize(SubLevelAccess subLevel){
        if(subLevel.boundingBox() != null){
            return (float) subLevel.boundingBox().maxY();
        }else{
            return 1;
        }
    }


    public static Vec3 getVelocity(SubLevelAccess subLevel, Level level) {
        return VS2ShipVelocityTracker.getShipVelocityPerTick(subLevel, level);
    }

    public static Vec3 getPosition(SubLevelAccess serverShip) {
        Vector3d vecD = serverShip.boundingBox().center(new Vector3d());
        return new Vec3(vecD.x, vecD.y, vecD.z);
    }

    /**
     * Translation applied to a live ship silhouette so it follows the
     * position reported by radar, including directional-jamming jitter.
     */
    public static Vec3 getReportedPositionOffset(
            RadarTrack track, SubLevelAccess subLevel) {
        if (track == null || subLevel == null || subLevel.boundingBox() == null) {
            return Vec3.ZERO;
        }
        return getReportedPositionOffset(track.position(),
                getPosition(subLevel));
    }

    public static Vec3 getReportedPositionOffset(Vec3 reportedPosition,
                                                 Vec3 physicalPosition) {
        if (!finite(reportedPosition) || !finite(physicalPosition)) {
            return Vec3.ZERO;
        }
        return reportedPosition.subtract(physicalPosition);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static Collection<RadarTrack> withConnectedFriendlies(Level level, BlockPos radarPos, Collection<RadarTrack> contacts) {
        if (level == null || !Mods.SABLE.isLoaded()) {
            return contacts;
        }

        Set<UUID> connected = RadarPhysics.connectedFrameIds(level, radarPos);
        if (connected.size() <= 1) {
            return contacts;
        }

        SubLevelAccess own = SableUtils.getShipManagingPos(level, radarPos);
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (own == null || container == null) {
            return contacts;
        }

        Map<String, RadarTrack> result = new LinkedHashMap<>();
        for (RadarTrack track : contacts) {
            if (track != null) {
                result.put(track.getId(), track);
            }
        }

        for (UUID id : connected) {
            if (id.equals(own.getUniqueId())) {
                continue;
            }

            SubLevelAccess sublevel = container.getSubLevel(id);
            if (sublevel == null) {
                continue;
            }

            RadarTrack track = getRadarTrack(sublevel, level);
            track.setFriendly(true);
            result.put(track.getId(), track);
        }

        return List.copyOf(result.values());
    }

    public static Collection<RadarTrack> groupConnectedSableTracks(
            Level level,
            Collection<RadarTrack> tracks) {

        if (!(level instanceof ServerLevel serverLevel) || tracks == null || !Mods.SABLE.isLoaded()) {
            return tracks;
        }

        SableAssemblyRegistry registry = SableAssemblyRegistry.get(serverLevel);
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        for (RadarTrack track : tracks) {
            if (track == null || track.trackCategory() != TrackCategory.SABLE || track.isSynthetic()) {
                continue;
            }

            try {
                registry.observe(serverLevel, UUID.fromString(track.id()));
            } catch (IllegalArgumentException ignored) {}
        }

        Map<String, RadarTrack> result = new LinkedHashMap<>();
        for (RadarTrack track : tracks) {
            if (track == null) {
                continue;
            }

            if (track.trackCategory() != TrackCategory.SABLE || track.isSynthetic()) {
                result.put(track.id(), track);
                continue;
            }

            UUID memberId;

            try {
                memberId = UUID.fromString(track.id());
            } catch (IllegalArgumentException ignored) {
                result.put(track.id(), track);
                continue;
            }

            UUID ownerId = registry.groupOf(memberId);
            String key = ownerId.toString();

            SubLevelAccess owner = container == null ? null : container.getSubLevel(ownerId);
            RadarTrack grouped = owner == null
                    ? new RadarTrack(
                    key,
                    track.position(),
                    track.velocity(),
                    track.scannedTime(),
                    TrackCategory.SABLE,
                    "Sable:ship",
                    track.getEnityHeight()
            ) : RadarTrackUtil.getRadarTrack(owner, level);

            grouped.setFriendly(track.isFriendly());
            grouped.setDisplayName(track.getDisplayName());
            grouped.setJammingData(track.getJammingData());
            grouped.setScannedTime(track.scannedTime());

            RadarTrack existing = result.get(key);
            if (existing == null) {
                result.put(key, grouped);
            } else {
                existing.setFriendly(existing.isFriendly() || track.isFriendly());
                if (existing.getDisplayName().isBlank() && !grouped.getDisplayName().isBlank()) {
                    existing.setDisplayName(grouped.getDisplayName());
                }

                if (track.scannedTime() > existing.scannedTime()) {
                    existing.setScannedTime(track.scannedTime());
                    existing.setJammingData(track.getJammingData());
                }
            }
        }

        return List.copyOf(result.values());
    }

    public static CompoundTag serializeNBTList(Collection<RadarTrack> tracks) {
        ListTag list = new ListTag();
        for (RadarTrack track : tracks) {
            list.add(track.serializeNetworkNBT());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("tracks", list);
        return tag;
    }

    public static List<RadarTrack> deserializeListNBT(CompoundTag tag) {
        List<RadarTrack> tracks = new ArrayList<>();
        ListTag list = tag.getList("tracks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            tracks.add(RadarTrack.deserializeNBT(list.getCompound(i)));
        }
        return tracks;
    }
}
