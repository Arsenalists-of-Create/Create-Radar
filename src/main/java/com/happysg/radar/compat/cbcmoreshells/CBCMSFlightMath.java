package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.ProjectileStep;

final class CBCMSFlightMath {
    private CBCMSFlightMath() {}

    static void integrate(
            double px, double py, double pz,
            double vx, double vy, double vz,
            double ax, double ay, double az,
            ProjectileStep output
    ) {
        output.set(
                px + vx + ax * 0.5,
                py + vy + ay * 0.5,
                pz + vz + az * 0.5,
                vx + ax,
                vy + ay,
                vz + az
        );
    }

    static double cappedCBCDrag(double drag, double density, boolean quadratic, double speed) {
        if (speed <= 1.0E-8 || drag <= 0.0 || density <= 0.0) {
            return 0.0;
        }
        double force = drag * density * speed;
        if (quadratic) {
            force *= speed;
        }
        return Math.min(force, speed);
    }
}
