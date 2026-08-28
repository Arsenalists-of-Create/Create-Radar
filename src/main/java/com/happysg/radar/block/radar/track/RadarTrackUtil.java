package com.happysg.radar.block.radar.track;

import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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




    public static CompoundTag serializeNBTList(Collection<RadarTrack> tracks) {
        ListTag list = new ListTag();
        for (RadarTrack track : tracks) {
            list.add(track.serializeNBT());
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
