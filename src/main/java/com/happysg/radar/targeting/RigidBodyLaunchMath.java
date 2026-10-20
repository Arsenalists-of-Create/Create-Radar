
package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public final class RigidBodyLaunchMath {
    private RigidBodyLaunchMath() {}

    private static final double ACCELERATION_HORIZON_TICKS = 20.0;

    public record Motion(Vec3 origin, Vec3 linearVelocity, Vec3 angularVelocity, Vec3 linearAcceleration, Vec3 angularAcceleration) {
        // Preserve compatibility with existing three-argument constructors.
        public Motion(Vec3 origin, Vec3 linearVelocity, Vec3 angularVelocity) {
            this(origin, linearVelocity, angularVelocity, Vec3.ZERO, Vec3.ZERO);
        }

        public Vec3 futurePosition(Vec3 point, double ticks) {
            double t = safeTime(ticks);
            double accelIntegral = accelerationIntegral(t);
            Vec3 rotationVector = angularVelocity.scale(t).add(angularAcceleration.scale(accelIntegral));
            Vec3 offset = rotate(point.subtract(origin), rotationVector);
            return origin.add(linearVelocity.scale(t)).add(linearAcceleration.scale(accelIntegral)).add(offset);
        }

        public Vec3 futureVelocity(Vec3 point, double ticks) {
            double t = safeTime(ticks);
            double accelIntegral = accelerationIntegral(t);

            Vec3 rotationVector = angularVelocity.scale(t).add(angularAcceleration.scale(accelIntegral));
            Vec3 offset = rotate(point.subtract(origin), rotationVector);
            Vec3 linear = linearVelocity.add(linearAcceleration.scale(Math.min(t, ACCELERATION_HORIZON_TICKS)));

            return linear.add(angularVelocityAt(t).cross(offset));
        }

        public Vec3 angularVelocityAt(double ticks) {
            return angularVelocity.add(angularAcceleration.scale(Math.min(safeTime(ticks), ACCELERATION_HORIZON_TICKS)));
        }
    }

    public static Motion fromVelocityProbes(
            Vec3 origin, Vec3 originVelocity,
            Vec3 velocityX, Vec3 velocityY, Vec3 velocityZ,
            double sampleDistance
    ) {
        if (!finite(origin)
                || !finite(originVelocity)
                || !finite(velocityX) || !finite(velocityY) || !finite(velocityZ)
                || !Double.isFinite(sampleDistance) || sampleDistance <= 0.0) {
            return null;
        }

        double inverse = 1.0 / sampleDistance;

        Vec3 dx = velocityX.subtract(originVelocity).scale(inverse);
        Vec3 dy = velocityY.subtract(originVelocity).scale(inverse);
        Vec3 dz = velocityZ.subtract(originVelocity).scale(inverse);

        Vec3 omega = new Vec3(0.5 * (dy.z - dz.y), 0.5 * (dz.x - dx.z), 0.5 * (dx.y - dy.x));

        return finite(omega) ? new Motion(origin, originVelocity, omega) : null;
    }

    private static Vec3 rotate(Vec3 offset, Vec3 rotationVector) {
        double angle = rotationVector.length();

        if (angle < 1.0e-12) {
            return offset;
        }

        Vec3 axis = rotationVector.scale(1.0 / angle);

        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);

        return offset.scale(cosine).add(axis.cross(offset).scale(sine)).add(axis.scale(axis.dot(offset) * (1.0 - cosine)));
    }

    private static double accelerationIntegral(double ticks) {
        double t = safeTime(ticks);
        double acceleratedTicks = Math.min(t, ACCELERATION_HORIZON_TICKS);
        return acceleratedTicks * (t - 0.5 * acceleratedTicks);
    }

    private static double safeTime(double ticks) {
        return Double.isFinite(ticks) ? Math.max(0.0, ticks) : 0.0;
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
