package com.happysg.radar.mixin;

import com.happysg.radar.item.radarproxfuze.AdvancedProximityFuze;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakAutocannonProjectile;

@Mixin(FlakAutocannonProjectile.class)
public class FlakAutocannonProjectileMixin {

    @Inject(method = "setFuze", at = @At("HEAD"), remap = false)
    private void createRadar$assignAdvancedProximityFuzeNetwork(ItemStack stack, CallbackInfo ci) {
        AdvancedProximityFuze.assignCurrentLaunchContext(stack);
    }
}
