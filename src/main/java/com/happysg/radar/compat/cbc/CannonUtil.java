package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc_at.CBCATCannonCompat;
import com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWMountCompat;
import com.happysg.radar.mixin.AbstractCannonAccessor;
import com.happysg.radar.mixin.AutoCannonAccessor;
import com.happysg.radar.debug.DiagnosticRecorder;
import com.happysg.radar.debug.ConflictTraceRecorder;
import com.happysg.radar.targeting.ProjectileSimulator;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.arsenalists.createenergycannons.content.cannons.magnetic.railgun.MountedEnergyCannonContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;


import org.slf4j.Logger;

import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;

import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.autocannon.ammo_container.AutocannonAmmoContainerItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonCommonShellProperties;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.IntegratedPropellantProjectile;
import rbasamoyai.createbigcannons.munitions.config.BigCannonPropellantCompatibilityHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.remix.GetItemStorage;


import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class CannonUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean CBC_AT_BIG_CANNON_LINKAGE_WARNING_LOGGED = new AtomicBoolean();
    private static final Map<BallisticWarningKey, Long> BALLISTIC_WARNING_TICKS =
            new ConcurrentHashMap<>();
    private static final long BALLISTIC_WARNING_INTERVAL_TICKS = 100L;
    private static final long BALLISTIC_WARNING_STALE_TICKS = 1200L;
    private static final int BALLISTIC_WARNING_CLEANUP_THRESHOLD = 256;
    private static final BallisticPropertiesComponent AC_FALLBACK = new BallisticPropertiesComponent(-0.025, 0.01, false, 0, 0, 0, 0);
    private static final BallisticPropertiesComponent BIG_CANNON_LAST_RESORT_FALLBACK = new BallisticPropertiesComponent(-0.05, 0.0, false, 0, 0, 0, 0);

    public record BigCannonShotState(
            float speed,
            BallisticPropertiesComponent ballistics,
            @Nullable String projectileClass,
            @Nullable BlockPos projectileLocalPos,
            @Nullable BlockPos muzzleExitLocalPos,
            double muzzleForwardOffset,
            int propellantCharges,
            float propellantPower,
            float projectileAddedPower,
            String reason
    ) {
        public boolean hasProjectile() {
            return projectileClass != null;
        }
    }

    public static boolean isAutocannonFamily(AbstractMountedCannonContraption cannon) {
        return isAutoCannon(cannon)
                || isTwinAutocannon(cannon)
                || isHeavyAutocannon(cannon)
                || isRocketPod(cannon)
                || isMediumRocketRail(cannon)
                || isCBCMWCannon(cannon);
    }

    public static int getBarrelLength(AbstractMountedCannonContraption cannon) {
        if (cannon == null)
            return 0;
        if(cannon.initialOrientation() == Direction.WEST || cannon.initialOrientation() == Direction.NORTH){
            return ((AbstractCannonAccessor) cannon).getBackExtensionLength();
        }
        else{
            return ((AbstractCannonAccessor) cannon).getFrontExtensionLength();
        }
    }
    public static Vec3 getCannonMountOffset(Level level, BlockPos pos) {
        return getCannonMountOffset(level.getBlockEntity(pos));
    }

    public static Vec3 getCannonMountOffset(BlockEntity mount) {
        if (mount == null) return Vec3.ZERO;

        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWMountCompat.isCompactMount(mount)) {
            return CBCMWMountCompat.getMountOffset(mount);
        }

        return isUp(mount) ? new Vec3(0, 2, 0) : new Vec3(0, -2, 0);
    }

    public static BallisticPropertiesComponent getAutocannonBallistics(AbstractMountedCannonContraption cannon, Level level) {
        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon)) {
            CBCMWCannonCompat.ShotState shotState = CBCMWCannonCompat.resolveShotState(cannon, level);
            if (shotState != null) {
                return shotState.ballistics();
            }
        }
        if (Mods.CBC_AT.isLoaded() && level instanceof ServerLevel serverLevel && CBCATCannonCompat.isCBCATCannon(cannon)) {
            CBCATCannonCompat.ShotState shotState = CBCATCannonCompat.resolveShotState(cannon, serverLevel);
            if (shotState != null) {
                return shotState.ballistics();
            }
        }
        BallisticPropertiesComponent loaded = resolveLoadedAutocannonBallistics(cannon, level);
        if (loaded != null) {
            return loaded;
        }
        return AC_FALLBACK;
    }

    @Nullable
    public static BallisticPropertiesComponent resolveLoadedAutocannonBallistics(
            AbstractMountedCannonContraption cannon, Level level) {
        if (cannon instanceof MountedAutocannonContraption
                && cannon.getStartPos() != null
                && cannon.presentBlockEntities.get(cannon.getStartPos())
                instanceof AbstractAutocannonBreechBlockEntity breech) {
            // The automation handler hides partially filled queues and magazines.
            // Preview the same round extractNextInput() will consume, in FIFO order.
            return level == null ? null : getProjectileBallistics(
                    createAutocannonProjectile(peekAutocannonAmmo(breech), level));
        }
        if (cannon == null || level == null || !(cannon instanceof GetItemStorage storageOwner)) {
            return null;
        }

        var storage = storageOwner.getItemStorage();
        if (storage == null) {
            return null;
        }

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            AbstractCannonProjectile projectile = createAutocannonProjectile(stack, level);
            BallisticPropertiesComponent props = getProjectileBallistics(projectile);
            if (props != null) {
                return props;
            }
        }

        return null;
    }

    public static ItemStack peekAutocannonAmmo(AbstractAutocannonBreechBlockEntity breech) {
        ItemStack queued = breech.getInputBuffer().peekFirst();
        if (queued != null) {
            return queued.copy();
        }
        // Poll a copy so CBC's tracer/creative-magazine selection stays exact
        // without consuming ammunition or advancing the real magazine's cursor.
        ItemStack magazine = breech.getMagazine();
        return magazine.isEmpty() ? ItemStack.EMPTY
                : AutocannonAmmoContainerItem.pollItemFromContainer(magazine.copy());
    }

    @Nullable
    private static AbstractCannonProjectile createAutocannonProjectile(ItemStack stack, Level level) {
        try {
            if (stack.getItem() instanceof AutocannonAmmoItem item) {
                return item.getAutocannonProjectile(stack, level);
            }
            if (Mods.CBC_AT.isLoaded()) {
                return CBCATCompat.createAutocannonProjectile(stack, level);
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not resolve loaded autocannon projectile ballistics for {}", stack, t);
            DiagnosticRecorder.warn("weapon_adapter",
                    "autocannon_projectile", "projectile_resolution_failed",
                    t, level, null, "createbigcannons", "cbc_at");
        }

        return null;
    }

    public static BallisticPropertiesComponent getBallistics(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) {
            LOGGER.warn("Could not read cannon type for ballistics: cannon={} level={}; using HE shell fallback", cannon, level);
            return getBigCannonFallbackBallistics();
        }

        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level);
        }

        logCannonTypeReadFailure("getBallistics", cannon);
        return resolveBigCannonShotState(cannon, level).ballistics();
    }

    public static BigCannonShotState resolveBigCannonShotState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        BallisticPropertiesComponent fallback = getBigCannonFallbackBallistics();
        if (cannon == null || level == null) {
            LOGGER.warn("Could not resolve big cannon shot state: cannon={} level={}; using HE shell fallback", cannon, level);
            return new BigCannonShotState(0.0F, fallback, null, null, null, 0.0, 0, 0.0F, 0.0F, "missing_cannon_or_level");
        }

        Direction direction = cannon.initialOrientation();
        BlockPos currentPos = cannon.getStartPos();
        if (direction == null || currentPos == null || cannon.presentBlockEntities == null) {
            LOGGER.warn("Could not resolve big cannon shot state for {}: direction={} start={} entities={}; using HE shell fallback",
                    cannon.getClass().getName(), direction, currentPos, cannon.presentBlockEntities);
            return new BigCannonShotState(0.0F, fallback, null, null, null, CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), 0, 0.0F, 0.0F, "missing_cannon_geometry");
        }

        CBCATCompat.BigCannonPhysicsResult cbcAtPhysics = resolveCBCATBigCannonPhysics(cannon, level);

        float speed = 0.0F;
        float propellantPower = 0.0F;
        float projectileAddedPower = 0.0F;
        int propellantCharges = 0;
        int cannonBlocksSeen = 0;
        List<StructureTemplate.StructureBlockInfo> propellantBlocks = new ArrayList<>();
        List<StructureTemplate.StructureBlockInfo> projectileBlocks = new ArrayList<>();
        AbstractBigCannonProjectile projectile = null;
        BlockPos projectileLocalPos = null;

        while (cannon.presentBlockEntities.get(currentPos) instanceof IBigCannonBlockEntity cannonBlockEntity) {
            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();
            StructureTemplate.StructureBlockInfo cannonBlockInfo = cannon.getBlocks().get(currentPos);
            if (containedBlockInfo == null || cannonBlockInfo == null) {
                break;
            }

            Block block = containedBlockInfo.state().getBlock();
            if (containedBlockInfo.state().isAir()) {
                if (cannonBlocksSeen == 0) {
                    return new BigCannonShotState(0.0F, fallback, null, null, currentPos, CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), 0, 0.0F, 0.0F, "empty_start");
                }
                if (projectile == null && projectileBlocks.isEmpty()) {
                    speed = Math.max(speed - 1.0F, 0.0F);
                } else if (projectile == null) {
                    LOGGER.warn("Big cannon projectile assembly was interrupted by air at {}; using partial speed={} and HE shell fallback", currentPos, speed);
                    return new BigCannonShotState(speed, fallback, null, null, currentPos, CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges, propellantPower, projectileAddedPower, "projectile_air_gap");
                }
            } else if (block instanceof BigCannonPropellantBlock propellantBlock
                    && !(block instanceof ProjectileBlock<?>)) {
                if (cannonBlocksSeen == 0
                        && !propellantBlock.canBeIgnited(containedBlockInfo, direction)) {
                    return new BigCannonShotState(speed, fallback, null, null, currentPos,
                            CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                            propellantPower, projectileAddedPower, "unignitable_starting_propellant");
                }
                List<StructureTemplate.StructureBlockInfo> candidate = new ArrayList<>(propellantBlocks);
                candidate.add(containedBlockInfo);
                if (!isSafeBigCannonPropellantLoad(candidate, direction)) {
                    return new BigCannonShotState(speed, fallback, null, null, currentPos,
                            CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                            propellantPower, projectileAddedPower, "invalid_propellant_load");
                }
                propellantBlocks.add(containedBlockInfo);
                float chargePower = Math.max(0.0F, propellantBlock.getChargePower(containedBlockInfo));
                speed += chargePower;
                propellantPower += chargePower;
                propellantCharges++;
            } else if (block instanceof ProjectileBlock<?> projectileBlock && projectile == null) {
                projectileBlocks.add(containedBlockInfo);
                if (projectileLocalPos == null) {
                    projectileLocalPos = currentPos.immutable();
                }
                if (!isValidBigCannonProjectileAssembly(projectileBlocks, direction)) {
                    return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                            CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                            propellantPower, projectileAddedPower, "invalid_projectile_assembly");
                }
                if (projectileBlock.isComplete(projectileBlocks, direction)) {
                    projectile = projectileBlock.getProjectile(level, List.copyOf(projectileBlocks));
                    if (projectile == null) {
                        return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                                CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                                propellantPower, projectileAddedPower, "projectile_resolution_failed");
                    }
                    projectileAddedPower = projectile.addedChargePower();
                    speed += projectileAddedPower;
                }
            } else {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "invalid_loaded_block");
            }

            cannonBlocksSeen++;
            currentPos = currentPos.relative(direction);
        }

        if (projectile == null && !projectileBlocks.isEmpty()) {
            StructureTemplate.StructureBlockInfo firstProjectileInfo = projectileBlocks.get(0);
            if (!(firstProjectileInfo.state().getBlock() instanceof ProjectileBlock<?> projectileBlock)) {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "invalid_projectile_assembly");
            }
            int remaining = projectileBlock.getExpectedSize() - projectileBlocks.size();
            if (remaining < 1) {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "incomplete_projectile_assembly");
            }
            for (int i = 0; i < remaining; i++) {
                StructureTemplate.StructureBlockInfo additionalInfo = cannon.getBlocks().get(currentPos);
                if (additionalInfo == null) {
                    return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                            CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                            propellantPower, projectileAddedPower, "incomplete_projectile_assembly");
                }
                projectileBlocks.add(additionalInfo);
                if (!isValidBigCannonProjectileAssembly(projectileBlocks, direction)) {
                    return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                            CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                            propellantPower, projectileAddedPower, "invalid_projectile_assembly");
                }
                currentPos = currentPos.relative(direction);
            }
            if (!projectileBlock.isComplete(projectileBlocks, direction)) {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "incomplete_projectile_assembly");
            }
            projectile = projectileBlock.getProjectile(level, List.copyOf(projectileBlocks));
            if (projectile == null) {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "projectile_resolution_failed");
            }
            projectileAddedPower = projectile.addedChargePower();
            speed += projectileAddedPower;
        }

        if (projectile instanceof IntegratedPropellantProjectile integratedPropellant
                && !projectileBlocks.isEmpty()) {
            List<StructureTemplate.StructureBlockInfo> candidate = new ArrayList<>(propellantBlocks);
            candidate.add(projectileBlocks.get(0));
            if (!isSafeBigCannonPropellantLoad(candidate, direction)) {
                return new BigCannonShotState(speed, fallback, null, projectileLocalPos, currentPos,
                        CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges,
                        propellantPower, projectileAddedPower, "invalid_integrated_propellant_load");
            }
            float integratedPower = Math.max(0.0F, integratedPropellant.getChargePower());
            speed += integratedPower;
            propellantPower += integratedPower;
            propellantCharges++;
        }

        BlockPos muzzleExit = CBCMuzzleUtil.getMuzzleExitLocal(cannon);
        AbstractBigCannonProjectile resolvedProjectile = projectile != null
                ? projectile
                : (cbcAtPhysics != null ? cbcAtPhysics.projectile() : null);
        if (resolvedProjectile != null) {
            if (projectile == null && cbcAtPhysics != null) {
                projectileLocalPos = cbcAtPhysics.projectileLocalPos();
                projectileAddedPower = cbcAtPhysics.projectileAddedPower();
            }
            BallisticPropertiesComponent ballistics = getProjectileBallistics(resolvedProjectile);
            if (ballistics == null) {
                LOGGER.warn(
                        "Could not read big cannon projectile ballistics for {}; using HE shell fallback {}",
                        resolvedProjectile.getClass().getName(),
                        fallback
                );
                ballistics = fallback;
            }
            float effectiveSpeed = effectiveBigCannonSpeed(speed, cbcAtPhysics);
            if (effectiveSpeed <= 0.0F && shouldLogBallisticWarning(level, cannon,
                    "non_positive_charge", projectileLocalPos)) {
                LOGGER.warn("Big cannon loaded projectile at {} resolved non-positive charge power {}; using projectile ballistics but speed is invalid",
                        projectileLocalPos, effectiveSpeed);
            }
            String reason = projectile == null
                    ? "loaded_cbc_at_cartridge"
                    : "loaded_projectile";
            BigCannonShotState state = new BigCannonShotState(
                    effectiveSpeed,
                    ballistics,
                    resolvedProjectile.getClass().getName(),
                    projectileLocalPos,
                    muzzleExit,
                    CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon),
                    propellantCharges,
                    propellantPower,
                    projectileAddedPower,
                    bigCannonReason(reason, cbcAtPhysics)
            );
            LOGGER.debug("Resolved big cannon shot state: speed={} projectile={} projectileLocal={} muzzleExit={} muzzleOffset={} gravity={} drag={} quadratic={}",
                    state.speed(), state.projectileClass(), state.projectileLocalPos(), state.muzzleExitLocalPos(), state.muzzleForwardOffset(),
                    state.ballistics().gravity(), state.ballistics().drag(), state.ballistics().isQuadraticDrag());
            return state;
        }

        if (shouldLogBallisticWarning(level, cannon,
                "no_loaded_projectile", muzzleExit)) {
            LOGGER.warn("No loaded big cannon projectile found during ordered resolve; speed={} muzzleExit={} using HE shell fallback {}",
                    speed, muzzleExit, fallback);
        }
        return new BigCannonShotState(speed, fallback, null, null, muzzleExit, CBCMuzzleUtil.getBigCannonSpawnForwardOffset(cannon), propellantCharges, propellantPower, projectileAddedPower, "no_loaded_projectile");
    }

    private static boolean isValidBigCannonProjectileAssembly(
            List<StructureTemplate.StructureBlockInfo> projectileBlocks,
            Direction direction
    ) {
        List<StructureTemplate.StructureBlockInfo> copy = List.copyOf(projectileBlocks);
        for (ListIterator<StructureTemplate.StructureBlockInfo> iterator = projectileBlocks.listIterator();
             iterator.hasNext(); ) {
            int index = iterator.nextIndex();
            StructureTemplate.StructureBlockInfo info = iterator.next();
            if (!(info.state().getBlock() instanceof ProjectileBlock<?> projectileBlock)
                    || !projectileBlock.isValidAddition(copy, info, index, direction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeBigCannonPropellantLoad(
            List<StructureTemplate.StructureBlockInfo> propellantBlocks,
            Direction direction
    ) {
        Map<Block, Integer> allowedCounts = new HashMap<>();
        Set<Block> foundBlocks = new HashSet<>();
        for (ListIterator<StructureTemplate.StructureBlockInfo> iterator = propellantBlocks.listIterator();
             iterator.hasNext(); ) {
            int index = iterator.nextIndex();
            StructureTemplate.StructureBlockInfo info = iterator.next();
            Block block = info.state().getBlock();
            if (!(block instanceof BigCannonPropellantBlock propellantBlock)
                    || !propellantBlock.isValidAddition(info, index, direction)) {
                return false;
            }

            var compatibilities = BigCannonPropellantCompatibilityHandler.getCompatibilities(block);
            boolean firstAndPreviouslyUnrestricted = false;
            if (foundBlocks.add(block)) {
                for (Map.Entry<Block, Integer> entry : compatibilities.validPropellantCounts().entrySet()) {
                    Block compatibleBlock = entry.getKey();
                    int oldCount = allowedCounts.getOrDefault(compatibleBlock, -1);
                    int newCount = entry.getValue();
                    if (newCount >= 0 && (oldCount < 0 || newCount < oldCount)) {
                        firstAndPreviouslyUnrestricted = block == compatibleBlock
                                && !allowedCounts.containsKey(block);
                        allowedCounts.put(compatibleBlock, newCount);
                    }
                }
            }
            if (allowedCounts.containsKey(block) && !firstAndPreviouslyUnrestricted) {
                int allowed = allowedCounts.get(block);
                if (allowed <= 0) {
                    return false;
                }
                allowedCounts.put(block, allowed - 1);
            }
        }
        return true;
    }

    private static boolean shouldLogBallisticWarning(
            Level level, AbstractMountedCannonContraption cannon,
            String reason, @Nullable BlockPos localPosition) {
        if (level == null || cannon == null) {
            return true;
        }

        long tick = level.getGameTime();
        BallisticWarningKey key = new BallisticWarningKey(
                System.identityHashCode(cannon), reason,
                localPosition == null ? null : localPosition.immutable());
        Long previousTick = BALLISTIC_WARNING_TICKS.get(key);
        if (previousTick != null && tick >= previousTick
                && tick - previousTick < BALLISTIC_WARNING_INTERVAL_TICKS) {
            return false;
        }

        BALLISTIC_WARNING_TICKS.put(key, tick);
        if (BALLISTIC_WARNING_TICKS.size()
                > BALLISTIC_WARNING_CLEANUP_THRESHOLD) {
            BALLISTIC_WARNING_TICKS.entrySet().removeIf(entry -> {
                long age = tick - entry.getValue();
                return age < 0L || age > BALLISTIC_WARNING_STALE_TICKS;
            });
        }
        return true;
    }

    private static float effectiveBigCannonSpeed(float cbcSpeed, @Nullable CBCATCompat.BigCannonPhysicsResult cbcAtPhysics) {
        return cbcAtPhysics == null ? cbcSpeed : cbcAtPhysics.speed();
    }

    @Nullable
    private static CBCATCompat.BigCannonPhysicsResult resolveCBCATBigCannonPhysics(AbstractMountedCannonContraption cannon, Level level) {
        if (!Mods.CBC_AT.isLoaded() || !isBigCannon(cannon)) {
            return null;
        }
        try {
            return CBCATCompat.resolveBigCannonPhysics(cannon, level);
        } catch (Throwable throwable) {
            DiagnosticRecorder.warn("weapon_adapter", "cbc_at_physics",
                    "cbc_at_physics_fallback", throwable, level, null,
                    "cbc_at");
            if (CBC_AT_BIG_CANNON_LINKAGE_WARNING_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Could not load CBC:AT big cannon physics compatibility; falling back to CBC charge power", throwable);
            }
            return null;
        }
    }

    private static String bigCannonReason(String reason, @Nullable CBCATCompat.BigCannonPhysicsResult cbcAtPhysics) {
        return cbcAtPhysics == null ? reason : reason + "_cbc_at_physics";
    }

    private record BallisticWarningKey(
            int cannonIdentity, String reason,
            @Nullable BlockPos localPosition) {
    }

    public static BallisticPropertiesComponent getBigCannonFallbackBallistics() {
        try {
            BigCannonCommonShellProperties properties = CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE
                    .getPropertiesOf((EntityType<?>) CBCEntityTypes.HE_SHELL.get());
            BallisticPropertiesComponent ballistics = properties == null ? null : properties.ballistics();
            if (ballistics != null) {
                return ballistics;
            }
            LOGGER.warn("Could not read HE shell fallback ballistics: missing shell properties or ballistics; using last-resort fallback {}", BIG_CANNON_LAST_RESORT_FALLBACK);
        } catch (Throwable t) {
            LOGGER.warn("Could not read HE shell fallback ballistics; using last-resort fallback {}", BIG_CANNON_LAST_RESORT_FALLBACK, t);
            DiagnosticRecorder.warn("weapon_adapter", "fallback_ballistics",
                    "fallback_ballistics_failed", t, null, null,
                    "createbigcannons");
        }

        return BIG_CANNON_LAST_RESORT_FALLBACK;
    }

    @Nullable
    public static BallisticPropertiesComponent getProjectileBallistics(AbstractCannonProjectile projectile) {
        if (projectile == null) {
            return null;
        }

        Class<?> type = projectile.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("getBallisticProperties");
                method.setAccessible(true);
                Object result = method.invoke(projectile);
                return result instanceof BallisticPropertiesComponent bp ? bp : null;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOGGER.warn("Could not invoke projectile ballistics method for {}", projectile.getClass().getName(), e);
                return null;
            }
        }

        return null;
    }

//    public static float getRotarySpeed( AbstractMountedCannonContraption contraptionEntity) {
//        if(!Mods.CBCMODERNWARFARE.isLoaded()) return 0f;
//        if(contraptionEntity == null) return 0f;
//        Map<BlockPos, BlockEntity> presentBlockEntities = contraptionEntity.presentBlockEntities;
//        LOGGER.debug(" → presentBlockEntities count = {}", presentBlockEntities.size());
//        if(presentBlockEntities.isEmpty()) return 0f;
//        int barrelCount = 0;
//        RotarycannonMaterial material = null;
//        for (BlockEntity entity : presentBlockEntities.values()) {
//            if(entity instanceof RotarycannonBlockEntity blockEntity && !(entity instanceof RotarycannonBreechBlockEntity)){
//                barrelCount++;
//                if(material == null){
//                    material = ((RotarycannonBlock) blockEntity.getBlockState().getBlock()).getRotarycannonMaterial();
//                }
//            }
//        }
//        if(material == null) return 0;
//        float baseSpeed = material.properties().baseSpeed();
//        int speedIncrease = Math.min(barrelCount, material.properties().maxSpeedIncreases());
//        return baseSpeed+speedIncrease*material.properties().speedIncreasePerBarrel();
//    }
//
//    public static float getMediumCannonSpeed(AbstractMountedCannonContraption contraptionEntity) {
//        if(!Mods.CBCMODERNWARFARE.isLoaded()) return 0f;
//        if(contraptionEntity == null) return 0f;
//        Map<BlockPos, BlockEntity> presentBlockEntities = contraptionEntity.presentBlockEntities;
//        if(presentBlockEntities.isEmpty()) return 0f;
//        int barrelCount = 0;
//        MediumcannonMaterial material = null;
//        List<BlockEntity> blocks = presentBlockEntities.values().stream().toList();
//        for (BlockEntity entity : blocks){
//            if(entity instanceof MediumcannonBlockEntity blockEntity && !(entity instanceof MediumcannonBreechBlockEntity)){
//                barrelCount++;
//                if(material == null){
//                    material = ((MediumcannonBlock) blockEntity.getBlockState().getBlock()).getMediumcannonMaterial();
//                }
//            }
//        }
//        if(material == null) return 0;
//        float baseSpeed = material.properties().baseSpeed();
//        int speedIncrease = Math.min(barrelCount, material.properties().maxSpeedIncreases());
//        return baseSpeed+speedIncrease*material.properties().speedIncreasePerBarrel();
//    }

    public static float getBigCannonSpeed(ServerLevel level, AbstractMountedCannonContraption cannon ,PitchOrientedContraptionEntity contraptionEntity) {
        if(contraptionEntity == null) return 0;
        return resolveBigCannonShotState(cannon, level).speed();
    }

    public static float getInitialVelocity(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null) return 0f;

        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon)) {
            CBCMWCannonCompat.ShotState shotState = CBCMWCannonCompat.resolveShotState(cannon, level);
            return shotState == null ? 0.0F : shotState.speed();
        }

        if (Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATCannon(cannon)) {
            CBCATCannonCompat.ShotState shotState = CBCATCannonCompat.resolveShotState(cannon, level);
            return shotState == null ? 0.0F : shotState.speed();
        }


        if (isEnergyCannon(cannon)) {
            float velocity = ((MountedEnergyCannonContraption) cannon).getMuzzleVelocity(level);
            LOGGER.debug("   • EnergyCannon speed = {}", velocity);
            return velocity;
        }

        if (isBigCannon(cannon)) {
            LOGGER.debug("   • BigCannon speed = {}", getBigCannonSpeed(level,cannon, (PitchOrientedContraptionEntity)cannon.entity));
            return getBigCannonSpeed(level, cannon ,(PitchOrientedContraptionEntity)cannon.entity);
        } else if (isAutocannonFamily(cannon)) {
            LOGGER.debug("   • AutoCannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        }
//        else if(isRotaryCannon(cannon)){
//            LOGGER.debug("   • RotaryCannon speed = {}", getRotarySpeed(cannon));
//            return getRotarySpeed(cannon);
//        }
//        else if(isMediumCannon(cannon)){
//            LOGGER.debug("   • MediumCannon speed = {}", getMediumCannonSpeed(cannon));
//            return getMediumCannonSpeed(cannon);
//        }
        else if(isTwinAutocannon(cannon)){
            LOGGER.debug("   • TwinACannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        } else if(isHeavyAutocannon(cannon)){
            LOGGER.debug("   • HeavyACannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        }
        LOGGER.debug("   • No known cannon type → returning 0");
        return 0;
    }

    public static int getAutocannonLifetimeTicks(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return 100;

        if (!(isAutocannonFamily(cannon) )) {
            return 100;
        }

        try {
            AutocannonMaterial mat = getAutocannonMaterial(cannon);
            if (mat != null) {
                int t = mat.properties().projectileLifetime();
                if (isHeavyAutocannon(cannon)) {
                    t *= 3;
                }
                if (t > 0) return t;
            }
        } catch (Throwable failure) {
            LOGGER.debug("Mixin maybe didnt apply?");
            DiagnosticRecorder.warn("mixin_accessor",
                    "autocannon_lifetime", "autocannon_accessor_failed",
                    failure, null, null, "createbigcannons");
            ConflictTraceRecorder.invariant(false, "mixin_accessor",
                    "autocannon_lifetime", "autocannon_accessor_failed",
                    null, null, "createbigcannons");
        }

        return 100;
    }

    public static int getAutocannonLifetimeTicks(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon)) {
            CBCMWCannonCompat.ShotState shotState = CBCMWCannonCompat.resolveShotState(cannon, level);
            return shotState == null ? 0 : shotState.lifetimeTicks();
        }
        if (Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATCannon(cannon)) {
            CBCATCannonCompat.ShotState shotState = CBCATCannonCompat.resolveShotState(cannon, level);
            return shotState == null ? 0 : shotState.maxFlightTicks();
        }
        return getAutocannonLifetimeTicks(cannon);
    }

    public static double getMaxProjectileRangeBlocks(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return 0;

        double speed = getInitialVelocity(cannon, level);
        if (speed <= 0) return 0;

        // lifetime
        int lifeTicks = getAutocannonLifetimeTicks(cannon, level);
        if (lifeTicks <= 0) return 0;

        if (isAutocannonFamily(cannon)) {
            if (Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isPoweredRocket(cannon)) {
                CBCATCannonCompat.ShotState shotState = CBCATCannonCompat.resolveShotState(cannon, level);
                if (shotState == null || shotState.rocketModel() == null) {
                    return 0.0;
                }
                ProjectileSimulator.SimulationResult simulation = new ProjectileSimulator().simulate(
                        Vec3.ZERO,
                        new Vec3(1.0, 0.0, 0.0),
                        Vec3.ZERO,
                        shotState.rocketModel(),
                        shotState.maxFlightTicks(),
                        null
                );
                Vec3 end = simulation.endPosition();
                return Math.hypot(end.x, end.z);
            }
            BallisticPropertiesComponent bp = getAutocannonBallistics(cannon, level);

            double drag = Math.max(0.0, Math.min(0.25, bp.drag()));
            double retained = Math.pow(1.0 - drag, lifeTicks);
            double avg = (1.0 + retained) * 0.5;
            return speed * lifeTicks * avg;
        }

        // Big cannon path (your existing approximation)
        double drag = getProjectileDrag(cannon, level);
        drag = Math.max(0.0, Math.min(0.25, drag));

        double retained = Math.pow(1.0 - drag, lifeTicks);
        double avg = (1.0 + retained) * 0.5;

        return speed * lifeTicks * avg;
    }

    public static double getProjectileGravity(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level).gravity();
        }
        return getBallistics(cannon, level).gravity();
    }

    public static double getProjectileDrag(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level).drag();
        }
        return getBallistics(cannon, level).drag();
    }

    public static boolean isHeavyAutocannon(AbstractMountedCannonContraption cannon) {
        if(!Mods.CBC_AT.isLoaded()) return false;
        return CBCATCompat.isHeavyAutocannon(cannon);
    }

    public static boolean isTwinAutocannon(AbstractMountedCannonContraption cannon) {
        if(!Mods.CBC_AT.isLoaded()) return false;
        return CBCATCompat.isTwinAutocannon(cannon);
    }

    public static boolean isRocketPod(AbstractMountedCannonContraption cannon) {
        return Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isRocketPod(cannon);
    }

    public static boolean isMediumRocketRail(AbstractMountedCannonContraption cannon) {
        return Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isMediumRocketRail(cannon);
    }

    public static boolean isPoweredRocket(AbstractMountedCannonContraption cannon) {
        return Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isPoweredRocket(cannon);
    }

    public static boolean isCBCATCannon(AbstractMountedCannonContraption cannon) {
        return Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATCannon(cannon);
    }

    public static boolean isCBCMWCannon(AbstractMountedCannonContraption cannon) {
        return Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon);
    }

    public static boolean isMediumCannon(AbstractMountedCannonContraption cannon) {
        return Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isMediumCannon(cannon);
    }

    public static boolean isRotaryCannon(AbstractMountedCannonContraption cannon) {
        return Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isRotaryCannon(cannon);
    }

    @Nullable
    public static CBCMWCannonCompat.ShotState resolveCBCMWShotState(
            AbstractMountedCannonContraption cannon, Level level
    ) {
        return Mods.CBCMODERNWARFARE.isLoaded()
                ? CBCMWCannonCompat.resolveShotState(cannon, level)
                : null;
    }

    public static boolean isCBCMSCannon(AbstractMountedCannonContraption cannon) {
        return Mods.CBCMORESHELLS.isLoaded() && CBCMSCannonCompat.isCBCMSMount(cannon);
    }

    public static boolean isCBCMSFamily(AbstractMountedCannonContraption cannon) {
        return Mods.CBCMORESHELLS.isLoaded()
                && (CBCMSCannonCompat.isCBCMSMount(cannon) || isBigCannon(cannon));
    }

    @Nullable
    public static CBCMSCannonCompat.ShotState resolveCBCMSShotState(
            AbstractMountedCannonContraption cannon, ServerLevel level
    ) {
        return Mods.CBCMORESHELLS.isLoaded() ? CBCMSCannonCompat.resolveShotState(cannon, level) : null;
    }

    @Nullable
    public static CBCATCannonCompat.ShotState resolveCBCATShotState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        return Mods.CBC_AT.isLoaded() ? CBCATCannonCompat.resolveShotState(cannon, level) : null;
    }

    public static boolean isBigCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedBigCannonContraption;
    }

    public static boolean hasBigCannonBlocks(AbstractMountedCannonContraption cannon) {
        if (cannon == null || cannon.presentBlockEntities == null) {
            return false;
        }
        for (BlockEntity blockEntity : cannon.presentBlockEntities.values()) {
            if (blockEntity instanceof IBigCannonBlockEntity) {
                return true;
            }
        }
        return false;
    }

    public static void logCannonTypeReadFailure(String context, AbstractMountedCannonContraption cannon) {
        if (cannon == null) {
            LOGGER.warn("Could not read cannon type in {}: cannon=null", context);
            return;
        }
        if (!isBigCannon(cannon) && hasBigCannonBlocks(cannon)) {
            LOGGER.warn(
                    "Could not read big cannon type in {}: contraption class {} contains big cannon block entities but is not MountedBigCannonContraption",
                    context,
                    cannon.getClass().getName()
            );
        }
    }

    public static boolean isAutoCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedAutocannonContraption;
    }
    public static boolean isEnergyCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CREATEENERGYCANNONS.isLoaded()) return false;
        return cannonContraption instanceof MountedEnergyCannonContraption;
    }

    public static boolean isLaserCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CREATEENERGYCANNONS.isLoaded()) return false;
        return cannonContraption != null && cannonContraption.getClass().getSimpleName().equals("MountedLaserCannonContraption");
    }


    public static boolean isCannonReadyToFire(CannonMountBlockEntity mount) {
        if (mount == null) return false;

        if (Mods.CREATEENERGYCANNONS.isLoaded() && mount instanceof net.arsenalists.createenergycannons.content.energymount.EnergyCannonMountBlockEntity energyMount) {
            return energyMount.isReadyToFire();
        }

        PitchOrientedContraptionEntity entity = mount.getContraption();
        if (entity == null || !entity.isAlive()) return false;
        if (entity.getContraption() instanceof MountedBigCannonContraption cannon) {
            BlockPos start = cannon.getStartPos();
            Direction direction = cannon.initialOrientation();
            if (start == null || direction == null) return false;
            BlockEntity breech = cannon.presentBlockEntities.get(
                    start.relative(direction.getOpposite()));
            if (breech instanceof QuickfiringBreechBlockEntity quickfiring
                    && quickfiring.getOpenProgress() > 0) {
                return false;
            }
        }
        // Do not gate autocannons on canFire(): its fire-rate check needs the
        // redstone signal we are deciding whether to supply in the first place.
        return true;
    }

    public static boolean isCannonReadyToFire(CannonMountContext mount) {
        if (mount == null) return false;
        if (mount.blockEntity() instanceof CannonMountBlockEntity cbcMount) {
            return isCannonReadyToFire(cbcMount);
        }
        return true;
    }

    private static float getAutoCannonSpeed(AbstractMountedCannonContraption cannon) {
        AutocannonMaterial cann = getAutocannonMaterial(cannon);
        if (cann == null) return 0f;
        var props = cann.properties();

        float speed = props.baseSpeed();
        BlockPos pos = cannon.getStartPos().relative(cannon.initialOrientation());
        int count = 0;

        while (true) {
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (be == null || !(be instanceof IAutocannonBlockEntity || (Mods.CBC_AT.isLoaded() && CBCATCompat.isAutocannonBarrel(be)))) break;

            count++;
            if (count <= props.maxSpeedIncreases())  speed += props.speedIncreasePerBarrel();
            if (count >  props.maxBarrelLength())    break;

            pos = pos.relative(cannon.initialOrientation());
        }

        return speed;
    }

    @Nullable
    private static AutocannonMaterial getAutocannonMaterial(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return null;

        if (isAutoCannon(cannon)) {
            try {
                return ((AutoCannonAccessor) cannon).getMaterial();
            } catch (Throwable failure) {
                LOGGER.debug("Mixin maybe didnt apply?");
                DiagnosticRecorder.warn("mixin_accessor",
                        "autocannon_material", "autocannon_accessor_failed",
                        failure, null, null, "createbigcannons");
                ConflictTraceRecorder.invariant(false, "mixin_accessor",
                        "autocannon_material", "autocannon_accessor_failed",
                        null, null, "createbigcannons");
                return null;
            }
        }

        return Mods.CBC_AT.isLoaded() ? CBCATCompat.getAutocannonMaterial(cannon) : null;
    }


    public static boolean isUp(Level level , Vec3 mountPos){
        BlockEntity blockEntity =  level.getBlockEntity(new BlockPos( (int) mountPos.x, (int) mountPos.y, (int) mountPos.z));
        return isUp(blockEntity);
    }

    public static boolean isUp(BlockEntity blockEntity) {
        if(!(blockEntity instanceof CannonMountBlockEntity cannonMountBlockEntity)) return true;
        if(cannonMountBlockEntity.getContraption() == null) return true;
        return !(cannonMountBlockEntity.getContraption().position().y < blockEntity.getBlockPos().getY());
    }

}
