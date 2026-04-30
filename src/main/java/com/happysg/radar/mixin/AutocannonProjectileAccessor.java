package com.happysg.radar.mixin;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

@Mixin(AbstractCannonProjectile.class)
public interface AutocannonProjectileAccessor {
    @Invoker(value = "getBallisticProperties", remap = false)
    BallisticPropertiesComponent getBallisticProperties();
}