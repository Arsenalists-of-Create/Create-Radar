
package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.world.level.Level;

public record CBCMSDualCannonProjectileModel(double muzzleSpeed, double gravity, double drag, boolean quadraticDrag, int maxFlightTicks) implements CBCMSProjectileModel {
    public CBCMSDualCannonProjectileModel {
        maxFlightTicks = Math.max(1, maxFlightTicks);
    }

    @Override
    public void step(int tick, double px, double py, double pz, double vx, double vy, double vz, Level level, ProjectileStep output) {
        double speedSquared = vx * vx + vy * vy + vz * vz;
        double dragScale = 0.0;
        if (drag > 0.0 && speedSquared >= 1.0e-8) {
            dragScale = quadraticDrag ? drag * Math.sqrt(speedSquared) : drag;
        }

        double ax = -vx * dragScale;
        double ay = gravity - vy * dragScale;
        double az = -vz * dragScale;

        CBCMSFlightMath.integrate(px, py, pz, vx, vy, vz, ax, ay, az, output);
    }
}
