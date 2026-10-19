
package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public final class RigidBodyLaunchMath {
    private RigidBodyLaunchMath() {}

    public record Motion(Vec3 origin, Vec3 linearVelocity, Vec3 angularVelocity) {
        public Vec3 futurePosition(Vec3 point, double ticks) {
            double t = safeTime(ticks);
            Vec3 offset = rotate(point.subtract(origin), angularVelocity, t);
            return origin.add(linearVelocity.scale(t)).add(offset);
        }

        public Vec3 futureVelocity(Vec3 point, double ticks) {
            double t = safeTime(ticks);
            Vec3 offset = rotate(point.subtract(origin), angularVelocity, t);
            return linearVelocity.add(angularVelocity.cross(offset));
        }
    }

    public static Motion fromVelocityProbes(
            Vec3 origin,
            Vec3 originVelocity,
            Vec3 velocityX,
            Vec3 velocityY,
            Vec3 velocityZ,
            double sampleDistance
    ) {
        if (!finite(origin)
                || !finite(originVelocity)
                || !finite(velocityX)
                || !finite(velocityY)
                || !finite(velocityZ)
                || !Double.isFinite(sampleDistance)
                || sampleDistance <= 0.0) {
            return null;
        }

        double inverse = 1.0 / sampleDistance;

        Vec3 dx = velocityX.subtract(originVelocity).scale(inverse);
        Vec3 dy = velocityY.subtract(originVelocity).scale(inverse);
        Vec3 dz = velocityZ.subtract(originVelocity).scale(inverse);

        Vec3 omega = new Vec3(
                0.5 * (dy.z - dz.y),
                0.5 * (dz.x - dx.z),
                0.5 * (dx.y - dy.x)
        );

        return finite(omega) ? new Motion(origin, originVelocity, omega) : null;
    }

    private static Vec3 rotate(Vec3 offset, Vec3 omega, double ticks) {
        double speed = omega.length();

        if (speed < 1.0e-12 || ticks <= 0.0) {
            return offset;
        }

        Vec3 axis = omega.scale(1.0 / speed);
        double angle = speed * ticks;

        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);

        return offset.scale(cosine).add(axis.cross(offset).scale(sine)).add(axis.scale(axis.dot(offset) * (1.0 - cosine)));
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
