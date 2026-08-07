package com.happysg.radar.compat.cbcmw;

import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.ItemCannonBehavior;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.autocannon.ammo_container.AutocannonAmmoContainerItem;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.MediumcannonBlock;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.IMediumcannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.breech.MediumcannonBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.material.MediumcannonMaterial;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.RotarycannonBlock;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.IRotarycannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.breech.AbstractRotarycannonBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.material.RotarycannonMaterial;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonAmmoItem;

import javax.annotation.Nullable;

/**
 * CBC Modern Warfare cannon hooks kept behind the mod-loaded check in
 * {@link CannonUtil}. Keeping the third-party types in this class prevents
 * ordinary CBC targeting from eagerly linking CBCMW classes.
 */
public final class CBCMWCannonCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BallisticPropertiesComponent FALLBACK_BALLISTICS =
            new BallisticPropertiesComponent(-0.025, 0.01, false, 0, 0, 0, 0);

    private CBCMWCannonCompat() {
    }

    public record ShotState(
            float speed,
            BallisticPropertiesComponent ballistics,
            int lifetimeTicks,
            String ammunition
    ) {
    }

    private record BarrelWalk(int travelled, BlockPos exit) {
    }

    public static boolean isCBCMWCannon(AbstractMountedCannonContraption cannon) {
        return isMediumCannon(cannon) || isRotaryCannon(cannon);
    }

    public static boolean isMediumCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedMediumcannonContraption;
    }

    public static boolean isRotaryCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedRotarycannonContraption;
    }

    @Nullable
    public static ShotState resolveShotState(AbstractMountedCannonContraption cannon, Level level) {
        if (cannon == null || level == null) {
            return null;
        }

        try {
            if (isMediumCannon(cannon)) {
                return resolveMediumShot(cannon, level);
            }
            if (isRotaryCannon(cannon)) {
                return resolveRotaryShot(cannon, level);
            }
        } catch (RuntimeException | LinkageError error) {
            DiagnosticRecorder.warn("cbc_modern_warfare",
                    "resolve_shot_state", "compatibility_resolution_failed",
                    error, level, null, "cbcmodernwarfare",
                    "createbigcannons");
            LOGGER.warn("Could not resolve CBC Modern Warfare cannon shot state for {}",
                    cannon.getClass().getName(), error);
        }
        return null;
    }

    @Nullable
    private static ShotState resolveMediumShot(AbstractMountedCannonContraption cannon, Level level) {
        BlockPos start = cannon.getStartPos();
        Direction direction = cannon.initialOrientation();
        if (start == null || direction == null) {
            return null;
        }

        BlockEntity startBlockEntity = cannon.presentBlockEntities.get(start);
        if (!(startBlockEntity instanceof MediumcannonBreechBlockEntity breech)
                || !(startBlockEntity.getBlockState().getBlock() instanceof MediumcannonBlock block)) {
            return null;
        }

        MediumcannonMaterial material = block.getMediumcannonMaterial();
        ItemStack loadedStack = breech.getInputBuffer();
        AbstractCannonProjectile projectile = createMediumProjectile(loadedStack, level);
        if (material == null || projectile == null) {
            return null;
        }

        BarrelWalk walk = walkBarrels(cannon, start, direction, true, loadedStack);
        float speed = material.properties().baseSpeed()
                + Math.min(walk.travelled(), material.properties().maxSpeedIncreases())
                * material.properties().speedIncreasePerBarrel();
        BallisticPropertiesComponent ballistics = resolveBallistics(projectile);
        return new ShotState(speed, ballistics, 0, describeAmmo(loadedStack));
    }

    @Nullable
    private static ShotState resolveRotaryShot(AbstractMountedCannonContraption cannon, Level level) {
        BlockPos start = cannon.getStartPos();
        Direction direction = cannon.initialOrientation();
        if (start == null || direction == null) {
            return null;
        }

        BlockEntity startBlockEntity = cannon.presentBlockEntities.get(start);
        if (!(startBlockEntity instanceof AbstractRotarycannonBreechBlockEntity breech)
                || !(startBlockEntity.getBlockState().getBlock() instanceof RotarycannonBlock block)) {
            return null;
        }

        RotarycannonMaterial material = block.getRotarycannonMaterial();
        ItemStack loadedStack = resolveRotaryAmmo(breech);
        AbstractCannonProjectile projectile = createAutocannonProjectile(loadedStack, level);
        if (material == null || projectile == null) {
            return null;
        }

        BarrelWalk walk = walkBarrels(cannon, start, direction, false, loadedStack);
        float speed = material.properties().baseSpeed()
                + Math.min(walk.travelled(), material.properties().maxSpeedIncreases())
                * material.properties().speedIncreasePerBarrel();
        int lifetime = Math.max(1, material.properties().projectileLifetime());
        BallisticPropertiesComponent ballistics = resolveBallistics(projectile);
        return new ShotState(speed, ballistics, lifetime, describeAmmo(loadedStack));
    }

    public static BlockPos getMuzzleExitLocal(AbstractMountedCannonContraption cannon) {
        if (!isCBCMWCannon(cannon) || cannon.getStartPos() == null || cannon.initialOrientation() == null) {
            return null;
        }

        boolean medium = isMediumCannon(cannon);
        ItemStack loadedStack = resolveLoadedAmmo(cannon);
        return walkBarrels(
                cannon,
                cannon.getStartPos(),
                cannon.initialOrientation(),
                medium,
                loadedStack
        ).exit();
    }

    public static Vec3 getSpawnAnchorWorld(
            PitchOrientedContraptionEntity mounted,
            AbstractMountedCannonContraption cannon
    ) {
        BlockPos exit = getMuzzleExitLocal(cannon);
        Direction direction = cannon.initialOrientation();
        if (exit == null || direction == null) {
            return Vec3.ZERO;
        }

        Vec3 rawSpawn = mounted.toGlobalVector(Vec3.atCenterOf(exit.relative(direction)), 0);
        if (isRotaryCannon(cannon)) {
            Vec3 rawBore = mounted.toGlobalVector(Vec3.atCenterOf(exit), 0);
            Vec3 forward = rawSpawn.subtract(rawBore);
            return forward.lengthSqr() < 1.0E-8
                    ? rawSpawn
                    : rawSpawn.add(forward.normalize().scale(0.75));
        }

        Vec3 origin = mounted.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        Vec3 forward = rawSpawn.subtract(origin);
        return forward.lengthSqr() < 1.0E-8
                ? rawSpawn
                : rawSpawn.subtract(forward.normalize().scale(1.5));
    }

    private static BarrelWalk walkBarrels(
            AbstractMountedCannonContraption cannon,
            BlockPos start,
            Direction direction,
            boolean medium,
            @Nullable ItemStack loadedStack
    ) {
        int travelled = 0;
        BlockPos current = start.relative(direction);
        boolean validateLoadedRound = loadedStack != null && !loadedStack.isEmpty();
        while (true) {
            BlockEntity blockEntity = cannon.presentBlockEntities.get(current);
            ItemCannonBehavior behavior = getMatchingBehavior(blockEntity, medium);
            if (behavior == null || validateLoadedRound && !behavior.canLoadItem(loadedStack)) {
                break;
            }
            travelled++;
            current = current.relative(direction);
        }
        return new BarrelWalk(travelled, current.immutable());
    }

    @Nullable
    private static ItemCannonBehavior getMatchingBehavior(@Nullable BlockEntity blockEntity, boolean medium) {
        if (medium && blockEntity instanceof IMediumcannonBlockEntity mediumCannon) {
            return mediumCannon.cannonBehavior();
        }
        if (!medium && blockEntity instanceof IRotarycannonBlockEntity rotaryCannon) {
            return rotaryCannon.cannonBehavior();
        }
        return null;
    }

    private static ItemStack resolveLoadedAmmo(AbstractMountedCannonContraption cannon) {
        BlockPos start = cannon.getStartPos();
        BlockEntity startBlockEntity = start == null ? null : cannon.presentBlockEntities.get(start);
        if (startBlockEntity instanceof MediumcannonBreechBlockEntity breech) {
            return breech.getInputBuffer();
        }
        if (startBlockEntity instanceof AbstractRotarycannonBreechBlockEntity breech) {
            return resolveRotaryAmmo(breech);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack resolveRotaryAmmo(AbstractRotarycannonBreechBlockEntity breech) {
        ItemStack queued = breech.getInputBuffer().peek();
        if (queued != null && !queued.isEmpty()) {
            return queued;
        }

        ItemStack magazine = breech.getMagazine();
        return magazine.isEmpty()
                ? ItemStack.EMPTY
                : AutocannonAmmoContainerItem.pollItemFromContainer(magazine.copy());
    }

    @Nullable
    private static AbstractCannonProjectile createMediumProjectile(@Nullable ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof MediumcannonAmmoItem ammoItem)) {
            return null;
        }
        return ammoItem.getMediumcannonProjectile(stack, level);
    }

    @Nullable
    private static AbstractCannonProjectile createAutocannonProjectile(@Nullable ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AutocannonAmmoItem ammoItem)) {
            return null;
        }
        return ammoItem.getAutocannonProjectile(stack, level);
    }

    private static BallisticPropertiesComponent resolveBallistics(@Nullable AbstractCannonProjectile projectile) {
        BallisticPropertiesComponent ballistics = CannonUtil.getProjectileBallistics(projectile);
        return ballistics == null ? FALLBACK_BALLISTICS : ballistics;
    }

    private static String describeAmmo(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<unresolved>";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
