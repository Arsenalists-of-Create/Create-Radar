package com.happysg.radar.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;

@Mixin(value = {
        MountedAutocannonContraption.class
}, remap = false)
public interface AutoCannonAccessor {
    @Accessor("cannonMaterial")
    AutocannonMaterial getMaterial();
}