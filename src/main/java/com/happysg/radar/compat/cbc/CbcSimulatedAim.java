package com.happysg.radar.compat.cbc;

import com.happysg.radar.api.weapon.WeaponShotAdapterRegistry;
import com.happysg.radar.api.weapon.WeaponShotContext;
import com.happysg.radar.api.weapon.WeaponShotProfile;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileModelBridge;
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import javax.annotation.Nullable;

public final class CbcSimulatedAim {
    private static final long REFRESH_TICKS = 3;
    private static final double TARGET_MOVE_SQR = 0.09;
    private static final double VELOCITY_CHANGE_SQR = 0.0004;

    private final TargetingComputer computer = TargetingComputer.createDefault();
    private AbstractMountedCannonContraption previousCannon;
    private Vec3 previousTarget;
    private Vec3 previousVelocity;
    private Vec3 previousShooterVelocity;
    private long previousTick = Long.MIN_VALUE;
    private TargetingResult previousResult;

    public static boolean supports(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedBigCannonContraption || cannon instanceof MountedAutocannonContraption;
    }

    public void reset() {
        previousCannon = null;
        previousTarget = null;
        previousVelocity = null;
        previousShooterVelocity = null;
        previousTick = Long.MIN_VALUE;
        previousResult = null;
    }

    public TargetingResult solve(ServerLevel level, CannonMountBlockEntity mount,
                                AbstractMountedCannonContraption cannon,
                                Vec3 target, Vec3 targetVelocity, Vec3 targetAcceleration,
                                Vec3 shooterVelocity, @Nullable AABB targetBounds,
                                @Nullable AutoPitchControllerBlockEntity pitchController) {
        if (level == null || mount == null || cannon == null || target == null
                || !supports(cannon) || mount.getContraption() == null
                || mount.getContraption().getContraption() != cannon) {
            reset();
            return TargetingResult.noShot("missing mounted cannon");
        }

        Vec3 velocity = targetVelocity == null ? Vec3.ZERO : targetVelocity;
        Vec3 acceleration = targetAcceleration == null ? Vec3.ZERO : targetAcceleration;
        Vec3 inherited = shooterVelocity == null ? Vec3.ZERO : shooterVelocity;
        long now = level.getGameTime();
        if (previousResult != null && previousCannon == cannon
                && now >= previousTick && now - previousTick < REFRESH_TICKS
                && previousTarget.distanceToSqr(target) <= TARGET_MOVE_SQR
                && previousVelocity.distanceToSqr(velocity) <= VELOCITY_CHANGE_SQR
                && previousShooterVelocity.distanceToSqr(inherited) <= VELOCITY_CHANGE_SQR) {
            return previousResult;
        }

        TargetingResult lastResult = previousResult;
        previousCannon = cannon;
        previousTarget = target;
        previousVelocity = velocity;
        previousShooterVelocity = inherited;
        previousTick = now;
        previousResult = TargetingResult.noShot("unresolved ballistics");

        PitchOrientedContraptionEntity entity = mount.getContraption();
        Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(entity);
        Vec3 pivot = entity.toGlobalVector(Vec3.atCenterOf(net.minecraft.core.BlockPos.ZERO), 0);
        Vec3 forward = CBCMuzzleUtil.getForwardWorld(entity);
        if (!finite(muzzle) || !finite(pivot) || !finite(forward) || forward.lengthSqr() < 1.0e-12) {
            return previousResult;
        }

        double muzzleOffset = Math.max(0.0, muzzle.subtract(pivot).dot(forward));
        ProjectileModel model;
        int flightTicks;
        WeaponShotProfile profile = cannon instanceof MountedBigCannonContraption
                ? WeaponShotAdapterRegistry.resolve(new WeaponShotContext(level, mount.getBlockPos(), null, null))
                : null;
        if (profile != null) {
            if (profile.aimMode() != WeaponShotProfile.AimMode.BALLISTIC || profile.projectileModel() == null || profile.maxFlightTicks() <= 0) {
                return previousResult;
            }
            model = ProjectileModelBridge.toInternal(profile.projectileModel());
            flightTicks = profile.maxFlightTicks();
        } else {
            float speed = CannonUtil.getInitialVelocity(cannon, level);
            if (!Float.isFinite(speed) || speed <= 0.0f) {
                return previousResult;
            }

            BallisticPropertiesComponent ballistics = CannonUtil.getBallistics(cannon, level);
            if (ballistics == null) {
                return previousResult;
            }
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            double gravity = ballistics.gravity() * dimension.gravityMultiplier();
            double drag = ballistics.drag();
            double dragDensity = dimension.dragMultiplier();
            if (!Double.isFinite(gravity) || !Double.isFinite(drag) || !Double.isFinite(dragDensity)) {
                return previousResult;
            }

            if (cannon instanceof MountedAutocannonContraption) {
                flightTicks = Math.max(1, CannonUtil.getAutocannonLifetimeTicks(cannon));
            } else {
                double distance = muzzle.distanceTo(target);
                flightTicks = Math.max(160, Math.min(2400, (int) Math.ceil(distance / speed * 3.0 + 120.0)));
            }
            model = ProjectileModel.cbc(speed, gravity, drag, dragDensity, ballistics.isQuadraticDrag());
        }
        if (!Double.isFinite(model.muzzleSpeed()) || model.muzzleSpeed() <= 0.0 || !Double.isFinite(model.gravity()) || !Double.isFinite(model.drag()) || !Double.isFinite(model.dragDensity())) {
            return previousResult;
        }

        double minPitch = pitchController == null ? -89.0 : pitchController.getMinAngleDeg();
        double maxPitch = pitchController == null ? 89.0 : pitchController.getMaxAngleDeg();
        CannonMountContext mountContext = CannonMountContext.of(mount);
        CannonMountContext.PitchLimits mountLimits = mountContext == null ? null : mountContext.pitchLimits();
        if (mountLimits != null) {
            minPitch = Math.max(minPitch, mountLimits.minDegrees());
            maxPitch = Math.min(maxPitch, mountLimits.maxDegrees());
        }

        TargetingSnapshot.Builder builder = TargetingSnapshot.builder(level)
                .muzzlePosition(muzzle)
                .launchPivotPosition(muzzleOffset > 0.0 ? pivot : null)
                .muzzleForwardOffset(muzzleOffset)
                .inheritedVelocity(inherited)
                .targetPosition(target)
                .targetVelocity(velocity)
                .targetAcceleration(acceleration)
                .targetAabb(targetBounds)
                .projectileSpeed(model.muzzleSpeed())
                .gravity(model.gravity())
                .drag(model.drag())
                .dragDensity(model.dragDensity())
                .quadraticDrag(model.quadraticDrag())
                .cbcPhysics(model.cbcPhysics())
                .maxFlightTicks(flightTicks)
                .pitchConstraint(PitchConstraint.world(minPitch, maxPitch))
                .preferHighArc(pitchController != null && pitchController.isArtillery());

        if (lastResult != null && lastResult.hasShot()) {
            builder.preferredYawDeg(lastResult.desiredYawDeg());
            builder.preferredPitchDeg(lastResult.desiredPitchDeg());
        }

        try {
            previousResult = computer.solve(builder.build(), model);
        } catch (RuntimeException exception) {
            previousResult = TargetingResult.noShot("simulated targeting unavailable");
        }
        return previousResult;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
