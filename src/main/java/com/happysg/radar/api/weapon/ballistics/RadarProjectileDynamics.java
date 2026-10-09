package com.happysg.radar.api.weapon.ballistics;

import net.minecraft.world.level.Level;

@FunctionalInterface
public interface RadarProjectileDynamics {
    void step(
            int tick,
            double positionX,
            double positionY,
            double positionZ,
            double velocityX,
            double velocityY,
            double velocityZ,
            Level level,
            RadarProjectileStep output
    );
}