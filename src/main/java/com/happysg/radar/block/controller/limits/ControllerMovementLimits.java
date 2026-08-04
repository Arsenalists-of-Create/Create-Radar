package com.happysg.radar.block.controller.limits;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;
import com.happysg.radar.targeting.TargetingMath;

import java.util.Optional;

public record ControllerMovementLimits(CannonAxis axis, double minDegrees,
                                       double maxDegrees) {
    public static final double ANGLE_EPSILON_DEG =
            TargetingMath.ANGLE_EPSILON_DEG;

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
                && minDegrees <= -180.0 + ANGLE_EPSILON_DEG
                && maxDegrees >= 180.0 - ANGLE_EPSILON_DEG;
    }

    public Optional<ControllerMovementLimits> intersection(
            ControllerMovementLimits supported
    ) {
        if (supported == null || supported.axis != axis) {
            return Optional.empty();
        }
        double constrainedMin = Math.max(minDegrees, supported.minDegrees);
        double constrainedMax = Math.min(maxDegrees, supported.maxDegrees);
        if (constrainedMin > constrainedMax + ANGLE_EPSILON_DEG) {
            return Optional.empty();
        }
        if (constrainedMin > constrainedMax) {
            double boundary = (constrainedMin + constrainedMax) * 0.5;
            constrainedMin = boundary;
            constrainedMax = boundary;
        }
        return Optional.of(new ControllerMovementLimits(axis,
                canonicalZero(constrainedMin),
                canonicalZero(constrainedMax)));
    }

    public ControllerMovementLimits constrainedTo(
            ControllerMovementLimits supported
    ) {
        if (supported == null || supported.axis != axis) {
            return this;
        }
        return intersection(supported).orElseThrow(() ->
                new IllegalArgumentException(
                        "Movement limits do not overlap supported range"));
    }

    public boolean allowsControllerTarget(double controllerTargetDegrees,
                                          double neutralControllerDegrees) {
        if (!Double.isFinite(controllerTargetDegrees)
                || !Double.isFinite(neutralControllerDegrees)) {
            return false;
        }
        if (axis == CannonAxis.PITCH) {
            return controllerTargetDegrees >= minDegrees - ANGLE_EPSILON_DEG
                    && controllerTargetDegrees <= maxDegrees + ANGLE_EPSILON_DEG;
        }
        if (isUnrestrictedYaw()) {
            return true;
        }
        double offset = yawOffsetForRange(
                neutralControllerDegrees, controllerTargetDegrees);
        return offset >= minDegrees - ANGLE_EPSILON_DEG
                && offset <= maxDegrees + ANGLE_EPSILON_DEG;
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
        if (Math.abs(offset + 180.0) <= ANGLE_EPSILON_DEG
                && minDegrees > -180.0 + ANGLE_EPSILON_DEG
                && maxDegrees >= 180.0 - ANGLE_EPSILON_DEG) {
            return 180.0;
        }
        return offset;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) <= ANGLE_EPSILON_DEG ? 0.0 : value;
    }
}
