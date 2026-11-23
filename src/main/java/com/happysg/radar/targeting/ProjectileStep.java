package com.happysg.radar.targeting;

/**
 * Mutable output used by custom projectile models in the solver hot path.
 * A single instance is reused for an entire trajectory to avoid allocating
 * vectors for every candidate tick.
 */
public final class ProjectileStep {
    public double positionX;
    public double positionY;
    public double positionZ;
    public double velocityX;
    public double velocityY;
    public double velocityZ;

    public ProjectileStep set(
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
