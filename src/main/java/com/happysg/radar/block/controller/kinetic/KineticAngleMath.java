package com.happysg.radar.block.controller.kinetic;

public final class KineticAngleMath {
    private KineticAngleMath() {
    }

    public static double wrap360(double degrees) {
        double wrapped = degrees % 360.0;
        return wrapped < 0.0 ? wrapped + 360.0 : wrapped;
    }

    public static double shortestDelta(double fromDegrees, double toDegrees) {
        double delta = wrap360(toDegrees) - wrap360(fromDegrees);
        if (delta >= 180.0) {
            delta -= 360.0;
        } else if (delta < -180.0) {
            delta += 360.0;
        }
        return delta;
    }

    public static boolean isInInclusiveWrappedInterval(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);
        if (Math.abs(shortestDelta(min, max)) <= 1.0e-9) {
            return true;
        }
        return min <= max ? angle >= min && angle <= max : angle >= min || angle <= max;
    }
}
