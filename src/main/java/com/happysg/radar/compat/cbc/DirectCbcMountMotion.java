package com.happysg.radar.compat.cbc;

/**
 * Motion profile used by direct CBC mount control. Values are expressed in
 * controller degrees and ticks so aiming and slew prediction share one model.
 */
public final class DirectCbcMountMotion {
    public static final double MAX_DEGREES_PER_TICK = 5.0;
    public static final double MAX_ACCELERATION_DEGREES_PER_TICK2 = 0.5;
    private static final double RPM_TO_DEGREES_PER_TICK = 1.0 / 12.0;
    private static final double EPSILON = 1.0E-6;
    private static final double TRACKING_RATE_BLEND = 0.45;
    private static final double TRACKING_ERROR_GAIN = 0.75;
    private static final double TRACKING_ACCELERATION_DEGREES_PER_TICK2 = 1.0;
    private static final double TRACKING_MIN_RATE = 0.15;
    private static final double TRACKING_MAX_SETPOINT_JUMP = MAX_DEGREES_PER_TICK * 1.5;

    private DirectCbcMountMotion() {}

    public static double maxSpeedForRpm(double rpm) {
        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            return 0.0;
        }
        return Math.min(MAX_DEGREES_PER_TICK, Math.abs(rpm) * RPM_TO_DEGREES_PER_TICK);
    }

    public static double estimateTicks(double angleDegrees, double rpm) {
        double distance = Math.abs(angleDegrees);
        double maxSpeed = maxSpeedForRpm(rpm);
        if (!Double.isFinite(distance) || distance <= EPSILON || maxSpeed <= EPSILON) {
            return 0.0;
        }

        double acceleration = MAX_ACCELERATION_DEGREES_PER_TICK2;
        double accelerationDistance = maxSpeed * maxSpeed / acceleration;
        if (distance <= accelerationDistance) {
            return 2.0 * Math.sqrt(distance / acceleration);
        }
        return 2.0 * maxSpeed / acceleration + (distance - accelerationDistance) / maxSpeed;
    }

    public static final class State {
        private double velocityDegreesPerTick;
        private double lastTargetDegrees = Double.NaN;
        private long lastTargetTick = Long.MIN_VALUE;
        private double filteredTargetRate;

        public double nextStep(double errorDegrees, double rpm) {
            if (!Double.isFinite(errorDegrees)) {
                reset();
                return 0.0;
            }

            double distance = Math.abs(errorDegrees);
            double maxSpeed = maxSpeedForRpm(rpm);
            if (distance <= EPSILON || maxSpeed <= EPSILON) {
                reset();
                return distance <= EPSILON ? errorDegrees : 0.0;
            }

            double direction = Math.signum(errorDegrees);
            if (velocityDegreesPerTick != 0.0 && Math.signum(velocityDegreesPerTick) != direction) {
                velocityDegreesPerTick = 0.0;
            }

            double brakingSpeed = Math.sqrt(2.0 * MAX_ACCELERATION_DEGREES_PER_TICK2 * distance);
            double desiredVelocity = direction * Math.min(maxSpeed, brakingSpeed);
            velocityDegreesPerTick = approach(velocityDegreesPerTick, desiredVelocity, MAX_ACCELERATION_DEGREES_PER_TICK2);

            double step = direction * Math.min(distance, Math.abs(velocityDegreesPerTick));
            if (Math.abs(step) >= distance - EPSILON) {
                velocityDegreesPerTick = 0.0;
            }
            return step;
        }

        public double nextTrackingStep(double errorDegrees, double rpm, double targetDegrees, long gameTick, boolean wrappedAngle) {
            if (!Double.isFinite(errorDegrees) || !Double.isFinite(targetDegrees)) {
                reset();
                return 0.0;
            }

            double maxSpeed = maxSpeedForRpm(rpm);
            if (maxSpeed <= EPSILON) {
                reset();
                return 0.0;
            }

            boolean consecutive = lastTargetTick != Long.MIN_VALUE && gameTick == lastTargetTick + 1;
            if (consecutive) {
                double change = targetDegrees - lastTargetDegrees;

                if (wrappedAngle) {
                    change = (change + 540.0) % 360.0 - 180.0;
                }

                if (Math.abs(change) <= TRACKING_MAX_SETPOINT_JUMP) {
                    filteredTargetRate = filteredTargetRate * (1.0 - TRACKING_RATE_BLEND) + change * TRACKING_RATE_BLEND;
                } else {

                    filteredTargetRate = 0.0;
                }
            } else {
                filteredTargetRate = 0.0;
            }

            lastTargetDegrees = targetDegrees;
            lastTargetTick = gameTick;

            if (!consecutive || Math.abs(filteredTargetRate) < TRACKING_MIN_RATE) {
                return nextStep(errorDegrees, rpm);
            }

            double distance = Math.abs(errorDegrees);
            if (distance <= EPSILON) {
                velocityDegreesPerTick = 0.0;
                return 0.0;
            }

            double direction = Math.signum(errorDegrees);
            if (velocityDegreesPerTick != 0.0 && Math.signum(velocityDegreesPerTick) != direction) {
                velocityDegreesPerTick = 0.0;
            }

            double requestedVelocity;

            if (!consecutive || Math.abs(filteredTargetRate) < TRACKING_MIN_RATE) {
                double brakingSpeed = Math.sqrt(2.0 * TRACKING_ACCELERATION_DEGREES_PER_TICK2 * distance);
                requestedVelocity = direction * Math.min(maxSpeed, brakingSpeed);
            } else {
                requestedVelocity = filteredTargetRate + TRACKING_ERROR_GAIN * errorDegrees;
                requestedVelocity = Math.max(-maxSpeed, Math.min(maxSpeed, requestedVelocity));
            }

            velocityDegreesPerTick = approach(velocityDegreesPerTick, requestedVelocity, TRACKING_ACCELERATION_DEGREES_PER_TICK2);
            double step = direction * Math.min(distance, Math.max(0.0, velocityDegreesPerTick * direction));
            velocityDegreesPerTick = step;
            return step;
        }

        public void recordAppliedStep(double appliedStep) {
            velocityDegreesPerTick = Double.isFinite(appliedStep) ? appliedStep : 0.0;
        }

        public void reset() {
            velocityDegreesPerTick = 0.0;
            lastTargetDegrees = Double.NaN;
            lastTargetTick = Long.MIN_VALUE;
            filteredTargetRate = 0.0;
        }

        double velocityDegreesPerTick() {
            return velocityDegreesPerTick;
        }
    }

    private static double approach(double current, double target, double maximumChange) {
        if (current < target) {
            return Math.min(current + maximumChange, target);
        }
        return Math.max(current - maximumChange, target);
    }
}
