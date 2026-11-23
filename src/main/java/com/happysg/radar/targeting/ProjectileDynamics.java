package com.happysg.radar.targeting;

import net.minecraft.world.level.Level;

@FunctionalInterface
public interface ProjectileDynamics {
    void step(
            int tick,
            double positionX,
            double positionY,
            double positionZ,
            double velocityX,
            double velocityY,
            double velocityZ,
            Level level,
            ProjectileStep output
    );
}
