package com.happysg.radar.mixin;

import com.happysg.radar.compat.cbcwpf.IShupapiumACContraptionAccess;
import net.ato.shupapium.utils.actypes.ShupapiumACProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;

@Pseudo
@Mixin(targets = "net.ato.shupapium.utils.MountedShupapiumACContraption", remap = false)
public interface ShupapiumACContraptionAccessor extends IShupapiumACContraptionAccess {

    @Override
    @Accessor("cannonMaterial")
    AutocannonMaterial getShupapiumMaterial();

    @Override
    @Accessor("profile")
    ShupapiumACProfile getProfile();
}
