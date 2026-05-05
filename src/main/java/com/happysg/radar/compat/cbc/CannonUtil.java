package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.mixin.AbstractCannonAccessor;
import com.happysg.radar.mixin.AutoCannonAccessor;
import com.happysg.radar.mixin.AutocannonProjectileAccessor;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
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
import com.happysg.radar.compat.stub.IItemHandler;
import com.happysg.radar.compat.cbcwpf.CBCWPFCompat;

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
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.MediumcannonBlock;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.MediumcannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.breech.MediumcannonBreechBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.medium_cannon.material.MediumcannonMaterial;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.RotarycannonBlock;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.RotarycannonBlockEntity;
import riftyboi.cbcmodernwarfare.cannons.rotarycannon.material.RotarycannonMaterial;
import riftyboi.cbcmodernwarfare.forge.cannons.RotarycannonBreechBlockEntity;
import com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption;
import com.dsvv.cbcat.cannon.twin_autocannon.ITwinAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.RifledBarrelBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.IHeavyAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption;
import net.arsenalists.createenergycannons.content.cannons.magnetic.railgun.MountedEnergyCannonContraption;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlock.HORIZONTAL_FACING;

public class CannonUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final BallisticPropertiesComponent AC_FALLBACK = new BallisticPropertiesComponent(-0.025, 0.01, false, 0, 0, 0, 0);

    public static boolean isAutocannonFamily(AbstractMountedCannonContraption cannon) {
        return isAutoCannon(cannon)
                || isRotaryCannon(cannon)
                || isMediumCannon(cannon)
                || isTwinAutocannon(cannon)
                || isHeavyAutocannon(cannon)
                || CBCWPFCompat.isShupapiumAutocannon(cannon);
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
    public static Vec3 getCannonMountOffset(net.minecraft.world.level.Level level, BlockPos pos) {
        return getCannonMountOffset(level.getBlockEntity(pos));
    }

    public static Vec3 getCannonMountOffset(BlockEntity mount) {
        if (mount == null) return Vec3.ZERO;

        if (Mods.CBCMODERNWARFARE.isLoaded() && mount instanceof CompactCannonMountBlockEntity mwMount) {
            if (mwMount.getBlockState().hasProperty(HORIZONTAL_FACING)) {
                Direction dir = mwMount.getBlockState().getValue(HORIZONTAL_FACING);
                return switch (dir) {
                    case EAST -> new Vec3(0, 0,  1);
                    case SOUTH -> new Vec3(-1,0,  0);
                    case WEST -> new Vec3(0, 0, -1);
                    case NORTH -> new Vec3(1, 0,  0);
                    default -> Vec3.ZERO;
                };
            }
        }

        return isUp(mount) ? new Vec3(0, 2, 0) : new Vec3(0, -2, 0);
    }

    public static BallisticPropertiesComponent getAutocannonBallistics(AbstractMountedCannonContraption cannon, net.minecraft.world.level.Level level) {
        if (cannon == null || level == null) return AC_FALLBACK;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {
            BallisticPropertiesComponent bp = CBCWPFCompat.resolveAutocannonBallistics(cannon, level);
            return bp != null ? bp : AC_FALLBACK;
        }

        java.util.function.Function<ItemStack, BallisticPropertiesComponent> fromCBCAmmo = (stack) -> {
            if (stack == null || stack.isEmpty()) return AC_FALLBACK;
            if (!(stack.getItem() instanceof AutocannonAmmoItem ammo)) return BallisticPropertiesComponent.DEFAULT;

            AbstractAutocannonProjectile proj = ammo.getAutocannonProjectile(stack, level);
            if (proj == null) return BallisticPropertiesComponent.DEFAULT;

            return ((AutocannonProjectileAccessor) proj).getBallisticProperties();
        };

        // Use reflection to avoid compile-time dependency on net.minecraftforge.items.IItemHandler
        // which doesn't exist in NeoForge 1.21.1
        for (BlockEntity be : cannon.presentBlockEntities.values()) {
            if (be instanceof AutocannonBreechBlockEntity b) {
                try {
                    Object handler = b.getClass().getMethod("createItemHandler").invoke(b);
                    ItemStack round = (ItemStack) handler.getClass().getMethod("getStackInSlot", int.class).invoke(handler, 0);
                    return fromCBCAmmo.apply(round);
                } catch (Throwable e) {

                    return AC_FALLBACK;
                }
            }
        }

        if (Mods.CBCMODERNWARFARE.isLoaded()) {
            for (BlockEntity be : cannon.presentBlockEntities.values()) {
                if (be instanceof RotarycannonBreechBlockEntity rb) {
                    try {
                        Object handler = rb.getClass().getMethod("createItemHandler").invoke(rb);
                        ItemStack round = (ItemStack) handler.getClass().getMethod("getStackInSlot", int.class).invoke(handler, 0);
                        return fromCBCAmmo.apply(round);
                    } catch (Throwable e) {

                        return AC_FALLBACK;
                    }
                }
            }

            for (BlockEntity be : cannon.presentBlockEntities.values()) {
                if (be instanceof MediumcannonBreechBlockEntity mb) {
                    ItemStack round = mb.getInputBuffer();
                    return fromCBCAmmo.apply(round);
                }
            }

        }

        return AC_FALLBACK;
    }

    public static BallisticPropertiesComponent getBallistics(AbstractMountedCannonContraption cannon, net.minecraft.server.level.ServerLevel level) {
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

    public static float getRotarySpeed( AbstractMountedCannonContraption contraptionEntity) {
        if(!Mods.CBCMODERNWARFARE.isLoaded()) return 0f;
        if(contraptionEntity == null) return 0f;
        Map<BlockPos, BlockEntity> presentBlockEntities = contraptionEntity.presentBlockEntities;

        if(presentBlockEntities.isEmpty()) return 0f;
        int barrelCount = 0;
        RotarycannonMaterial material = null;
        for (BlockEntity entity : presentBlockEntities.values()) {
            if(entity instanceof RotarycannonBlockEntity blockEntity && !(entity instanceof RotarycannonBreechBlockEntity)){
                barrelCount++;
                if(material == null){
                    if (blockEntity.getBlockState().getBlock() instanceof RotarycannonBlock rb)
                        material = rb.getRotarycannonMaterial();
                    else material = null;
                }
            }
        }
        if(material == null) return 0;
        float baseSpeed = material.properties().baseSpeed();
        int speedIncrease = Math.min(barrelCount, material.properties().maxSpeedIncreases());
        return baseSpeed+speedIncrease*material.properties().speedIncreasePerBarrel();
    }

    public static float getMediumCannonSpeed(AbstractMountedCannonContraption contraptionEntity) {
        if(!Mods.CBCMODERNWARFARE.isLoaded()) return 0f;
        if(contraptionEntity == null) return 0f;
        Map<BlockPos, BlockEntity> presentBlockEntities = contraptionEntity.presentBlockEntities;
        if(presentBlockEntities.isEmpty()) return 0f;
        int barrelCount = 0;
        MediumcannonMaterial material = null;
        List<BlockEntity> blocks = presentBlockEntities.values().stream().toList();
        for (BlockEntity entity : blocks){
            if(entity instanceof MediumcannonBlockEntity blockEntity && !(entity instanceof MediumcannonBreechBlockEntity)){
                barrelCount++;
                if(material == null){
                    if (blockEntity.getBlockState().getBlock() instanceof MediumcannonBlock mb)
                        material = mb.getMediumcannonMaterial();
                    else material = null;
                }
            }
        }
        if(material == null) return 0;
        float baseSpeed = material.properties().baseSpeed();
        int speedIncrease = Math.min(barrelCount, material.properties().maxSpeedIncreases());
        return baseSpeed+speedIncrease*material.properties().speedIncreasePerBarrel();
    }

    public static int getBigCannonSpeed(net.minecraft.server.level.ServerLevel level, AbstractMountedCannonContraption cannon ,PitchOrientedContraptionEntity contraptionEntity) {
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

    public static float getInitialVelocity(AbstractMountedCannonContraption cannon, net.minecraft.server.level.ServerLevel level) {

        if (cannon == null) return 0f;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {

            return CBCWPFCompat.resolveShupapiumMuzzleSpeed(cannon);
        }

        if (isEnergyCannon(cannon)) {
            float velocity = ((MountedEnergyCannonContraption) cannon).getMuzzleVelocity(level);

            return velocity;
        }

        if (isBigCannon(cannon)) {
            PitchOrientedContraptionEntity _poce = cannon.entity instanceof PitchOrientedContraptionEntity _p ? _p : null;

            return getBigCannonSpeed(level, cannon, _poce);
        } else if (isAutoCannon(cannon)) {

            return getAutoCannonSpeed(cannon);
        }
        else if(isRotaryCannon(cannon)){

            return getRotarySpeed(cannon);
        }
        else if(isMediumCannon(cannon)){

            return getMediumCannonSpeed(cannon);
        } else if(isTwinAutocannon(cannon)){

            return getAutoCannonSpeed(cannon);
        } else if(isHeavyAutocannon(cannon)){

            return getAutoCannonSpeed(cannon);
        }

        return 0;
    }

    public static int getAutocannonLifetimeTicks(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return 100;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {
            int t = CBCWPFCompat.resolveLifetimeTicks(cannon);
            return t > 0 ? t : 100;
        }

        // Only CBC autocannon contraptions have this accessor reliably
        if (!(isAutoCannon(cannon) || isTwinAutocannon(cannon) || isHeavyAutocannon(cannon))) {
            return 100;
        }

        try {
            AutocannonMaterial mat = ((AutoCannonAccessor) cannon).getMaterial();
            if (mat != null) {
                int t = mat.properties().projectileLifetime();
                if (t > 0) return t;
            }
        } catch (Throwable ignored) {

        }

        return 100;
    }

    public static double getMaxProjectileRangeBlocks(AbstractMountedCannonContraption cannon, net.minecraft.server.level.ServerLevel level) {
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

    public static double getProjectileGravity(AbstractMountedCannonContraption cannon, net.minecraft.server.level.ServerLevel level) {
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

    public static double getProjectileDrag(AbstractMountedCannonContraption cannon, net.minecraft.server.level.ServerLevel level) {
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
    public static boolean isRotaryCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CBCMODERNWARFARE.isLoaded()) return false;
        return cannonContraption instanceof MountedRotarycannonContraption;
    }
    public static boolean isMediumCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CBCMODERNWARFARE.isLoaded()) return false;
        return cannonContraption instanceof MountedMediumcannonContraption;
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
            return true;
        }

        // Regular cannons are always ready
        return true;
    }

    private static float getAutoCannonSpeed(AbstractMountedCannonContraption cannon) {
        AutocannonMaterial cann = ((AutoCannonAccessor) cannon).getMaterial();
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


    public static boolean isUp(net.minecraft.world.level.Level level , Vec3 mountPos){
        BlockEntity blockEntity =  level.getBlockEntity(new BlockPos( (int) mountPos.x, (int) mountPos.y, (int) mountPos.z));
        return isUp(blockEntity);
    }

    public static boolean isUp(BlockEntity blockEntity) {
        if(!(blockEntity instanceof CannonMountBlockEntity cannonMountBlockEntity)) return true;
        if(cannonMountBlockEntity.getContraption() == null) return true;
        var contraptionEntity = cannonMountBlockEntity.getContraption();
        return !(contraptionEntity.position().y < blockEntity.getBlockPos().getY());
    }

}
