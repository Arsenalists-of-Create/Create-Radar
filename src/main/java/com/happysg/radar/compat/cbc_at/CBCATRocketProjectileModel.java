package com.happysg.radar.compat.cbc_at;

import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;

/**
 * Exact powered-flight model used by CBC:AT's small and medium rockets.
 */
public record CBCATRocketProjectileModel(
        double muzzleSpeed,
        double gravity,
        double drag,
        boolean quadraticDrag,
        double dragDensity,
        int fuelTicks,
        int maxFlightTicks,
        double thrustIncrease,
        double maxThrust
) implements ProjectileModel {

    public CBCATRocketProjectileModel {
        fuelTicks = Math.max(0, fuelTicks);
        maxFlightTicks = Math.max(1, maxFlightTicks);
        dragDensity = !Double.isFinite(dragDensity) || dragDensity < 0.0 ? 1.0 : dragDensity;
        thrustIncrease = Math.max(0.0, thrustIncrease);
        maxThrust = Math.max(0.0, maxThrust);
    }

    @Override
    public boolean cbcPhysics() {
        return true;
    }

    @Override
    public boolean usesCustomDynamics() {
        return true;
    }

    @Override
    public void step(
            int tick,
            double positionX,
            double positionY,
            double positionZ,
            double velocityX,
            double velocityY,
            double velocityZ,
            Level level,
            ProjectileStep output
    ) {
        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        double unitX = speed > 1.0E-8 ? velocityX / speed : 0.0;
        double unitY = speed > 1.0E-8 ? velocityY / speed : 0.0;
        double unitZ = speed > 1.0E-8 ? velocityZ / speed : 0.0;

        double density = dragDensity;
        if (level != null) {
            density += FluidDragHandler.getFluidDrag(level.getFluidState(BlockPos.containing(positionX, positionY, positionZ)));
        }

        double dragForce = 0.0;
        if (speed > 1.0E-8 && drag > 0.0 && density > 0.0) {
            dragForce = drag * density * speed;
            if (quadraticDrag) {
                dragForce *= speed;
            }
            dragForce = Math.min(dragForce, speed);
        }

        boolean powered = tick < fuelTicks;
        double thrust = powered ? Math.min((tick + 2.0) * thrustIncrease, maxThrust) : 0.0;
        double axialForce = thrust - dragForce;
        double accelerationX = unitX * axialForce;
        double accelerationY = unitY * axialForce + gravity * (powered ? 0.875 : 1.3);
        double accelerationZ = unitZ * axialForce;

        output.set(
                positionX + velocityX + accelerationX * 0.5,
                positionY + velocityY + accelerationY * 0.5,
                positionZ + velocityZ + accelerationZ * 0.5,
                velocityX + accelerationX,
                velocityY + accelerationY,
                velocityZ + accelerationZ
        );
    }

    @Override
    public double estimateFlightTicks(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0) {
            return 0.0;
        }

        double position = 0.0;
        double velocity = Math.max(1.0E-6, muzzleSpeed);
        for (int tick = 0; tick < maxFlightTicks; tick++) {
            double dragForce = drag * dragDensity * velocity;
            if (quadraticDrag) {
                dragForce *= velocity;
            }
            dragForce = Math.min(Math.max(0.0, dragForce), velocity);
            double thrust = tick < fuelTicks ? Math.min((tick + 2.0) * thrustIncrease, maxThrust) : 0.0;
            double acceleration = thrust - dragForce;
            double nextPosition = position + velocity + acceleration * 0.5;
            if (nextPosition >= distance) {
                double span = Math.max(1.0E-6, nextPosition - position);
                return tick + Math.max(0.0, Math.min(1.0, (distance - position) / span));
            }
            position = nextPosition;
            velocity = Math.max(0.0, velocity + acceleration);
        }
        return maxFlightTicks;
    }
}
