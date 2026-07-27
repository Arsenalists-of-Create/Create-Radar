package com.happysg.radar.block.controller.kinetic;

import net.minecraft.world.phys.Vec3;


public record KineticAimFrame(Vec3 rightAxis, Vec3 upAxis, Vec3 forwardAxis) {
    private static final double EPSILON = 1.0e-12;
    private static final Vec3 WORLD_RIGHT = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_FORWARD = new Vec3(0.0, 0.0, 1.0);

    public KineticAimFrame {
        Vec3 safeUp = normalizeOr(upAxis, WORLD_UP);
        Vec3 safeRight = projectPerpendicular(normalizeOr(rightAxis, WORLD_RIGHT), safeUp);
        if (safeRight.lengthSqr() < EPSILON) {
            safeRight = projectPerpendicular(
                    Math.abs(safeUp.y) < 0.9 ? WORLD_UP : WORLD_RIGHT, safeUp);
        }
        safeRight = safeRight.normalize();

        Vec3 derivedForward = safeRight.cross(safeUp);
        if (derivedForward.lengthSqr() < EPSILON) {
            safeRight = WORLD_RIGHT;
            safeUp = WORLD_UP;
            derivedForward = WORLD_FORWARD;
        } else {
            derivedForward = derivedForward.normalize();
            Vec3 suppliedForward = normalizeOr(forwardAxis, derivedForward);
            if (derivedForward.dot(suppliedForward) < 0.0) {
                safeRight = safeRight.scale(-1.0);
                derivedForward = safeRight.cross(safeUp).normalize();
            }
        }

        rightAxis = safeRight;
        upAxis = safeUp;
        forwardAxis = derivedForward;
    }

    public static KineticAimFrame world() {
        return new KineticAimFrame(WORLD_RIGHT, WORLD_UP, WORLD_FORWARD);
    }

    public Vec3 toLocalDirection(Vec3 worldDirection) {
        if (!finite(worldDirection) || worldDirection.lengthSqr() < EPSILON) {
            return Vec3.ZERO;
        }
        Vec3 normalized = worldDirection.normalize();
        Vec3 local = new Vec3(
                normalized.dot(rightAxis),
                normalized.dot(upAxis),
                normalized.dot(forwardAxis));
        return local.lengthSqr() < EPSILON ? Vec3.ZERO : local.normalize();
    }

    /**
     * Converts a world launch direction to the absolute angle expected by the
     * selected controller. Yaw uses south=0/west=90; pitch uses signed
     * elevation.
     */
    public double controllerTargetDegrees(CannonAxis axis, Vec3 worldDirection) {
        Vec3 local = toLocalDirection(worldDirection);
        if (local.lengthSqr() < EPSILON) {
            return Double.NaN;
        }
        if (axis == CannonAxis.YAW) {
            return KineticAngleMath.wrap360(
                    Math.toDegrees(Math.atan2(local.z, local.x)) - 90.0);
        }
        return Math.toDegrees(Math.atan2(local.y, Math.hypot(local.x, local.z)));
    }

    /** Reconstructs a world direction from controller yaw and pitch angles. */
    public Vec3 worldDirection(double controllerYawDegrees, double pitchDegrees) {
        if (!Double.isFinite(controllerYawDegrees) || !Double.isFinite(pitchDegrees)) {
            return Vec3.ZERO;
        }
        double yaw = Math.toRadians(controllerYawDegrees - 270.0);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        Vec3 local = new Vec3(
                horizontal * Math.cos(yaw),
                Math.sin(pitch),
                horizontal * Math.sin(yaw));
        Vec3 world = rightAxis.scale(local.x)
                .add(upAxis.scale(local.y))
                .add(forwardAxis.scale(local.z));
        return world.lengthSqr() < EPSILON ? Vec3.ZERO : world.normalize();
    }

    private static Vec3 projectPerpendicular(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(vector.dot(normal)));
    }

    private static Vec3 normalizeOr(Vec3 vector, Vec3 fallback) {
        return finite(vector) && vector.lengthSqr() >= EPSILON
                ? vector.normalize() : fallback;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
