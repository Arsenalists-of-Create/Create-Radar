package com.happysg.radar.compat.cbc;

import com.happysg.radar.api.weapon.WeaponFirePreparation;
import com.happysg.radar.api.weapon.WeaponShotAdapterRegistry;
import com.happysg.radar.api.weapon.WeaponShotContext;
import com.happysg.radar.api.weapon.WeaponShotProfile;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileModelBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;

public final class CbcShotProfileRegistration {
    private static boolean registered;

    private CbcShotProfileRegistration() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        if (Mods.CBC_AT.isLoaded()) {
            WeaponShotAdapterRegistry.register("create_radar:cbcat_preview", CBCAT::resolve);
        }
        if (Mods.CBCMORESHELLS.isLoaded()) {
            WeaponShotAdapterRegistry.register("create_radar:cbcms_preview", CBCMS::resolve);
        }
        if (Mods.CBCMODERNWARFARE.isLoaded()) {
            WeaponShotAdapterRegistry.register("create_radar:cbcmw_cannons_preview", CBCMW::resolve);
            com.happysg.radar.compat.cbcmw.CBCMWCannonCompat.registerLauncherShotAdapter();
        }
        registered = true;
    }

    @Nullable
    private static Mounted resolveMounted(WeaponShotContext context) {
        ServerLevel level = context.level();
        if (!level.getServer().isSameThread()) {
            return null;
        }
        CannonMountContext mount = context.weapon() instanceof CbcRadarWeaponAdapter adapter
                ? adapter.legacyCbcContext()
                : CannonMountContext.resolveEndpoint(level, context.mountPos());
        if (mount == null || !mount.isCurrent()) {
            return null;
        }
        PitchOrientedContraptionEntity entity = mount.getContraption();
        if (entity == null || !entity.isAlive()
                || !(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return null;
        }
        BlockPos start = cannon.getStartPos();
        if (start == null) {
            return null;
        }
        Vec3 localAnchor = CBCMuzzleUtil.getCBCSpawnAnchorWorld(entity);
        Vec3 worldAnchor = Mods.VALKYRIENSKIES.isLoaded()
                && VS2Utils.isBlockInShipyard(level, mount.getBlockPos())
                ? VS2Utils.getWorldVec(level, localAnchor)
                : localAnchor;
        return new Mounted(level, cannon, worldAnchor);
    }

    private static WeaponShotProfile ballisticPreview(Mounted mounted, ProjectileModel model,
                                                     int flightTicks, String fingerprint, String reason) {
        if (model == null || !Double.isFinite(model.muzzleSpeed()) || model.muzzleSpeed() <= 0.0
                || fingerprint == null || fingerprint.isBlank()) {
            return unavailable(mounted, reason);
        }
        return WeaponShotProfile.ballistic(ProjectileModelBridge.toApi(model),
                mounted.anchor(), Vec3.ZERO, Math.max(1, flightTicks), fingerprint,
                false, WeaponFirePreparation.READY, reason + "_preview_only");
    }

    private static WeaponShotProfile unavailable(Mounted mounted, String reason) {
        return WeaponShotProfile.disabled(mounted.anchor(), Vec3.ZERO,
                "create_radar:" + reason + ":unavailable", reason + "_unresolved");
    }

    private record Mounted(ServerLevel level, AbstractMountedCannonContraption cannon, Vec3 anchor) {}

    private static final class CBCAT {
        @Nullable
        static WeaponShotProfile resolve(WeaponShotContext context) {
            Mounted mounted = resolveMounted(context);
            if (mounted == null || !com.happysg.radar.compat.cbc_at.CBCATCannonCompat.isCBCATCannon(mounted.cannon())) {
                return null;
            }
            var shot = com.happysg.radar.compat.cbc_at.CBCATCannonCompat.resolveShotState(mounted.cannon(), mounted.level());
            return shot == null ? unavailable(mounted, "cbcat") : ballisticPreview(mounted,
                    shot.projectileModel(), shot.maxFlightTicks(), shot.fingerprint(), shot.reason());
        }
    }

    private static final class CBCMS {
        @Nullable
        static WeaponShotProfile resolve(WeaponShotContext context) {
            Mounted mounted = resolveMounted(context);
            if (mounted == null || !(com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat.isCBCMSMountedWeapon(mounted.cannon())
                    || isBigCannonTorpedo(mounted.cannon()))) {
                return null;
            }
            var shot = com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat.resolveShotState(mounted.cannon(), mounted.level());
            return shot == null ? unavailable(mounted, "cbcms") : ballisticPreview(mounted,
                    shot.projectileModel(), shot.lifetimeCapTicks(), shot.fingerprint(), shot.diagnosticReason());
        }

        private static boolean isBigCannonTorpedo(AbstractMountedCannonContraption cannon) {
            if (!(cannon instanceof rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption)
                    || cannon.presentBlockEntities == null) {
                return false;
            }
            for (var blockEntity : cannon.presentBlockEntities.values()) {
                if (blockEntity instanceof rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity big) {
                    var info = big.cannonBehavior().block();
                    if (info != null && info.state().getBlock()
                            instanceof com.cainiao1053.cbcmoreshells.munitions.big_cannon.TorpedoProjectileBlock<?>) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class CBCMW {
        @Nullable
        static WeaponShotProfile resolve(WeaponShotContext context) {
            Mounted mounted = resolveMounted(context);
            if (mounted == null || !com.happysg.radar.compat.cbcmw.CBCMWCannonCompat.isCBCMWCannon(mounted.cannon())) {
                return null;
            }
            var shot = com.happysg.radar.compat.cbcmw.CBCMWCannonCompat.resolveShotState(mounted.cannon(), mounted.level());
            if (shot == null || shot.ballistics() == null) {
                return unavailable(mounted, "cbcmw");
            }
            var dimension = rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler
                    .getProperties(mounted.level());
            var model = ProjectileModel.cbc(shot.speed(),
                    shot.ballistics().gravity() * dimension.gravityMultiplier(),
                    shot.ballistics().drag(), dimension.dragMultiplier(),
                    shot.ballistics().isQuadraticDrag());
            String fingerprint = "cbcmw:" + shot.ammunition() + ":" + shot.speed()
                    + ":" + shot.ballistics() + ":" + shot.lifetimeTicks();
            return ballisticPreview(mounted, model, shot.lifetimeTicks() > 0 ? shot.lifetimeTicks() : 200,
                    fingerprint, "cbcmw_cannon");
        }
    }
}
