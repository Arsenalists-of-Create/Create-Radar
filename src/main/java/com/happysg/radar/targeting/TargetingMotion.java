package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public final class TargetingMotion {
    private TargetingMotion() {}

    public static double accelerationTrust(TargetMotionClass motionClass) {
        if (motionClass == TargetMotionClass.ERRATIC) {
            return 0.25;
        }

        if (motionClass == TargetMotionClass.UNKNOWN || motionClass == null) {
            return 0.5;
        }

        return 1.0;
    }

    public static Vec3 effectiveAcceleration(Vec3 acceleration, TargetMotionClass motionClass) {
        if (acceleration == null
                || !Double.isFinite(acceleration.x)
                || !Double.isFinite(acceleration.y)
                || !Double.isFinite(acceleration.z)) {
            return Vec3.ZERO;
        }

        double lengthSq = acceleration.lengthSqr();
        Vec3 clamped = lengthSq > 0.25 * 0.25 ? acceleration.scale(0.25 / Math.sqrt(lengthSq)) : acceleration;
        return clamped.scale(accelerationTrust(motionClass));
    }

    public static Vec3 futurePosition(Vec3 position, Vec3 velocity, Vec3 acceleration, double ticks) {
        double t = Double.isFinite(ticks) ? Math.max(0.0, ticks) : 0.0;
        return position.add(velocity.scale(t)).add(acceleration.scale(0.5 * t * t));
    }

    public static Vec3 futureVelocity(Vec3 velocity, Vec3 acceleration, double ticks) {
        double t = Double.isFinite(ticks) ? Math.max(0.0, ticks) : 0.0;
        return velocity.add(acceleration.scale(t));
    }
}