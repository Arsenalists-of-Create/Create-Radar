package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.LoadedFluidDrag;
import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record CBCMSRackedTorpedoProjectileModel(
        double muzzleSpeed,
        double gravity,
        double drag,
        double buoyancyFactor,
        double steadyStateVelocity,
        double waterDamp,
        int maxFlightTicks
) implements CBCMSProjectileModel {
    public CBCMSRackedTorpedoProjectileModel {
        maxFlightTicks = Math.max(1, maxFlightTicks);
    }

    @Override
    public boolean quadraticDrag() {
        return false;
    }

    @Override
    public void step(
            int tick,
            double px, double py, double pz,
            double vx, double vy, double vz,
            Level level,
            ProjectileStep output
    ) {
        boolean immersed = LoadedFluidDrag.isImmersed(level, BlockPos.containing(px, py, pz));
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);

        double ux = speed > 1.0E-8 ? vx / speed : 0.0;
        double uy = speed > 1.0E-8 ? vy / speed : 0.0;
        double uz = speed > 1.0E-8 ? vz / speed : 0.0;

        double axialForce;
        double verticalForce;

        if (immersed) {
            axialForce = -0.07 * (speed - steadyStateVelocity);
            verticalForce = gravity * -buoyancyFactor - Math.max(0.0, waterDamp) * vy;
        } else {
            axialForce = -drag * speed;
            verticalForce = gravity;
        }

        CBCMSFlightMath.integrate(
                px, py, pz,
                vx, vy, vz,
                ux * axialForce,
                uy * axialForce + verticalForce,
                uz * axialForce,
                output
        );
    }
}