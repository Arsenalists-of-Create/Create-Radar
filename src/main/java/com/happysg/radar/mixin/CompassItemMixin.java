package com.happysg.radar.mixin;

import com.happysg.radar.item.RadarCompassLink;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompassItem.class)
public abstract class CompassItemMixin extends Item {
    public CompassItemMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "inventoryTick", at = @At("HEAD"), remap = false)
    private void createRadar$refreshRadarTarget(ItemStack stack, Level level, Entity entity, int slot,
                                                boolean selected, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            RadarCompassLink.refreshLodestoneTarget(stack, serverLevel);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (!slotChanged && RadarCompassLink.matchesIgnoringLiveTarget(oldStack, newStack)) {
            return false;
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }
}
