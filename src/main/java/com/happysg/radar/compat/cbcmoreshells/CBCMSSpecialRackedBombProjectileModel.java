package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.LoadedFluidDrag;
import com.happysg.radar.targeting.ProjectileStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public record CBCMSSpecialRackedBombProjectileModel(
        double muzzleSpeed,
        double gravity,
        double drag,
        boolean quadraticDrag,
        double dragDensity,
        boolean depthCharge,
        double deflection,
        int maxFlightTicks
) implements CBCMSProjectileModel {
    public CBCMSSpecialRackedBombProjectileModel {
        maxFlightTicks = Math.max(1, maxFlightTicks);
    }

    @Override
    public void step(
            int tick,
            double px, double py, double pz,
            double vx, double vy, double vz,
            Level level,
            ProjectileStep output
    ) {
        double density = dragDensity;

        if (level != null) {
            BlockPos fluidPos = BlockPos.containing(px, py, pz);

            if (LoadedFluidDrag.isImmersed(level, fluidPos)) {
                if (depthCharge) {
                    density += 32.0;
                } else {
                    density += 0.05 * LoadedFluidDrag.sample(level, fluidPos);
                }
            }
        }

        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double dragForce = CBCMSFlightMath.cappedCBCDrag(drag, density, quadraticDrag, speed);
        double damping = speed > 1.0E-8 ? dragForce / speed : 0.0;

        CBCMSFlightMath.integrate(
                px, py, pz,
                vx, vy, vz,
                -vx * damping,
                gravity - vy * damping,
                -vz * damping,
                output
        );

        if (depthCharge || level == null || deflection <= 0.01) {
            return;
        }

        Vec3 nextVelocity = new Vec3(output.velocityX, output.velocityY, output.velocityZ);
        double impactSpeed = nextVelocity.length();

        if (!Double.isFinite(impactSpeed) || impactSpeed <= muzzleSpeed + 0.8) {
            return;
        }

        Vec3 from = new Vec3(px, py, pz);
        Vec3 to = new Vec3(output.positionX, output.positionY, output.positionZ);

        if (from.distanceToSqr(to) < 1.0E-10) {
            return;
        }

        BlockHitResult hit = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                CollisionContext.empty()
        ));

        if (hit.getType() != HitResult.Type.BLOCK || !LoadedFluidDrag.isImmersed(level, hit.getBlockPos())) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        double incidence = Math.max(0.0, nextVelocity.normalize().dot(normal.reverse()));

        if (incidence >= deflection) {
            return;
        }

        Vec3 reflected = nextVelocity.subtract(normal.scale(1.8 * nextVelocity.dot(normal)));
        Vec3 impact = hit.getLocation();

        output.set(
                impact.x,
                impact.y,
                impact.z,
                reflected.x,
                reflected.y,
                reflected.z
        );
    }
}