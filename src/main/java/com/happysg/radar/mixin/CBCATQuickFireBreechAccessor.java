package com.happysg.radar.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.dsvv.cbcat.cannon.heavy_autocannon.qf_breech.HeavyAutocannonQuickFireBreechBlockEntity", remap = false)
public interface CBCATQuickFireBreechAccessor {
    @Accessor(value = "cartridge", remap = false)
    ItemStack createRadar$getCartridge();
}
