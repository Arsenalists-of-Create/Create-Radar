
package com.happysg.radar.targeting;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Predicts a selected point on a moving, rotating rigid body.
 */
public record RigidBodyTargetMotion(RigidBodyLaunchMath.Motion motion, Vec3 observedWorldPoint, double launchDelayTicks) {
    public RigidBodyTargetMotion {
        Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(observedWorldPoint, "observedWorldPoint");

        if (!finite(observedWorldPoint) || !Double.isFinite(launchDelayTicks) || launchDelayTicks < 0.0) {
            throw new IllegalArgumentException("Invalid rigid-body target observation");
        }
    }

    public Vec3 positionAt(double flightTicks) {
        return motion.futurePosition(observedWorldPoint, totalTicks(flightTicks));
    }

    public Vec3 velocityAt(double flightTicks) {
        return motion.futureVelocity(observedWorldPoint, totalTicks(flightTicks));
    }

    public Vec3 launchPosition() {
        return positionAt(0.0);
    }

    public Vec3 launchVelocity() {
        return velocityAt(0.0);
    }

    private double totalTicks(double flightTicks) {
        double safeFlightTicks = Double.isFinite(flightTicks) ? Math.max(0.0, flightTicks) : 0.0;
        return launchDelayTicks + safeFlightTicks;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
