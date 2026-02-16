package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class FakeRadarTrackFactory {

    public static List<RadarTrack> generate(ServerLevel level, BlockPos radarPos, int count) {
        List<RadarTrack> tracks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Vec3 offset = new Vec3(
                    level.random.nextGaussian() * 100,
                    level.random.nextGaussian() * 30,
                    level.random.nextGaussian() * 100
            );

//            RadarTrack fake = RadarTrack.fake(
//                    radarPos.getCenter().add(offset),
//                    UUID.randomUUID()
//            );

//            tracks.add(fake);
      }

        return tracks;
    }
}
