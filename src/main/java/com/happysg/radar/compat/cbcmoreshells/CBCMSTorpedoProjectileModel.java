package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Exact CBCMS air/water force law for torpedoes fired by standard big cannons. */
public record CBCMSTorpedoProjectileModel(
        double muzzleSpeed,
        double gravity,
        double drag,
        boolean quadraticDrag,
        double dragDensity,
        double buoyancyFactor,
        double steadyStateVelocity,
        int maxFlightTicks
) implements CBCMSProjectileModel {
    public CBCMSTorpedoProjectileModel {
        maxFlightTicks = Math.max(1, maxFlightTicks);
    }

    @Override
    public void step(int tick, double px, double py, double pz, double vx, double vy, double vz,
                     Level level, ProjectileStep output) {
        boolean immersed = level != null
                && !level.getFluidState(BlockPos.containing(px, py, pz)).isEmpty();
        stepForMedium(immersed, px, py, pz, vx, vy, vz, output);
    }

    public void stepForMedium(boolean immersed, double px, double py, double pz,
                              double vx, double vy, double vz, ProjectileStep output) {
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double ux = speed > 1.0E-8 ? vx / speed : 0.0;
        double uy = speed > 1.0E-8 ? vy / speed : 0.0;
        double uz = speed > 1.0E-8 ? vz / speed : 0.0;
        double axial;
        double vertical;
        if (immersed) {
            axial = -0.07 * (speed - steadyStateVelocity);
            vertical = gravity * -buoyancyFactor - Math.max(0.0, drag) * vy;
        } else {
            axial = -(drag / 10.0) * speed;
            vertical = gravity;
        }
        CBCMSFlightMath.integrate(px, py, pz, vx, vy, vz,
                ux * axial, uy * axial + vertical, uz * axial, output);
    }
}
