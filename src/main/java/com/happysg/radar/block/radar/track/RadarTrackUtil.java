package com.happysg.radar.block.radar.track;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RadarTrackUtil {

    public static RadarTrack getRadarTrack(SubLevelAccess subLevel, Level level) {
        return new RadarTrack(String.valueOf(subLevel.getUniqueId()), getPosition(subLevel), getVelocity(subLevel, level), level.getGameTime(),
                TrackCategory.SABLE, "Sable:subLevel", getShipSize(subLevel));
    }

    public static float getShipSize(SubLevelAccess subLevel){
        if(subLevel.boundingBox() != null){
            return (float) subLevel.boundingBox().maxY();
        }else{
            return 1;
        }
    }
    public static Vec3 getVelocity(SubLevelAccess subLevel, Level level) {
        //Probally not the best to cast like this.
        Vector3f vec3d = new Vector3f((Vector3fc) SableCompanion.INSTANCE.getVelocity(level, subLevel.boundingBox().center()));
        return new Vec3(vec3d);
    }

    public static Vec3 getPosition(SubLevelAccess serverShip) {
        Vector3d vecD = serverShip.boundingBox().center(new Vector3d());
        return new Vec3(vecD.x, vecD.y, vecD.z);
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
