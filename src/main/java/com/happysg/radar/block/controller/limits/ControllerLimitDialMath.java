package com.happysg.radar.block.controller.limits;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;

/** Pure coordinate and snapping rules for the movement-limit dial. */
public final class ControllerLimitDialMath {
    private static final double TENTHS = 10.0;
    private static final double EPSILON = 1.0e-9;

    private ControllerLimitDialMath() {
    }

    public static Point direction(
            CannonAxis axis, double limitDegrees, double dialZeroDegrees
    ) {
        if (axis == CannonAxis.PITCH) {
            double angle = Math.toRadians(limitDegrees);
            double horizontalSign = Math.cos(
                    Math.toRadians(dialZeroDegrees)) < 0.0 ? -1.0 : 1.0;
            return new Point(horizontalSign * Math.cos(angle),
                    -Math.sin(angle));
        }
        double angle = Math.toRadians(dialZeroDegrees + limitDegrees);
        return new Point(Math.sin(angle), -Math.cos(angle));
    }

    public static double draggedValue(
            CannonAxis axis, Handle handle,
            double pointerX, double pointerY,
            double dialZeroDegrees, double currentValue,
            double currentMin, double currentMax,
            double supportedMin, double supportedMax
    ) {
        if (axis == null || handle == null
                || !Double.isFinite(pointerX)
                || !Double.isFinite(pointerY)
                || pointerX * pointerX + pointerY * pointerY < EPSILON) {
            return currentValue;
        }
        double raw;
        if (axis == CannonAxis.PITCH) {
            double horizontalSign = Math.cos(
                    Math.toRadians(dialZeroDegrees)) < 0.0 ? -1.0 : 1.0;
            raw = Math.toDegrees(Math.atan2(
                    -pointerY, pointerX * horizontalSign));
        } else {
            double screenAngle = Math.toDegrees(
                    Math.atan2(pointerX, -pointerY));
            double wrapped = KineticAngleMath.wrap180(
                    screenAngle - dialZeroDegrees);
            raw = unwrapNear(wrapped, currentValue);
        }

        double axisLower = axis == CannonAxis.PITCH ? -90.0 : -180.0;
        double axisUpper = axis == CannonAxis.PITCH ? 90.0 : 180.0;
        double lower = clamp(supportedMin, axisLower, axisUpper);
        double upper = clamp(supportedMax, lower, axisUpper);
        double candidate = snapTenth(clamp(raw, lower, upper));
        candidate = handle == Handle.LOWER
                ? Math.min(candidate, currentMax)
                : Math.max(candidate, currentMin);
        return canonicalZero(clamp(candidate, lower, upper));
    }

    public static double snapTenth(double degrees) {
        if (!Double.isFinite(degrees)) {
            return Double.NaN;
        }
        return canonicalZero(Math.rint(degrees * TENTHS) / TENTHS);
    }

    private static double unwrapNear(double wrapped, double reference) {
        double result = wrapped;
        for (double candidate : new double[]{wrapped - 360.0,
                wrapped + 360.0}) {
            if (Math.abs(candidate - reference)
                    < Math.abs(result - reference)) {
                result = candidate;
            }
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) < EPSILON ? 0.0 : value;
    }

    public enum Handle {
        LOWER,
        UPPER
    }

    public record Point(double x, double y) {
    }
}
