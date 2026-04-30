package com.happysg.radar.compat.cbcwpf;

import net.ato.shupapium.blockentities.ShupapiumACBreechBlockEntity;
import net.ato.shupapium.items.ShupapiumAmmoContainerItem;
import net.ato.shupapium.items.ShupapiumAmmoItem;
import net.ato.shupapium.utils.actypes.ShupapiumACProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.ItemCannonBehavior;
import rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

public final class CBCWPFShupapiumImpl {
    private CBCWPFShupapiumImpl() {}

    static AutocannonMaterial resolveMaterial(AbstractMountedCannonContraption cannon) {
        return ((IShupapiumACContraptionAccess) cannon).getShupapiumMaterial();
    }

    static ShupapiumACProfile resolveProfile(AbstractMountedCannonContraption cannon) {
        return ((IShupapiumACContraptionAccess) cannon).getProfile();
    }

    static ShupapiumACBreechBlockEntity findBreech(AbstractMountedCannonContraption cannon) {
        for (BlockEntity be : cannon.presentBlockEntities.values()) {
            if (be instanceof ShupapiumACBreechBlockEntity b) return b;
        }
        return null;
    }

    static ItemStack findLoadedRoundInBarrel(AbstractMountedCannonContraption cannon) {
        for (BlockEntity be : cannon.presentBlockEntities.values()) {
            if (be == null) continue;
            try {
                Object behavior = be.getClass().getMethod("cannonBehavior").invoke(be);
                if (behavior instanceof ItemCannonBehavior icb) {
                    ItemStack s = icb.getItem();
                    if (s != null && !s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
        }
        return ItemStack.EMPTY;
    }

    static ItemStack peekNextRound(ShupapiumACBreechBlockEntity breech) {
        if (breech == null) return ItemStack.EMPTY;

        if (!breech.getInputBuffer().isEmpty()) {
            ItemStack s = breech.getInputBuffer().peekFirst();
            return s != null ? s : ItemStack.EMPTY;
        }

        ItemStack mag = breech.getMagazine();
        if (mag == null || mag.isEmpty()) return ItemStack.EMPTY;

        ItemStack main = ShupapiumAmmoContainerItem.getMainAmmoStack(mag);
        return main != null ? main : ItemStack.EMPTY;
    }

    static ItemStack peekLoadedOrNext(AbstractMountedCannonContraption cannon, ShupapiumACBreechBlockEntity breech) {
        ItemStack loaded = findLoadedRoundInBarrel(cannon);
        return !loaded.isEmpty() ? loaded : peekNextRound(breech);
    }

    static float profileBaseSpeed(AbstractMountedCannonContraption cannon) {
        ShupapiumACProfile profile = resolveProfile(cannon);
        return profile != null ? profile.getProjectileBaseSpeed() : 0f;
    }

    static float muzzleSpeed(AbstractMountedCannonContraption cannon) {
        float base = profileBaseSpeed(cannon);
        if (base <= 0f) return 0f;

        AutocannonMaterial mat = resolveMaterial(cannon);
        if (mat == null) return base;

        var props = mat.properties();
        float speed = base;

        BlockPos pos = cannon.getStartPos().relative(cannon.initialOrientation());
        int barrelCount = 0;

        while (cannon.presentBlockEntities.get(pos) instanceof IAutocannonBlockEntity) {
            barrelCount++;
            if (barrelCount <= props.maxSpeedIncreases())
                speed += props.speedIncreasePerBarrel();
            if (barrelCount > props.maxBarrelLength())
                break;
            pos = pos.relative(cannon.initialOrientation());
        }

        return speed;
    }

    /** Shupapium uses ProjectileManager.track(projectile, level, seconds) */
    static int lifetimeTicks(AbstractMountedCannonContraption cannon) {
        AutocannonMaterial mat = resolveMaterial(cannon);
        if (mat == null) return 0;
        int seconds = mat.properties().projectileLifetime();
        return seconds > 0 ? seconds * 20 : 0;
    }

    public static BallisticPropertiesComponent ballistics(AbstractMountedCannonContraption cannon, net.minecraft.world.level.Level level) {
        final double FALLBACK_DRAG = 0.0;
        final boolean FALLBACK_QUAD = false;
        final double GRAV_ON = -0.025;

        ShupapiumACBreechBlockEntity breech = findBreech(cannon);
        if (breech == null)
            return new BallisticPropertiesComponent(GRAV_ON, FALLBACK_DRAG, FALLBACK_QUAD, 0, 0, 0, 0);

        ItemStack round = peekLoadedOrNext(cannon, breech);
        if (round.isEmpty())
            return new BallisticPropertiesComponent(GRAV_ON, FALLBACK_DRAG, FALLBACK_QUAD, 0, 0, 0, 0);

        if (!(round.getItem() instanceof ShupapiumAmmoItem ammo))
            return new BallisticPropertiesComponent(GRAV_ON, FALLBACK_DRAG, FALLBACK_QUAD, 0, 0, 0, 0);

        double gravity = ammo.projectileAffectedByWorldsGravity() ? GRAV_ON : 0.0;

        var main = ammo.getMainProperties(round);
        BallisticPropertiesComponent bp = (main != null ? main.ballistics() : null);

        if (bp == null)
            return new BallisticPropertiesComponent(gravity, FALLBACK_DRAG, FALLBACK_QUAD, 0, 0, 0, 0);

        return new BallisticPropertiesComponent(
                gravity,
                bp.drag(),
                bp.isQuadraticDrag(),
                0, 0, 0, 0
        );
    }
}