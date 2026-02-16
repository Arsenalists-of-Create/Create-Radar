package com.happysg.radar.block.radar.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class RadarTrackUtil {

    public static RadarTrack getRadarTrack(Ship ship, Level level) {
        //return new RadarTrack(String.valueOf(ship.getId()), getPosition(ship), getVelocity(ship), level.getGameTime(),
                //TrackCategory.VS2, "VS2:ship", ship.getShipAABB().maxY());
        return null;
    }

    public static Vec3 getVelocity(Ship ship) {
        return new Vec3(ship.getVelocity().x(), ship.getVelocity().y(), ship.getVelocity().z());
    }

    public static Vec3 getPosition(Ship serverShip) {
        //Vector3d vecD = serverShip.getWorldAABB().center(new Vector3d());
        //return new Vec3(vecD.x, vecD.y, vecD.z);
        return null;
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
