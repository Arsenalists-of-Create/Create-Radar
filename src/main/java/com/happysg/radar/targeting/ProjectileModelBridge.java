package com.happysg.radar.targeting;

import com.happysg.radar.api.weapon.ballistics.RadarProjectileDynamics;
import com.happysg.radar.api.weapon.ballistics.RadarProjectileModel;
import com.happysg.radar.api.weapon.ballistics.RadarProjectileStep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class ProjectileModelBridge {
    private ProjectileModelBridge() {}

    public static ProjectileModel toInternal(RadarProjectileModel model) {
        Objects.requireNonNull(model, "model");

        if (model instanceof InternalView view) {
            return view.delegate;
        }

        return new ApiView(model);
    }

    public static RadarProjectileModel toApi(ProjectileModel model) {
        Objects.requireNonNull(model, "model");

        if (model instanceof ApiView view) {
            return view.delegate;
        }

        return new InternalView(model);
    }

    private static final class ApiView implements ProjectileModel {

        private final RadarProjectileModel delegate;

        private ApiView(RadarProjectileModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public double muzzleSpeed() {
            return delegate.muzzleSpeed();
        }

        @Override
        public double gravity() {
            return delegate.gravity();
        }

        @Override
        public double drag() {
            return delegate.drag();
        }

        @Override
        public boolean quadraticDrag() {
            return delegate.quadraticDrag();
        }

        @Override
        public boolean cbcPhysics() {
            return delegate.cbcPhysics();
        }

        @Override
        public double dragDensity() {
            return delegate.dragDensity();
        }

        @Override
        public boolean usesCustomDynamics() {
            return delegate.usesCustomDynamics();
        }

        @Override
        public ProjectileDynamics createDynamics(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity) {
            RadarProjectileDynamics dynamics = delegate.createDynamics(startPosition, aimDirection, inheritedVelocity);

            RadarProjectileStep apiStep = new RadarProjectileStep();

            return (
                    tick,
                    positionX,
                    positionY,
                    positionZ,
                    velocityX,
                    velocityY,
                    velocityZ,
                    level,
                    output
            ) -> {
                dynamics.step(
                        tick,
                        positionX,
                        positionY,
                        positionZ,
                        velocityX,
                        velocityY,
                        velocityZ,
                        level,
                        apiStep
                );

                output.set(
                        apiStep.positionX,
                        apiStep.positionY,
                        apiStep.positionZ,
                        apiStep.velocityX,
                        apiStep.velocityY,
                        apiStep.velocityZ
                );
            };
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
            RadarProjectileStep apiStep = new RadarProjectileStep();

            delegate.step(
                    tick,
                    positionX,
                    positionY,
                    positionZ,
                    velocityX,
                    velocityY,
                    velocityZ,
                    level,
                    apiStep
            );

            output.set(
                    apiStep.positionX,
                    apiStep.positionY,
                    apiStep.positionZ,
                    apiStep.velocityX,
                    apiStep.velocityY,
                    apiStep.velocityZ
            );
        }

        @Override
        public double estimateFlightTicks(double distance) {
            return delegate.estimateFlightTicks(distance);
        }

        @Override
        public Vec3 velocityAfterTick(Vec3 velocity) {
            return delegate.velocityAfterTick(velocity);
        }
    }

    private static final class InternalView implements RadarProjectileModel {

        private final ProjectileModel delegate;

        private InternalView(ProjectileModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public double muzzleSpeed() {
            return delegate.muzzleSpeed();
        }

        @Override
        public double gravity() {
            return delegate.gravity();
        }

        @Override
        public double drag() {
            return delegate.drag();
        }

        @Override
        public boolean quadraticDrag() {
            return delegate.quadraticDrag();
        }

        @Override
        public boolean cbcPhysics() {
            return delegate.cbcPhysics();
        }

        @Override
        public double dragDensity() {
            return delegate.dragDensity();
        }

        @Override
        public boolean usesCustomDynamics() {
            return delegate.usesCustomDynamics();
        }

        @Override
        public RadarProjectileDynamics createDynamics(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity) {
            ProjectileDynamics dynamics = delegate.createDynamics(startPosition, aimDirection, inheritedVelocity);

            ProjectileStep internalStep = new ProjectileStep();

            return (
                    tick,
                    positionX,
                    positionY,
                    positionZ,
                    velocityX,
                    velocityY,
                    velocityZ,
                    level,
                    output
            ) -> {
                dynamics.step(
                        tick,
                        positionX,
                        positionY,
                        positionZ,
                        velocityX,
                        velocityY,
                        velocityZ,
                        level,
                        internalStep
                );

                output.set(
                        internalStep.positionX,
                        internalStep.positionY,
                        internalStep.positionZ,
                        internalStep.velocityX,
                        internalStep.velocityY,
                        internalStep.velocityZ
                );
            };
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
                RadarProjectileStep output
        ) {
            ProjectileStep internalStep = new ProjectileStep();

            delegate.step(
                    tick,
                    positionX,
                    positionY,
                    positionZ,
                    velocityX,
                    velocityY,
                    velocityZ,
                    level,
                    internalStep
            );

            output.set(
                    internalStep.positionX,
                    internalStep.positionY,
                    internalStep.positionZ,
                    internalStep.velocityX,
                    internalStep.velocityY,
                    internalStep.velocityZ
            );
        }

        @Override
        public double estimateFlightTicks(double distance) {
            return delegate.estimateFlightTicks(distance);
        }

        @Override
        public Vec3 velocityAfterTick(Vec3 velocity) {
            return delegate.velocityAfterTick(velocity);
        }
    }
}