package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.LoadedFluidDrag;
import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record CBCMSRackedRocketProjectileModel(
        double muzzleSpeed,
        double gravity,
        double drag,
        int thrustTicks,
        double steadyStateVelocity,
        int maxFlightTicks
) implements CBCMSProjectileModel {

    public CBCMSRackedRocketProjectileModel {
        thrustTicks = Math.max(0, thrustTicks);
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
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);

        double ux = speed > 1.0E-8 ? vx / speed : 0.0;
        double uy = speed > 1.0E-8 ? vy / speed : 0.0;
        double uz = speed > 1.0E-8 ? vz / speed : 0.0;

        double dragForce = drag * speed;

        if (level != null) {
            double fluidDrag = LoadedFluidDrag.sample(level, BlockPos.containing(px, py, pz));
            dragForce += fluidDrag * drag * speed;
        }

        if (tick < thrustTicks) {
            dragForce += 0.12 * (speed - steadyStateVelocity);
        }

        double axialForce = -dragForce;

        CBCMSFlightMath.integrate(
                px, py, pz,
                vx, vy, vz,
                ux * axialForce,
                uy * axialForce + gravity,
                uz * axialForce,
                output
        );
    }
}