package com.happysg.radar.compat.cbcmw;

import com.happysg.radar.api.weapon.WeaponFirePreparation;
import com.happysg.radar.api.weapon.WeaponShotAdapterRegistry;
import com.happysg.radar.api.weapon.WeaponShotProfile;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.cbc.CbcRadarWeaponAdapter;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.happysg.radar.targeting.*;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMunitionsLauncherContraption;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.MediumcannonBlock;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.IMediumcannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.breech.MediumcannonBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.material.MediumcannonMaterial;
import riftyboi.cbcmodernwarfare.cannons.munitions_contraption_launcher.IMunitionsLauncherBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.munitions_contraption_launcher.MunitionsLauncherBehavior;
import riftyboi.cbcmodernwarfare.cannons.munitions_contraption_launcher.MunitionsLauncherBlock;
import riftyboi.cbcmodernwarfare.cannons.munitions_contraption_launcher.breeches.IBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.munitions_contraption_launcher.breeches.quickfiring.LauncherQuickFiringBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.RotarycannonBlock;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.IRotarycannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.breech.AbstractRotarycannonBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.material.RotarycannonMaterial;
import riftyboi.cbcmodernwarfare.index.CBCModernWarfareEntityTypes;
import riftyboi.cbcmodernwarfare.index.CBCModernWarfareMunitionPropertiesHandlers;
import riftyboi.cbcmodernwarfare.munitions.contraptions.config.MunitionsContraptionEntityProperties;
import riftyboi.cbcmodernwarfare.munitions.medium_cannon.MediumcannonAmmoItem;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.fuel_tanks.IFuelTankBlock;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.IGuidanceBlock;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.propulsion.IThrusterBlock;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.propulsion.MunitionsLauncherThrusterBlock;

import javax.annotation.Nullable;
import java.util.List;

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

    public record LauncherProjectileModel(
            double muzzleSpeed, double gravity, double drag,
            boolean quadraticDrag, double dragDensity, int fuelTicks,
            float burnRate, float thrust, double sampledFluidDrag
    ) implements ProjectileModel {
        @Override
        public boolean cbcPhysics() {
            return true;
        }

        @Override
        public boolean usesCustomDynamics() {
            return true;
        }

        @Override
        public ProjectileDynamics createDynamics(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity) {
            return new ProjectileDynamics() {
                private int fuel = Math.max(0, fuelTicks);

                @Override
                public void step(int tick, double x, double y, double z, double vx, double vy, double vz, Level level, ProjectileStep output) {
                    double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    double invSpeed = speed > 1.0E-4 ? 1.0 / speed : 0.0;
                    double density = dragDensity + sampledFluidDrag;

                    double dragForce = drag * density * speed;

                    if (quadraticDrag) {
                        dragForce *= speed;
                    }

                    dragForce = Math.min(dragForce, speed);
                    boolean powered = fuel > 0;
                    double axialForce = -dragForce;

                    if (powered) {
                        double thrustFactor = Math.max(0.1, 1.0 / (1.0 + speed * speed));
                        double thrustForce = thrust * speed * density * thrustFactor;

                        if (quadraticDrag) {
                            thrustForce *= speed;
                        }

                        axialForce += thrustForce;

                        fuel = (int) Math.max((float) fuel - burnRate, 0.0F);
                    }

                    double ax = vx * invSpeed * axialForce;
                    double ay = vy * invSpeed * axialForce + gravity;
                    double az = vz * invSpeed * axialForce;

                    output.set(
                            x + vx + ax * 0.5,
                            y + vy + ay * 0.5,
                            z + vz + az * 0.5,
                            vx + ax,
                            vy + ay,
                            vz + az
                    );
                }
            };
        }
    }

    @Nullable
    public static LauncherProjectileModel resolveLauncherProjectileModel(AbstractMountedCannonContraption cannon, ServerLevel level, BlockPos mountPos) {
        if (cannon == null || level == null || mountPos == null || !isMunitionsLauncher(cannon)) {
            return null;
        }

        LauncherLoadout loadout = resolveLauncherLoadout(cannon, level);

        if (loadout == null) {
            return null;
        }

        if (!level.getServer().isSameThread()) {
            return null;
        }

        if (!(cannon.entity instanceof PitchOrientedContraptionEntity mounted)) {
            return null;
        }

        Vec3 spawn = getLauncherSpawnAnchor(mounted, cannon, loadout);

        if (spawn == null) {
            return null;
        }

        if (Mods.SABLE.isLoaded()) {
            SubLevelAccess sublevel = SableCompanion.INSTANCE.getContaining(level, mountPos);
            spawn = SableUtils.getWorldVec(spawn, sublevel);
        }

        BlockPos spawnBlock = BlockPos.containing(spawn);
        if (level.getChunkSource().getChunkNow(spawnBlock.getX() >> 4, spawnBlock.getZ() >> 4) == null) {
            return null;
        }

        double sampledFluidDrag = LoadedFluidDrag.sample(level, spawnBlock);
        if (!Double.isFinite(sampledFluidDrag) || sampledFluidDrag < 0.0) {
            return null;
        }

        MunitionsContraptionEntityProperties properties = CBCModernWarfareMunitionPropertiesHandlers.MUNITIONS_CONTRAPTION.getPropertiesOf(CBCModernWarfareEntityTypes.MUNITIONS_CONTRAPTION.get());

        var dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        double gravity = loadout.gravity() * dimension.gravityMultiplier();

        double drag = properties.drag();
        double dragDensity = dimension.dragMultiplier();

        boolean quadraticDrag = properties.isQuadraticDrag();

        if (!Double.isFinite(gravity) || !Double.isFinite(drag) || !Double.isFinite(dragDensity) || drag < 0.0 || dragDensity < 0.0) {
            return null;
        }

        return new LauncherProjectileModel(
                loadout.initialSpeed(), gravity, drag,
                quadraticDrag, dragDensity, loadout.fuelTicks(),
                loadout.burnRate(), loadout.thrust(), sampledFluidDrag
        );
    }

    public record LauncherShotPreview(LauncherLoadout loadout, LauncherProjectileModel model, Vec3 muzzleWorldPosition, String fingerprint) { }
    public record LauncherLoadout(float initialSpeed, float gravity, int fuelTicks, float burnRate, float thrust, BlockPos muzzleExitLocal) { }

    @Nullable
    public static LauncherShotPreview resolveLauncherShotPreview(CannonMountContext mount, ServerLevel level) {
        if (mount == null || level == null || !mount.isCurrent()) {
            return null;
        }

        PitchOrientedContraptionEntity mounted = mount.getContraption();

        if (mounted == null || !mounted.isAlive() || !(mounted.getContraption() instanceof AbstractMountedCannonContraption cannon) || !isMunitionsLauncher(cannon)) {
            return null;
        }

        LauncherLoadout loadout = resolveLauncherLoadout(cannon, level);

        if (loadout == null) {
            return null;
        }

        LauncherProjectileModel model = resolveLauncherProjectileModel(cannon, level, mount.getBlockPos());
        if (model == null) {
            return null;
        }

        Vec3 muzzle = getLauncherSpawnAnchor(mounted, cannon, loadout);
        if (muzzle == null) {
            return null;
        }

        if (Mods.SABLE.isLoaded()) {
            SubLevelAccess sublevel = SableCompanion.INSTANCE.getContaining(level, mount.getBlockPos());
            muzzle = SableUtils.getWorldVec(muzzle, sublevel);
        }

        if (!Double.isFinite(muzzle.x) || !Double.isFinite(muzzle.y) || !Double.isFinite(muzzle.z)) {
            return null;
        }

        String fingerprint = Integer.toHexString(java.util.Objects.hash("cbcmw_launcher", loadout, model));
        return new LauncherShotPreview(loadout, model, muzzle, fingerprint);
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

    public static boolean isMunitionsLauncher(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedMunitionsLauncherContraption;
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

    @Nullable
    public static LauncherLoadout resolveLauncherLoadout(AbstractMountedCannonContraption cannon, Level level) {
        if (!isMunitionsLauncher(cannon) || level == null) {
            return null;
        }

        BlockPos start = cannon.getStartPos();
        Direction direction = cannon.initialOrientation();
        if (start == null || direction == null) {
            return null;
        }

        BlockEntity rearBreech = cannon.presentBlockEntities.get(start.relative(direction.getOpposite()));
        if (rearBreech instanceof LauncherQuickFiringBreechBlockEntity quickfiring && quickfiring.getOpenProgress() > 0) {
            return null;
        }

        BlockEntity startEntity = cannon.presentBlockEntities.get(start);
        if (startEntity == null || !(startEntity.getBlockState().getBlock() instanceof MunitionsLauncherBlock launcherBlock)) {
            return null;
        }

        var material = launcherBlock.getCannonMaterial();
        if (material == null || material.properties().isGunLauncher()) {
            return null;
        }

        float initialSpeed = material.properties().initialSpeed();
        if (!Float.isFinite(initialSpeed) || initialSpeed <= 0.0F) {
            return null;
        }

        float gravity = 0.0F;
        float burnRate = 0.0F;
        float thrust = 0.0F;
        long storedFuel = 0;

        int thrusterCount = 0;
        int warheadCount = 0;
        int loadedBlocks = 0;
        boolean passedEmptyTube = false;
        boolean reachedMuzzle = false;

        BlockPos breechPos = start.relative(direction.getOpposite());
        BlockPos current = cannon.presentBlockEntities.get(breechPos) instanceof IBreechBlockEntity ? start : breechPos;

        int maxBlocks = Math.max(1, cannon.getBlocks().size() + 1);

        for (int i = 0; i < maxBlocks; i++) {
            BlockEntity entity = cannon.presentBlockEntities.get(current);
            if (!(entity instanceof IMunitionsLauncherBlockEntity launcher)) {
                if (cannon.getBlocks().containsKey(current)) {
                    return null;
                }

                reachedMuzzle = true;
                break;
            }

            MunitionsLauncherBehavior behavior = launcher.cannonBehavior();
            if (behavior == null) {
                return null;
            }

            var loaded = behavior.block();
            if (loaded == null) {
                return null;
            }

            if (loaded.state().isAir()) {
                if (loadedBlocks == 0) {
                    return null;
                }

                passedEmptyTube = true;
                current = current.relative(direction);
                continue;
            }

            if (passedEmptyTube) {
                return null;
            }

            var block = loaded.state().getBlock();
            if (block instanceof IGuidanceBlock) {
                return null;
            }

            if (block instanceof IThrusterBlock thruster) {
                if (!(block instanceof MunitionsLauncherThrusterBlock)) {
                    return null;
                }

                gravity += thruster.addedGravity();
                gravity += thruster.addedGravity();
                storedFuel += thruster.storedFuel();
                burnRate += thruster.burnRate();
                thrust += thruster.thrust();
                thrusterCount++;
            } else if (block instanceof IFuelTankBlock tank) {
                var properties = tank.getProperties().fuelTankProperties();
                gravity += properties.addedGravity();
                storedFuel += properties.storedFuel();
            } else if (block instanceof ProjectileBlock warhead) {
                if (warheadCount != 0 || warhead.getExpectedSize() != 1) {
                    return null;
                }

                List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> pieces = List.of(loaded);
                if (!warhead.isValidAddition(pieces, loaded, 0, direction) || !warhead.isComplete(pieces, direction)) {
                    return null;
                }

                var projectile = warhead.getProjectile(level, pieces);
                BallisticPropertiesComponent properties = CannonUtil.getProjectileBallistics(projectile);

                if (properties == null || !Double.isFinite(properties.gravity())) {
                    return null;
                }

                gravity += (float) Math.abs(properties.gravity());
                warheadCount++;

            } else {
                return null;
            }

            loadedBlocks++;
            current = current.relative(direction);
        }

        if (loadedBlocks == 0 ||
                loadedBlocks == 0 ||
                thrusterCount != 1 ||
                storedFuel <= 0 ||
                storedFuel > Integer.MAX_VALUE / 20L ||
                !Float.isFinite(burnRate) ||
                !Float.isFinite(thrust) ||
                !Float.isFinite(gravity) ||
                burnRate <= 0.0F ||
                thrust <= 0.0F) {
            return null;
        }

        int fuelTicks = (int) storedFuel * 20;
        gravity = Math.min(gravity, -0.02F);

        return new LauncherLoadout(
                initialSpeed,
                gravity,
                fuelTicks,
                burnRate,
                thrust,
                current.immutable()
        );
    }

    /**
     * Returns CBCMW's actual launcher projectile spawn position.
     */
    @Nullable
    public static Vec3 getLauncherSpawnAnchor(PitchOrientedContraptionEntity mounted, AbstractMountedCannonContraption cannon, LauncherLoadout loadout) {
        if (mounted == null || cannon == null || loadout == null) {
            return null;
        }

        Direction direction = cannon.initialOrientation();

        if (direction == null || loadout.muzzleExitLocal() == null) {
            return null;
        }

        Vec3 spawn = mounted.toGlobalVector(Vec3.atCenterOf(loadout.muzzleExitLocal().relative(direction)), 0);
        Vec3 origin = mounted.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        Vec3 forward = spawn.subtract(origin);

        if (forward.lengthSqr() < 1.0E-12) {
            return null;
        }

        return spawn.subtract(forward.normalize().scale(2.0)).add(0.0, -0.5, 0.0);
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

    /**
     * Registers a read-only aiming profile for CBCMW launchers.
     * Automatic firing remains disabled until launcher
     * assembly and trajectory validation are complete.
     */
    public static void registerLauncherShotAdapter() {
        WeaponShotAdapterRegistry.register("create_radar:cbcmw_launcher_preview",
                context -> {
                    CannonMountContext mount = context.weapon() instanceof CbcRadarWeaponAdapter cbc ? cbc.legacyCbcContext() : CannonMountContext.resolveEndpoint(context.level(), context.mountPos());
                    if (mount == null || !mount.isCurrent()) {
                        return null;
                    }

                    PitchOrientedContraptionEntity mounted = mount.getContraption();
                    if (mounted == null || !mounted.isAlive()
                            || !(mounted.getContraption() instanceof AbstractMountedCannonContraption cannon)
                            || !isMunitionsLauncher(cannon)) {
                        return null;
                    }

                    LauncherShotPreview preview = resolveLauncherShotPreview(mount, context.level());

                    if (preview == null) {
                        return null;
                    }

                    return WeaponShotProfile.ballistic(
                            ProjectileModelBridge.toApi(preview.model()),
                            preview.muzzleWorldPosition(),
                            Vec3.ZERO,
                            0,
                            preview.fingerprint(),
                            false,
                            WeaponFirePreparation.READY,
                            "cbcmw_launcher_aim_preview"
                    );
                }
        );
    }
}
