package com.happysg.radar.block.radar.bearing;

import com.jozufozu.flywheel.util.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


//todo join entityPositions and VSPositions into a single map
public record VSRadarTracks(String id, Vec3 position, long scannedTime, Color color) {

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        tag.putLong("scannedTime", scannedTime);
        tag.putInt("color", color.getRGB());
        tag.putString("id", id);
        return tag;
    }

    public static VSRadarTracks deserializeNBT(CompoundTag tag) {
        return new VSRadarTracks(
                tag.getString("id"),
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                tag.getLong("scannedTime"),
                new Color(tag.getInt("color"))
        );
    }

    public static CompoundTag serializeNBTList(Collection<VSRadarTracks> tracks) {
        ListTag list = new ListTag();
        for (VSRadarTracks track : tracks) {
            list.add(track.serializeNBT());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("tracks", list);
        return tag;
    }

    public static List<VSRadarTracks> deserializeListNBT(CompoundTag tag) {
        List<VSRadarTracks> tracks = new ArrayList<>();
        ListTag list = tag.getList("tracks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            tracks.add(VSRadarTracks.deserializeNBT(list.getCompound(i)));
        }
        return tracks;
    }

}
