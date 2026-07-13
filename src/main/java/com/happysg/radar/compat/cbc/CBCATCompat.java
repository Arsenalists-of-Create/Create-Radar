package com.happysg.radar.compat.cbc;

import com.dsvv.cbcat.base.CustomPropellantContext;
import com.dsvv.cbcat.base.IBigCannonBlockPhysics;
import com.dsvv.cbcat.cannon.heavy_autocannon.HeavyAutocannonBlock;
import com.dsvv.cbcat.cannon.heavy_autocannon.IHeavyAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption;
import com.dsvv.cbcat.cannon.heavy_autocannon.munitions.HeavyAutocannonAmmoItem;
import com.dsvv.cbcat.cannon.twin_autocannon.ITwinAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.twin_autocannon.TwinAutocannonBlock;
import com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption;
import com.dsvv.cbcat.cartridge.IProjectileCartridgeBlock;
import com.dsvv.cbcat.config.CBCATConfigs;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class CBCATCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean BIG_CANNON_PHYSICS_WARNING_LOGGED = new AtomicBoolean();

    private CBCATCompat() {
    }

    record BigCannonPhysicsResult(
            float speed,
            @Nullable AbstractBigCannonProjectile projectile,
            @Nullable BlockPos projectileLocalPos,
            float projectileAddedPower
    ) {
    }

    static boolean isHeavyAutocannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedHeavyAutocannonContraption;
    }

    static boolean isTwinAutocannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedTwinAutocannonContraption;
    }

    static boolean isAutocannonBarrel(BlockEntity blockEntity) {
        return blockEntity instanceof ITwinAutocannonBlockEntity
                || blockEntity instanceof IHeavyAutocannonBlockEntity;
    }

    @Nullable
    static AbstractCannonProjectile createAutocannonProjectile(ItemStack stack, Level level) {
        if (stack.getItem() instanceof HeavyAutocannonAmmoItem item) {
            return item.getAutocannonProjectile(stack, level);
        }
        return null;
    }

    @Nullable
    static AutocannonMaterial getAutocannonMaterial(AbstractMountedCannonContraption cannon) {
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

    /**
     * Rebuilds the custom launch context installed by CBC:AT's
     * {@code MountedBigCannonContraptionMixin}. A non-null result means the
     * physics rework is enabled and its velocity must replace CBC's vanilla
     * summed charge power.
     */
    @Nullable
    static BigCannonPhysicsResult resolveBigCannonPhysics(AbstractMountedCannonContraption cannon, Level level) {
        try {
            if (cannon == null || level == null
                    || !(cannon instanceof MountedBigCannonContraption)
                    || CBCATConfigs.SERVER == null
                    || CBCATConfigs.SERVER.bigCannons == null
                    || (Boolean) CBCATConfigs.SERVER.bigCannons.disablePhysicRework.get()) {
                return null;
            }

            Direction direction = cannon.initialOrientation();
            BlockPos currentPos = cannon.getStartPos();
            if (direction == null || currentPos == null || cannon.presentBlockEntities == null) {
                return null;
            }

            CustomPropellantContext context = new CustomPropellantContext();
            boolean allowsMultipleCharges = true;
            List<StructureTemplate.StructureBlockInfo> projectileBlocks = new ArrayList<>();
            AbstractBigCannonProjectile projectile = null;
            BlockPos projectileLocalPos = null;
            float projectileAddedPower = 0.0F;

            while (cannon.presentBlockEntities.get(currentPos) instanceof IBigCannonBlockEntity cannonBlockEntity) {
                BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
                StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();
                StructureTemplate.StructureBlockInfo cannonBlockInfo = cannon.getBlocks().get(currentPos);
                if (containedBlockInfo == null || cannonBlockInfo == null) {
                    break;
                }

                Block containedBlock = containedBlockInfo.state().getBlock();
                if (!containedBlockInfo.state().isAir()) {
                    if (containedBlock instanceof IProjectileCartridgeBlock cartridgeBlock) {
                        allowsMultipleCharges = cartridgeBlock.allowsMultipleCharges();
                        if (!allowsMultipleCharges && context.chargesUsed > 0.0F) {
                            context.isDoomedToFail();
                        }

                        context.addPropellant(cartridgeBlock, containedBlockInfo, direction);
                        projectileBlocks.add(containedBlockInfo);
                        projectile = cartridgeBlock.getProjectile(level, projectileBlocks);
                        projectileLocalPos = currentPos.immutable();
                        if (projectile != null) {
                            projectileAddedPower = projectile.addedChargePower();
                            context.chargesUsed += projectileAddedPower;
                        }
                    } else if (containedBlock instanceof BigCannonPropellantBlock propellantBlock
                            && !(containedBlock instanceof ProjectileBlock<?>)) {
                        context.addPropellant(propellantBlock, containedBlockInfo, direction);
                        if (!allowsMultipleCharges) {
                            context.isDoomedToFail();
                        }
                    } else if (containedBlock instanceof ProjectileBlock<?> projectileBlock && projectile == null) {
                        projectileBlocks.add(containedBlockInfo);
                        projectile = projectileBlock.getProjectile(level, projectileBlocks);
                        projectileLocalPos = currentPos.immutable();
                        if (projectile != null) {
                            projectileAddedPower = projectile.addedChargePower();
                            context.chargesUsed += projectileAddedPower;
                        }
                    }
                }

                Block cannonBlock = cannonBlockInfo.state().getBlock();
                if (cannonBlock instanceof IBigCannonBlockPhysics physics) {
                    context.addBarrel(physics);
                }

                currentPos = currentPos.relative(direction);
            }

            return new BigCannonPhysicsResult(
                    context.getVelocity(),
                    projectile,
                    projectileLocalPos,
                    projectileAddedPower
            );
        } catch (Throwable throwable) {
            if (BIG_CANNON_PHYSICS_WARNING_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Could not resolve CBC:AT big cannon physics; falling back to CBC charge power", throwable);
            }
            return null;
        }
    }
}
