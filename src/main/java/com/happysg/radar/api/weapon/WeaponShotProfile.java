package com.happysg.radar.api.weapon;

import com.happysg.radar.targeting.ProjectileModel;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

public record WeaponShotProfile(
        AimMode aimMode,
        @Nullable ProjectileModel projectileModel,
        Vec3 muzzlePosition,
        Vec3 inheritedVelocity,
        int maxFlightTicks,
        String fingerprint,
        double minimumFiringToleranceDegrees,
        boolean canTriggerFire,
        WeaponFirePreparation firePreparation,
        String diagnosticReason
) {
    public WeaponShotProfile {
        if (aimMode == null || muzzlePosition == null || inheritedVelocity == null
                || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Weapon shot profile fields must be non-null");
        }
        if (aimMode == AimMode.BALLISTIC && projectileModel == null) {
            throw new IllegalArgumentException("Ballistic weapon profiles require a projectile model");
        }
        maxFlightTicks = Math.max(0, maxFlightTicks);
        minimumFiringToleranceDegrees = Double.isFinite(minimumFiringToleranceDegrees)
                ? Math.max(0.0, Math.min(180.0, minimumFiringToleranceDegrees))
                : 0.0;
        firePreparation = firePreparation == null ? WeaponFirePreparation.READY : firePreparation;
        diagnosticReason = diagnosticReason == null ? "adapter" : diagnosticReason;
    }

    public static WeaponShotProfile ballistic(
            ProjectileModel model,
            Vec3 muzzlePosition,
            Vec3 inheritedVelocity,
            int maxFlightTicks,
            String fingerprint,
            boolean canTriggerFire,
            WeaponFirePreparation firePreparation,
            String diagnosticReason
    ) {
        return new WeaponShotProfile(
                AimMode.BALLISTIC, model, muzzlePosition, inheritedVelocity,
                maxFlightTicks, fingerprint, 0.0, canTriggerFire,
                firePreparation, diagnosticReason);
    }

    public static WeaponShotProfile direct(
            Vec3 muzzlePosition,
            Vec3 inheritedVelocity,
            String fingerprint,
            double minimumFiringToleranceDegrees,
            boolean canTriggerFire,
            WeaponFirePreparation firePreparation,
            String diagnosticReason
    ) {
        return new WeaponShotProfile(
                AimMode.DIRECT, null, muzzlePosition, inheritedVelocity,
                0, fingerprint, minimumFiringToleranceDegrees, canTriggerFire,
                firePreparation, diagnosticReason);
    }

    public static WeaponShotProfile disabled(
            Vec3 muzzlePosition,
            Vec3 inheritedVelocity,
            String fingerprint,
            String diagnosticReason
    ) {
        return new WeaponShotProfile(
                AimMode.DISABLED, null, muzzlePosition, inheritedVelocity,
                0, fingerprint, 0.0, false,
                WeaponFirePreparation.READY, diagnosticReason);
    }

    public enum AimMode {
        BALLISTIC,
        DIRECT,
        DISABLED
    }
}
