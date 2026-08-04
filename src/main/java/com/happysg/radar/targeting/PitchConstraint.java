package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable mount-relative pitch limits and reference frame for a world-space
 * targeting solution.
 */
public record PitchConstraint(
        double minPitchDeg,
        double maxPitchDeg,
        Vec3 rightAxis,
        Vec3 upAxis,
        Vec3 forwardAxis
) {
    public static final double SOLVER_MIN_PITCH_DEG = -89.0;
    public static final double SOLVER_MAX_PITCH_DEG = 89.0;
    private static final double EPSILON_DEG =
            TargetingMath.ANGLE_EPSILON_DEG;
    private static final Vec3 WORLD_RIGHT = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_FORWARD = new Vec3(0.0, 0.0, 1.0);

    public PitchConstraint {
        minPitchDeg = sanitizeLimit(minPitchDeg, SOLVER_MIN_PITCH_DEG);
        maxPitchDeg = sanitizeLimit(maxPitchDeg, SOLVER_MAX_PITCH_DEG);

        Vec3 safeUp = normalizeOr(upAxis, WORLD_UP);
        Vec3 safeRight = projectPerpendicular(normalizeOr(rightAxis, WORLD_RIGHT), safeUp);
        if (safeRight.lengthSqr() < 1.0E-12) {
            safeRight = projectPerpendicular(Math.abs(safeUp.y) < 0.9 ? WORLD_UP : WORLD_RIGHT, safeUp);
        }
        safeRight = safeRight.normalize();

        Vec3 derivedForward = safeRight.cross(safeUp);
        if (derivedForward.lengthSqr() < 1.0E-12) {
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

    public static PitchConstraint unconstrained() {
        return world(SOLVER_MIN_PITCH_DEG, SOLVER_MAX_PITCH_DEG);
    }

    public static PitchConstraint world(double minPitchDeg, double maxPitchDeg) {
        return new PitchConstraint(minPitchDeg, maxPitchDeg, WORLD_RIGHT, WORLD_UP, WORLD_FORWARD);
    }

    public static PitchConstraint intersect(
            double controllerMinPitchDeg,
            double controllerMaxPitchDeg,
            double cannonMinPitchDeg,
            double cannonMaxPitchDeg
    ) {
        return intersect(controllerMinPitchDeg, controllerMaxPitchDeg, cannonMinPitchDeg, cannonMaxPitchDeg,
                WORLD_RIGHT, WORLD_UP, WORLD_FORWARD);
    }

    public static PitchConstraint intersect(
            double controllerMinPitchDeg,
            double controllerMaxPitchDeg,
            double cannonMinPitchDeg,
            double cannonMaxPitchDeg,
            Vec3 rightAxis,
            Vec3 upAxis,
            Vec3 forwardAxis
    ) {
        double minPitch = Math.max(SOLVER_MIN_PITCH_DEG,
                Math.max(finiteOr(controllerMinPitchDeg, SOLVER_MIN_PITCH_DEG),
                        finiteOr(cannonMinPitchDeg, SOLVER_MIN_PITCH_DEG)));
        double maxPitch = Math.min(SOLVER_MAX_PITCH_DEG,
                Math.min(finiteOr(controllerMaxPitchDeg, SOLVER_MAX_PITCH_DEG),
                        finiteOr(cannonMaxPitchDeg, SOLVER_MAX_PITCH_DEG)));
        return new PitchConstraint(minPitch, maxPitch, rightAxis, upAxis, forwardAxis);
    }

    public boolean hasReachablePitch() {
        return minPitchDeg <= maxPitchDeg + EPSILON_DEG;
    }

    public boolean allows(Vec3 worldAimDirection) {
        if (!hasReachablePitch()) {
            return false;
        }
        double pitch = mountYawPitch(worldAimDirection).pitchDeg();
        return Double.isFinite(pitch)
                && pitch >= minPitchDeg - EPSILON_DEG
                && pitch <= maxPitchDeg + EPSILON_DEG;
    }

    public Vec3 toMountRelativeDirection(Vec3 worldDirection) {
        if (!finite(worldDirection) || worldDirection.lengthSqr() < 1.0E-12) {
            return Vec3.ZERO;
        }
        Vec3 normalized = worldDirection.normalize();
        return new Vec3(
                normalized.dot(rightAxis),
                normalized.dot(upAxis),
                normalized.dot(forwardAxis)
        ).normalize();
    }

    public TargetingMath.YawPitch mountYawPitch(Vec3 worldDirection) {
        return TargetingMath.yawPitchFromDirection(toMountRelativeDirection(worldDirection));
    }

    public String summary() {
        return "min=" + minPitchDeg + ",max=" + maxPitchDeg + ",up=" + upAxis;
    }

    private static Vec3 projectPerpendicular(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(vector.dot(normal)));
    }

    private static Vec3 normalizeOr(Vec3 vector, Vec3 fallback) {
        return finite(vector) && vector.lengthSqr() >= 1.0E-12 ? vector.normalize() : fallback;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static double sanitizeLimit(double value, double fallback) {
        return Math.max(SOLVER_MIN_PITCH_DEG, Math.min(SOLVER_MAX_PITCH_DEG, finiteOr(value, fallback)));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
