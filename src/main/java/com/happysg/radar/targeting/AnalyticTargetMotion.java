package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

public record AnalyticTargetMotion(Vec3 position, Vec3 velocity, Vec3 acceleration, double accelerationRemainingTicks) {
    private static final double MAX_ROTATION_ERROR = 1.0e-4;

    @Nullable
    public static AnalyticTargetMotion from(TargetingSnapshot snapshot, double accelerationTrust) {
        if (snapshot == null) {
            return null;
        }

        RigidBodyTargetMotion rigid = snapshot.rigidBodyTargetMotion();
        if (rigid == null) {
            Vec3 acceleration = snapshot.targetAcceleration();
            double length = acceleration.length();

            if (length > 0.25) {
                acceleration = acceleration.scale(0.25 / length);
            }

            acceleration = acceleration.scale(Math.max(0.0, Math.min(1.0, accelerationTrust)));

            return new AnalyticTargetMotion(snapshot.targetPosition(), snapshot.targetVelocity(), acceleration, snapshot.targetAccelerationRemainingTicks());
        }

        RigidBodyLaunchMath.Motion motion = rigid.motion();
        if (!finite(motion.origin()) || !finite(motion.linearVelocity()) || !finite(motion.linearAcceleration()) || !finite(motion.angularVelocity()) || !finite(motion.angularAcceleration())) {
            return null;
        }

        double totalTicks = rigid.launchDelayTicks() + snapshot.maxFlightTicks();
        double accelerated = Math.min(totalTicks, RigidBodyLaunchMath.ACCELERATION_HORIZON_TICKS);
        double accelerationIntegral = accelerated * (totalTicks - 0.5 * accelerated);
        double angularDisplacement = motion.angularVelocity().length() * totalTicks + motion.angularAcceleration().length() * accelerationIntegral;

        double radius = rigid.observedWorldPoint().distanceTo(motion.origin());
        double rotationError = angularDisplacement * radius;

        if (!Double.isFinite(rotationError) || rotationError > MAX_ROTATION_ERROR) {
            return null;
        }

        double remainingAcceleration = Math.max(0.0, RigidBodyLaunchMath.ACCELERATION_HORIZON_TICKS - rigid.launchDelayTicks());

        Vec3 position = rigid.launchPosition();
        Vec3 velocity = rigid.launchVelocity();
        Vec3 acceleration = motion.linearAcceleration();

        if (!finite(position) || !finite(velocity) || !finite(acceleration)) {
            return null;
        }

        return new AnalyticTargetMotion(position, velocity, acceleration, remainingAcceleration);
    }

    public double accelerationFactor(double ticks) {
        double t = Double.isFinite(ticks) ? Math.max(0.0, ticks) : 0.0;
        double accelerated = Math.min(t, Math.max(0.0, accelerationRemainingTicks));
        return accelerated * (t - 0.5 * accelerated);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
