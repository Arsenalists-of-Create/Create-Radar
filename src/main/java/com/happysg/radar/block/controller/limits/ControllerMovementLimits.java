package com.happysg.radar.block.controller.limits;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;

import java.util.Optional;

public record ControllerMovementLimits(CannonAxis axis, double minDegrees,
                                       double maxDegrees) {
    private static final double EPSILON = 1.0e-7;

    public static ControllerMovementLimits defaults(CannonAxis axis) {
        return axis == CannonAxis.PITCH
                ? new ControllerMovementLimits(axis, -90.0, 90.0)
                : new ControllerMovementLimits(axis, -180.0, 180.0);
    }

    public static Optional<ControllerMovementLimits> validated(
            CannonAxis axis, double minDegrees, double maxDegrees
    ) {
        if (axis == null || !Double.isFinite(minDegrees)
                || !Double.isFinite(maxDegrees) || minDegrees > maxDegrees) {
            return Optional.empty();
        }
        double lower = axis == CannonAxis.PITCH ? -90.0 : -180.0;
        double upper = axis == CannonAxis.PITCH ? 90.0 : 180.0;
        if (minDegrees < lower || maxDegrees > upper) {
            return Optional.empty();
        }
        return Optional.of(new ControllerMovementLimits(
                axis, canonicalZero(minDegrees), canonicalZero(maxDegrees)));
    }

    public boolean isUnrestrictedYaw() {
        return axis == CannonAxis.YAW
                && minDegrees <= -180.0 + EPSILON
                && maxDegrees >= 180.0 - EPSILON;
    }

    public boolean allowsControllerTarget(double controllerTargetDegrees,
                                          double neutralControllerDegrees) {
        if (!Double.isFinite(controllerTargetDegrees)
                || !Double.isFinite(neutralControllerDegrees)) {
            return false;
        }
        if (axis == CannonAxis.PITCH) {
            return controllerTargetDegrees >= minDegrees - EPSILON
                    && controllerTargetDegrees <= maxDegrees + EPSILON;
        }
        if (isUnrestrictedYaw()) {
            return true;
        }
        double offset = yawOffsetForRange(
                neutralControllerDegrees, controllerTargetDegrees);
        return offset >= minDegrees - EPSILON
                && offset <= maxDegrees + EPSILON;
    }

    public double clampControllerTarget(double controllerTargetDegrees,
                                        double neutralControllerDegrees) {
        if (!Double.isFinite(controllerTargetDegrees)
                || !Double.isFinite(neutralControllerDegrees)) {
            return Double.NaN;
        }
        if (axis == CannonAxis.PITCH) {
            return clamp(controllerTargetDegrees, minDegrees, maxDegrees);
        }
        if (isUnrestrictedYaw()
                || allowsControllerTarget(controllerTargetDegrees,
                neutralControllerDegrees)) {
            return KineticAngleMath.wrap360(controllerTargetDegrees);
        }

        double minTarget = KineticAngleMath.wrap360(
                neutralControllerDegrees + minDegrees);
        double maxTarget = KineticAngleMath.wrap360(
                neutralControllerDegrees + maxDegrees);
        double minDistance = Math.abs(KineticAngleMath.shortestDelta(
                controllerTargetDegrees, minTarget));
        double maxDistance = Math.abs(KineticAngleMath.shortestDelta(
                controllerTargetDegrees, maxTarget));
        return minDistance <= maxDistance ? minTarget : maxTarget;
    }


    public double legalDelta(double currentControllerDegrees,
                             double targetControllerDegrees,
                             double neutralControllerDegrees) {
        if (!Double.isFinite(currentControllerDegrees)
                || !Double.isFinite(targetControllerDegrees)
                || !Double.isFinite(neutralControllerDegrees)) {
            return Double.NaN;
        }
        if (axis == CannonAxis.PITCH) {
            return targetControllerDegrees - currentControllerDegrees;
        }
        if (isUnrestrictedYaw()) {
            return KineticAngleMath.shortestDelta(
                    currentControllerDegrees, targetControllerDegrees);
        }

        if (!allowsControllerTarget(currentControllerDegrees,
                neutralControllerDegrees)) {
            double boundary = clampControllerTarget(
                    currentControllerDegrees, neutralControllerDegrees);
            return KineticAngleMath.shortestDelta(
                    currentControllerDegrees, boundary);
        }

        double currentOffset = yawOffsetForRange(
                neutralControllerDegrees, currentControllerDegrees);
        double targetOffset = yawOffsetForRange(
                neutralControllerDegrees, targetControllerDegrees);
        targetOffset = clamp(targetOffset, minDegrees, maxDegrees);
        return targetOffset - currentOffset;
    }

    private double yawOffsetForRange(double neutralDegrees, double angleDegrees) {
        double offset = KineticAngleMath.shortestDelta(neutralDegrees, angleDegrees);
        // +180 and -180 are the same heading
        if (Math.abs(offset + 180.0) <= EPSILON
                && minDegrees > -180.0 + EPSILON
                && maxDegrees >= 180.0 - EPSILON) {
            return 180.0;
        }
        return offset;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) <= EPSILON ? 0.0 : value;
    }
}
