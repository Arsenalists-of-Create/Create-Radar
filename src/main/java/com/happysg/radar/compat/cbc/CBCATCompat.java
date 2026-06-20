package com.happysg.radar.compat.cbc;

import com.dsvv.cbcat.cannon.heavy_autocannon.HeavyAutocannonBlock;
import com.dsvv.cbcat.cannon.heavy_autocannon.IHeavyAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption;
import com.dsvv.cbcat.cannon.heavy_autocannon.munitions.HeavyAutocannonAmmoItem;
import com.dsvv.cbcat.cannon.twin_autocannon.ITwinAutocannonBlockEntity;
import com.dsvv.cbcat.cannon.twin_autocannon.TwinAutocannonBlock;
import com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

import javax.annotation.Nullable;

final class CBCATCompat {
    private CBCATCompat() {
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
}
