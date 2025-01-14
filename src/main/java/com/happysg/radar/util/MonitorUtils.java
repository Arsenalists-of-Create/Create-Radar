package com.happysg.radar.util;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarTrack;
import com.happysg.radar.block.radar.bearing.VSRadarTracks;
import com.happysg.radar.compat.Mods;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Optional;

public class MonitorUtils {

    public static Vec3 calculateCenter(Level level, MonitorBlockEntity monitor, Direction facing, int size) {
        Vec3 preCenter;
        if (Mods.VALKYRIENSKIES.isLoaded() && VSGameUtilsKt.isBlockInShipyard(level, monitor.getControllerPos())) {
            preCenter = VSGameUtilsKt.toWorldCoordinates(level, monitor.getControllerPos().getCenter());
        } else {
            preCenter = Vec3.atCenterOf(monitor.getControllerPos());
        }
        return preCenter.add(
                facing.getStepX() * (size - 1) / 2.0,
                (size - 1) / 2.0,
                facing.getStepZ() * (size - 1) / 2.0
        );
    }

    public static Vec3 calculateSelectedPosition(Vec3 relative, Vec3 radarPos, float range, int size) {
        float sizeAdj = size == 1 ? 0.5f : ((size - 1) / 2f);
        if (size == 2) {
            sizeAdj = 0.75f;
        }
        return radarPos.add(relative.scale(range / sizeAdj));
    }

    public static Optional<String> findClosestEntity(Vec3 selected, float range, Iterable<RadarTrack> tracks, Iterable<VSRadarTracks> vsTracks) {
        double bestDistance = 0.1f * range;
        String closestEntity = null;

        for (RadarTrack track : tracks) {
            Vec3 entityPos = track.position().multiply(1, 0, 1);
            double newDistance = entityPos.distanceTo(selected.multiply(1, 0, 1));
            if (newDistance < bestDistance) {
                bestDistance = newDistance;
                closestEntity = track.entityId();
            }
        }

        for (VSRadarTracks track : vsTracks) {
            Vec3 entityPos = track.position().multiply(1, 0, 1);
            double newDistance = entityPos.distanceTo(selected.multiply(1, 0, 1));
            if (newDistance < bestDistance) {
                bestDistance = newDistance;
                closestEntity = track.id();
            }
        }

        return Optional.ofNullable(closestEntity);
    }
}
