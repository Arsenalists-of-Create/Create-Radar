package com.happysg.radar.api.radar.scan;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface RadarScanPolicy {
    RadarVisibilityResult test(ServerLevel level, Vec3 sensorPosition, Vec3 targetPosition);
}