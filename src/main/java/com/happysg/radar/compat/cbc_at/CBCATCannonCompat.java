package com.happysg.radar.compat.cbc_at;

import com.dsvv.cbcat.cannon.heavy_autocannon.HeavyAutocannonBlock;
import com.dsvv.cbcat.cannon.heavy_autocannon.IHeavyAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption;
import com.dsvv.cbcat.cannon.heavy_autocannon.munitions.AbstractHeavyAutocannonProjectileItem;
import com.dsvv.cbcat.cannon.heavy_autocannon.munitions.HeavyAutocannonAmmoItem;
import com.dsvv.cbcat.cannon.medium_rocketpod.IMediumRocketPodBlockEntity;
import com.dsvv.cbcat.cannon.medium_rocketpod.MediumRocketPodBlock;
import com.dsvv.cbcat.cannon.medium_rocketpod.contraption.MountedMediumRocketRailContraption;
import com.dsvv.cbcat.cannon.medium_rocketpod.munitions.AbstractMediumRocketItem;
import com.dsvv.cbcat.cannon.medium_rocketpod.munitions.MediumRocketItem;
import com.dsvv.cbcat.cannon.rocketpod.IRocketPodBlockEntity;
import com.dsvv.cbcat.cannon.rocketpod.RocketPodBlock;
import com.dsvv.cbcat.cannon.rocketpod.contraption.MountedRocketPodContraption;
import com.dsvv.cbcat.cannon.rocketpod.munitions.AbstractRocketItem;
import com.dsvv.cbcat.cannon.rocketpod.munitions.RocketItem;
import com.dsvv.cbcat.cannon.twin_autocannon.ITwinAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.twin_autocannon.TwinAutocannonBlock;
import com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption;
import com.dsvv.cbcat.registry.DataComponentRegistry;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.happysg.radar.mixin.CBCATQuickFireBreechAccessor;
import com.happysg.radar.targeting.ProjectileModel;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterialProperties;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.remix.GetItemStorage;

/**
 * CBC:AT 0.1.4c weapon-family and next-shot resolver.
 */
public final class CBCATCannonCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean RESOLUTION_WARNING_LOGGED = new AtomicBoolean();
    public static final double SMALL_ROCKET_THRUST_INCREASE = 0.025;
    public static final double SMALL_ROCKET_MAX_THRUST = 2.5;
    public static final double MEDIUM_ROCKET_THRUST_INCREASE = 0.05;
    public static final double MEDIUM_ROCKET_MAX_THRUST = 3.75;

    private CBCATCannonCompat() {
    }

    public enum CannonKind {
        HEAVY_AUTOCANNON(false),
        TWIN_AUTOCANNON(false),
        ROCKET_POD(true),
        MEDIUM_ROCKET_RAIL(true);

        private final boolean poweredRocket;

        CannonKind(boolean poweredRocket) {
            this.poweredRocket = poweredRocket;
        }

        public boolean poweredRocket() {
            return poweredRocket;
        }
    }

    public record ShotState(
            CannonKind kind,
            float speed,
            BallisticPropertiesComponent ballistics,
            ProjectileModel projectileModel,
            int fuelTicks,
            int maxFlightTicks,
            String ammunition,
            String fingerprint,
            String reason
    ) {
        public boolean poweredRocket() {
            return kind.poweredRocket();
        }

        @Nullable
        public CBCATRocketProjectileModel rocketModel() {
            return projectileModel instanceof CBCATRocketProjectileModel rocket ? rocket : null;
        }
    }

    public static boolean isCBCATCannon(@Nullable AbstractMountedCannonContraption cannon) {
        return kindOf(cannon) != null;
    }

    public static boolean isHeavyAutocannon(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedHeavyAutocannonContraption;
    }

    public static boolean isTwinAutocannon(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedTwinAutocannonContraption;
    }

    public static boolean isRocketPod(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedRocketPodContraption;
    }

    public static boolean isMediumRocketRail(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedMediumRocketRailContraption;
    }

    public static boolean isPoweredRocket(@Nullable AbstractMountedCannonContraption cannon) {
        CannonKind kind = kindOf(cannon);
        return kind != null && kind.poweredRocket();
    }

    @Nullable
    public static CannonKind kindOf(@Nullable AbstractMountedCannonContraption cannon) {
        if (isHeavyAutocannon(cannon)) {
            return CannonKind.HEAVY_AUTOCANNON;
        }
        if (isTwinAutocannon(cannon)) {
            return CannonKind.TWIN_AUTOCANNON;
        }
        if (isRocketPod(cannon)) {
            return CannonKind.ROCKET_POD;
        }
        if (isMediumRocketRail(cannon)) {
            return CannonKind.MEDIUM_ROCKET_RAIL;
        }
        return null;
    }

    public static boolean isCBCATBarrel(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IHeavyAutocannonBlockEntity
                || blockEntity instanceof ITwinAutocannonBlockEntity
                || blockEntity instanceof IRocketPodBlockEntity
                || blockEntity instanceof IMediumRocketPodBlockEntity;
    }

    @Nullable
    public static AutocannonMaterial getAutocannonMaterial(@Nullable AbstractMountedCannonContraption cannon) {
        if (cannon == null || cannon.presentBlockEntities == null) {
            return null;
        }
        for (BlockEntity blockEntity : cannon.presentBlockEntities.values()) {
            Block block = blockEntity.getBlockState().getBlock();
            if (block instanceof TwinAutocannonBlock twin) {
                return twin.getAutocannonMaterial();
            }
            if (block instanceof HeavyAutocannonBlock heavy) {
                return heavy.getAutocannonMaterial();
            }
            if (block instanceof RocketPodBlock rocketPod) {
                return rocketPod.getAutocannonMaterial();
            }
            if (block instanceof MediumRocketPodBlock mediumRail) {
                return mediumRail.getAutocannonMaterial();
            }
        }
        return null;
    }

    @Nullable
    public static AbstractCannonProjectile createProjectile(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof HeavyAutocannonAmmoItem heavyAmmo) {
            return heavyAmmo.getAutocannonProjectile(stack, level);
        }
        if (item instanceof AbstractHeavyAutocannonProjectileItem heavyProjectile) {
            return heavyProjectile.getAutocannonProjectile(stack, level);
        }
        if (item instanceof AbstractRocketItem rocket) {
            return rocket.getAutocannonProjectile(stack, level);
        }
        if (item instanceof RocketItem rocket) {
            return rocket.getAutocannonProjectile(stack, level);
        }
        if (item instanceof AbstractMediumRocketItem mediumRocket) {
            return mediumRocket.getAutocannonProjectile(stack, level);
        }
        if (item instanceof MediumRocketItem mediumRocket) {
            return mediumRocket.getAutocannonProjectile(stack, level);
        }
        if (item instanceof AutocannonAmmoItem autocannonAmmo) {
            return autocannonAmmo.getAutocannonProjectile(stack, level);
        }
        return null;
    }

    @Nullable
    public static ShotState resolveShotState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        CannonKind kind = kindOf(cannon);
        if (kind == null || level == null) {
            return null;
        }

        try {
            AutocannonMaterial material = getAutocannonMaterial(cannon);
            if (material == null) {
                return null;
            }
            ItemStack ammunition = findLoadedAmmunition(cannon, kind, level);
            if (ammunition == null || ammunition.isEmpty()) {
                return null;
            }
            AbstractCannonProjectile projectile = createProjectile(ammunition, level);
            BallisticPropertiesComponent ballistics = CannonUtil.getProjectileBallistics(projectile);
            if (projectile == null || ballistics == null) {
                return null;
            }

            AutocannonMaterialProperties properties = material.properties();
            int barrelCount = countSpeedIncreasingBarrels(cannon, kind, properties.maxBarrelLength());
            boolean strong = kind == CannonKind.HEAVY_AUTOCANNON
                    && (ammunition.getItem() instanceof HeavyAutocannonAmmoItem heavyAmmo && heavyAmmo.isStrong(ammunition)
                    || Boolean.TRUE.equals(ammunition.get(DataComponentRegistry.HA_STRONG_ROUND)));
            float speed = CBCATLaunchMath.initialSpeed(
                    properties.baseSpeed(),
                    properties.speedIncreasePerBarrel(),
                    barrelCount,
                    properties.maxSpeedIncreases(),
                    kind.poweredRocket(),
                    strong
            );

            int fuelTicks = kind.poweredRocket() ? readFuelTicks(ammunition) : 0;
            int materialLifetime = Math.max(1, properties.projectileLifetime());
            int maxFlightTicks = CBCATLaunchMath.flightLifetime(
                    materialLifetime,
                    fuelTicks,
                    kind.poweredRocket(),
                    kind == CannonKind.HEAVY_AUTOCANNON
            );

            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            double gravity = ballistics.gravity() * dimension.gravityMultiplier();
            double dragDensity = dimension.dragMultiplier();
            ProjectileModel model;
            if (kind == CannonKind.ROCKET_POD) {
                model = new CBCATRocketProjectileModel(
                        speed, gravity, ballistics.drag(), ballistics.isQuadraticDrag(), dragDensity,
                        fuelTicks, maxFlightTicks, SMALL_ROCKET_THRUST_INCREASE, SMALL_ROCKET_MAX_THRUST
                );
            } else if (kind == CannonKind.MEDIUM_ROCKET_RAIL) {
                model = new CBCATRocketProjectileModel(
                        speed, gravity, ballistics.drag(), ballistics.isQuadraticDrag(), dragDensity,
                        fuelTicks, maxFlightTicks, MEDIUM_ROCKET_THRUST_INCREASE, MEDIUM_ROCKET_MAX_THRUST
                );
            } else {
                model = ProjectileModel.cbc(speed, gravity, ballistics.drag(), dragDensity, ballistics.isQuadraticDrag());
            }

            String ammunitionId = String.valueOf(BuiltInRegistries.ITEM.getKey(ammunition.getItem()));
            String reason = kind.name().toLowerCase() + (strong ? "_strong" : "") + "_resolved";
            String fingerprint = Integer.toHexString(java.util.Objects.hash(
                    kind, ammunitionId, ammunition.getComponentsPatch(), speed, ballistics, model,
                    fuelTicks, maxFlightTicks, strong
            ));
            return new ShotState(kind, speed, ballistics, model, fuelTicks, maxFlightTicks,
                    ammunitionId, fingerprint, reason);
        } catch (Throwable throwable) {
            DiagnosticRecorder.warn("cbc_at", "resolve_shot_state",
                    "next_shot_resolution_failed", throwable, level, null,
                    "cbc_at", "createbigcannons");
            if (RESOLUTION_WARNING_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Could not resolve CBC:AT 0.1.4c next-shot state; affected cannon will not receive an approximate solution", throwable);
            }
            return null;
        }
    }

    private static int readFuelTicks(ItemStack ammunition) {
        Byte fuel = ammunition.get(DataComponentRegistry.ROCKET_FUEL);
        return fuel == null ? 0 : Math.max(0, fuel.intValue());
    }

    private static int countSpeedIncreasingBarrels(
            AbstractMountedCannonContraption cannon,
            CannonKind kind,
            int maxBarrelLength
    ) {
        Direction direction = cannon.initialOrientation();
        BlockPos start = cannon.getStartPos();
        if (direction == null || start == null || cannon.presentBlockEntities == null) {
            return 0;
        }
        int count = 0;
        BlockPos position = start.relative(direction);
        int safeLimit = Math.max(1, maxBarrelLength + 1);
        while (count < safeLimit && isBarrelForKind(cannon.presentBlockEntities.get(position), kind)) {
            count++;
            position = position.relative(direction);
        }
        return count;
    }

    private static boolean isBarrelForKind(@Nullable BlockEntity blockEntity, CannonKind kind) {
        return switch (kind) {
            case HEAVY_AUTOCANNON -> blockEntity instanceof IHeavyAutocannonBlockEntity;
            case TWIN_AUTOCANNON -> blockEntity instanceof ITwinAutocannonBlockEntity;
            case ROCKET_POD -> blockEntity instanceof IRocketPodBlockEntity;
            case MEDIUM_ROCKET_RAIL -> blockEntity instanceof IMediumRocketPodBlockEntity;
        };
    }

    @Nullable
    private static ItemStack findLoadedAmmunition(AbstractMountedCannonContraption cannon, CannonKind kind, ServerLevel level) {
        if (cannon instanceof GetItemStorage storageOwner) {
            var storage = storageOwner.getItemStorage();
            if (storage != null) {
                for (int slot = 0; slot < storage.getSlots(); slot++) {
                    ItemStack stack = storage.getStackInSlot(slot);
                    if (isAmmunitionForKind(stack, kind) && createProjectile(stack, level) != null) {
                        return stack.copy();
                    }
                }
            }
        }

        if (kind == CannonKind.HEAVY_AUTOCANNON && cannon.presentBlockEntities != null) {
            for (BlockEntity blockEntity : cannon.presentBlockEntities.values()) {
                if (blockEntity instanceof CBCATQuickFireBreechAccessor accessor) {
                    ItemStack stack = accessor.createRadar$getCartridge();
                    if (isAmmunitionForKind(stack, kind) && createProjectile(stack, level) != null) {
                        return stack.copy();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isAmmunitionForKind(@Nullable ItemStack stack, CannonKind kind) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return switch (kind) {
            case HEAVY_AUTOCANNON -> item instanceof HeavyAutocannonAmmoItem
                    || item instanceof AbstractHeavyAutocannonProjectileItem;
            case TWIN_AUTOCANNON -> item instanceof AutocannonAmmoItem;
            case ROCKET_POD -> item instanceof AbstractRocketItem || item instanceof RocketItem;
            case MEDIUM_ROCKET_RAIL -> item instanceof AbstractMediumRocketItem || item instanceof MediumRocketItem;
        };
    }
}
