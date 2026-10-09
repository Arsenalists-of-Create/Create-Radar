package com.happysg.radar.api.weapon.ballistics;

/**
 * Mutable output used by custom public projectile models during trajectory
 * simulation.
 */
public final class RadarProjectileStep {

    public double positionX;
    public double positionY;
    public double positionZ;

    public double velocityX;
    public double velocityY;
    public double velocityZ;

    public RadarProjectileStep set(
            double positionX,
            double positionY,
            double positionZ,
            double velocityX,
            double velocityY,
            double velocityZ
    ) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;

        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;

        return this;
    }
}