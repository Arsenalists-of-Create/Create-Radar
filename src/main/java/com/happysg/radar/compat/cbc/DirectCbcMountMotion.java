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

    private DirectCbcMountMotion() {
    }

    public static double maxSpeedForRpm(double rpm) {
        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            return 0.0;
        }
        return Math.min(MAX_DEGREES_PER_TICK,
                Math.abs(rpm) * RPM_TO_DEGREES_PER_TICK);
    }

    public static double estimateTicks(double angleDegrees, double rpm) {
        double distance = Math.abs(angleDegrees);
        double maxSpeed = maxSpeedForRpm(rpm);
        if (!Double.isFinite(distance) || distance <= EPSILON
                || maxSpeed <= EPSILON) {
            return 0.0;
        }

        double acceleration = MAX_ACCELERATION_DEGREES_PER_TICK2;
        double accelerationDistance = maxSpeed * maxSpeed / acceleration;
        if (distance <= accelerationDistance) {
            return 2.0 * Math.sqrt(distance / acceleration);
        }
        return 2.0 * maxSpeed / acceleration
                + (distance - accelerationDistance) / maxSpeed;
    }

    public static final class State {
        private double velocityDegreesPerTick;

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
            if (velocityDegreesPerTick != 0.0
                    && Math.signum(velocityDegreesPerTick) != direction) {
                velocityDegreesPerTick = 0.0;
            }

            double brakingSpeed = Math.sqrt(
                    2.0 * MAX_ACCELERATION_DEGREES_PER_TICK2 * distance);
            double desiredVelocity = direction * Math.min(maxSpeed, brakingSpeed);
            velocityDegreesPerTick = approach(
                    velocityDegreesPerTick,
                    desiredVelocity,
                    MAX_ACCELERATION_DEGREES_PER_TICK2);

            double step = direction * Math.min(
                    distance, Math.abs(velocityDegreesPerTick));
            if (Math.abs(step) >= distance - EPSILON) {
                velocityDegreesPerTick = 0.0;
            }
            return step;
        }

        public void reset() {
            velocityDegreesPerTick = 0.0;
        }

        double velocityDegreesPerTick() {
            return velocityDegreesPerTick;
        }
    }

    private static double approach(double current, double target,
                                   double maximumChange) {
        if (current < target) {
            return Math.min(current + maximumChange, target);
        }
        return Math.max(current - maximumChange, target);
    }
}
