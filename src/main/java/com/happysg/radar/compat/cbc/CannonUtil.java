package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.mixin.AbstractCannonAccessor;
import com.happysg.radar.mixin.AutoCannonAccessor;
import com.happysg.radar.mixin.AutocannonProjectileAccessor;
import com.mojang.logging.LogUtils;
import com.dsvv.cbcat.cannon.heavy_autocannon.HeavyAutocannonBlock;
import com.dsvv.cbcat.cannon.heavy_autocannon.IHeavyAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption;
import com.dsvv.cbcat.cannon.twin_autocannon.ITwinAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.twin_autocannon.TwinAutocannonBlock;
import com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.arsenalists.createenergycannons.content.cannons.magnetic.railgun.MountedEnergyCannonContraption;
import net.minecraft.core.BlockPos;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;

import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;


import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class CannonUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final BallisticPropertiesComponent AC_FALLBACK = new BallisticPropertiesComponent(-0.025, 0.01, false, 0, 0, 0, 0);

    public static boolean isAutocannonFamily(AbstractMountedCannonContraption cannon) {
        return isAutoCannon(cannon)
                || isTwinAutocannon(cannon)
                || isHeavyAutocannon(cannon);
//                || isRotaryCannon(cannon)
//                || isMediumCannon(cannon);
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

//        if (Mods.CBCMODERNWARFARE.isLoaded() && mount instanceof CompactCannonMountBlockEntity mwMount) {
//            if (mwMount.getBlockState().hasProperty(HORIZONTAL_FACING)) {
//                Direction dir = mwMount.getBlockState().getValue(HORIZONTAL_FACING);
//                return switch (dir) {
//                    case EAST -> new Vec3(0, 0,  1);
//                    case SOUTH -> new Vec3(-1,0,  0);
//                    case WEST -> new Vec3(0, 0, -1);
//                    case NORTH -> new Vec3(1, 0,  0);
//                    default -> Vec3.ZERO;
//                };
//            }
//        }

        return isUp(mount) ? new Vec3(0, 2, 0) : new Vec3(0, -2, 0);
    }

    public static BallisticPropertiesComponent getAutocannonBallistics(AbstractMountedCannonContraption cannon, Level level) {

        return AC_FALLBACK;
    }

    public static BallisticPropertiesComponent getBallistics(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return BallisticPropertiesComponent.DEFAULT;

        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level);
        }

        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;

            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();
            Block block = containedBlockInfo.state().getBlock();

            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                try {
                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    BallisticPropertiesComponent bp = (BallisticPropertiesComponent) method.invoke(projectile);
                    return bp != null ? bp : BallisticPropertiesComponent.DEFAULT;
                } catch (Throwable ignored) {
                    return BallisticPropertiesComponent.DEFAULT;
                }
            }
        }

        return BallisticPropertiesComponent.DEFAULT;
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

    public static int getBigCannonSpeed(ServerLevel level, AbstractMountedCannonContraption cannon ,PitchOrientedContraptionEntity contraptionEntity) {
        if(contraptionEntity == null) return 0;

        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        int speeed = 0;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;
            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof BigCannonPropellantBlock propellantBlock) {
                speeed += (int) propellantBlock.getChargePower(containedBlockInfo);
            } else if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                speeed += (int) projectile.addedChargePower();
            }
        }
        return speeed;
    }

    public static float getInitialVelocity(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null) return 0f;



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
                if (t > 0) return t;
            }
        } catch (Throwable ignored) {
            LOGGER.debug("Mixin maybe didnt apply?");
        }

        return 100;
    }

    public static double getMaxProjectileRangeBlocks(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return 0;

        double speed = getInitialVelocity(cannon, level);
        if (speed <= 0) return 0;

        // lifetime
        int lifeTicks = getAutocannonLifetimeTicks(cannon);
        if (lifeTicks <= 0) return 0;

        if (isAutocannonFamily(cannon)) {
            BallisticPropertiesComponent bp = getAutocannonBallistics(cannon, level);

            if (bp.isQuadraticDrag()) {
                return speed * lifeTicks; // generous upper bound
            }

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
        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;
            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                BallisticPropertiesComponent ballisticProperties;
                try {

                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    ballisticProperties = (BallisticPropertiesComponent) method.invoke(projectile);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                         ClassCastException e) {
                    return 0.05;
                }
                return ballisticProperties.gravity();
            }
        }
        return 0.05;
    }

    public static double getProjectileDrag(AbstractMountedCannonContraption cannon, ServerLevel level) {
        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        double drag = 0.01;

        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level).drag();
        }

        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;

            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                try {
                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    BallisticPropertiesComponent bp = (BallisticPropertiesComponent) method.invoke(projectile);
                    if (bp != null) drag = bp.drag();
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
                    return drag;
                }
            }
        }
        return drag;
    }

    public static boolean isHeavyAutocannon(AbstractMountedCannonContraption cannon) {
        if(!Mods.CBC_AT.isLoaded()) return false;
        return cannon instanceof MountedHeavyAutocannonContraption;
    }

    public static boolean isTwinAutocannon(AbstractMountedCannonContraption cannon) {
        if(!Mods.CBC_AT.isLoaded()) return false;
        return cannon instanceof MountedTwinAutocannonContraption;
    }

    public static boolean isBigCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedBigCannonContraption;
    }

    public static boolean isAutoCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedAutocannonContraption;
    }
//    public static boolean isRotaryCannon(AbstractMountedCannonContraption cannonContraption){
//        if(!Mods.CBCMODERNWARFARE.isLoaded()) return false;
//        return cannonContraption instanceof MountedRotarycannonContraption;
//    }
//    public static boolean isMediumCannon(AbstractMountedCannonContraption cannonContraption){
//        if(!Mods.CBCMODERNWARFARE.isLoaded()) return false;
//        return cannonContraption instanceof MountedMediumcannonContraption;
//    }

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

        // Regular cannons are always ready
        return true;
    }

    private static float getAutoCannonSpeed(AbstractMountedCannonContraption cannon) {
        AutocannonMaterial cann = getAutocannonMaterial(cannon);
        if (cann == null) return 0f;
        var props = cann.properties();

        Predicate<BlockEntity> isBarrel =
                e -> e instanceof IAutocannonBlockEntity
                        || e instanceof ITwinAutocannonBlockEntity
                        || e instanceof IHeavyAutocannonBlockEntity;

        float speed = props.baseSpeed();
        BlockPos pos = cannon.getStartPos().relative(cannon.initialOrientation());
        int count = 0;

        while (true) {
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (be == null || !isBarrel.test(be)) break;

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
            } catch (Throwable ignored) {
                LOGGER.debug("Mixin maybe didnt apply?");
                return null;
            }
        }

        if (!Mods.CBC_AT.isLoaded()) return null;

        for (BlockEntity blockEntity : cannon.presentBlockEntities.values()) {
            Block block = blockEntity.getBlockState().getBlock();
            if (block instanceof TwinAutocannonBlock twinAutocannonBlock) {
                return twinAutocannonBlock.getAutocannonMaterial();
            }
            if (block instanceof HeavyAutocannonBlock heavyAutocannonBlock) {
                return heavyAutocannonBlock.getAutocannonMaterial();
            }
        }

        return null;
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
