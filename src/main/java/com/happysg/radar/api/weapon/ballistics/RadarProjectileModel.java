package com.happysg.radar.api.weapon.ballistics;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Simple projectile models only need to provide speed, gravity and drag.
 * More advanced integrations may provide complete per-tick dynamics by
 * overriding {@link #usesCustomDynamics()} and
 * {@link #createDynamics(Vec3, Vec3, Vec3)}.
 */
public interface RadarProjectileModel {
    double muzzleSpeed();

    double gravity();

    double drag();

    boolean quadraticDrag();

    default boolean cbcPhysics() { return false; }

    default double dragDensity() { return 1.0; }

    /**
     * @return true when this model supplies its own complete per-tick
     * projectile integration.
     */
    default boolean usesCustomDynamics() {
        return false;
    }

    /**
     * Creates isolated dynamics state for one simulated trajectory.
     */
    default RadarProjectileDynamics createDynamics(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity) {
        return this::step;
    }

    /**
     * Performs one custom projectile integration step.
     * This is only used when {@link #usesCustomDynamics()} returns true.
     */
    default void step(
            int tick,
            double positionX,
            double positionY,
            double positionZ,
            double velocityX,
            double velocityY,
            double velocityZ,
            Level level,
            RadarProjectileStep output
    ) {
        throw new UnsupportedOperationException("Projectile model does not define custom dynamics");
    }

    default double estimateFlightTicks(double distance) {
        double speed = Math.max(1.0E-6, muzzleSpeed());
        return Double.isFinite(distance) && distance > 0.0 ? distance / speed : 0.0;
    }

    default Vec3 velocityAfterTick(Vec3 velocity) {
        if (velocity == null || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            return Vec3.ZERO;
        }

        Vec3 next = velocity;
        double speed = next.length();

        double dragForce = drag() * speed;

        if (quadraticDrag()) {
            dragForce *= speed;
        }

        dragForce = Math.min(dragForce, speed);

        if (dragForce > 0.0 && speed > 1.0E-8) {
            next = next.add(
                    next.normalize().scale(-dragForce)
            );
        }

        return next.add(0.0, gravity(), 0.0);
    }

    static RadarProjectileModel simple(double muzzleSpeed, double gravity, double drag) {
        return simple(
                muzzleSpeed,
                gravity,
                drag,
                false
        );
    }

    static RadarProjectileModel simple(double muzzleSpeed, double gravity, double drag, boolean quadraticDrag) {
        return new Simple(
                muzzleSpeed,
                gravity,
                drag,
                quadraticDrag,
                false,
                1.0
        );
    }

    static RadarProjectileModel cbc(double muzzleSpeed, double gravity, double drag, double dragDensity, boolean quadraticDrag) {
        return new Simple(muzzleSpeed, gravity, drag, quadraticDrag, true, dragDensity);
    }

    record Simple(
            double muzzleSpeed,
            double gravity,
            double drag,
            boolean quadraticDrag,
            boolean cbcPhysics,
            double dragDensity
    ) implements RadarProjectileModel { }
}