package com.happysg.radar.api.targeting;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Resolved world-space target state supplied to Create: Radars weapon guidance.
 * All position, velocity, and acceleration values must be expressed in
 * world coordinates. Addons targeting objects in moving coordinate frames
 * should use {@link com.happysg.radar.api.physics.RadarPhysics} before
 * returning a solution.
 */
public record RadarTargetingSolution(Vec3 position, Vec3 velocity, Vec3 acceleration) {

    public RadarTargetingSolution {
        position = Objects.requireNonNull(position, "position");
        velocity = Objects.requireNonNullElse(velocity, Vec3.ZERO);
        acceleration = Objects.requireNonNullElse(acceleration, Vec3.ZERO);

        if (!finite(position) || !finite(velocity) || !finite(acceleration)) {
            throw new IllegalArgumentException("Radar targeting solution vectors must be finite");
        }
    }

    public static RadarTargetingSolution of(Vec3 position, Vec3 velocity) {
        return new RadarTargetingSolution(position, velocity, Vec3.ZERO);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}