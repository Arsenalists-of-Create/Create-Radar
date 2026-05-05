package com.happysg.radar.mixin;

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarContraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MechanicalBearingBlockEntity.class)
public class MechanicalBearingMixin {

    @Redirect(
            method = "assemble",
            at = @At(value = "NEW", target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;"),
            remap = false
    )
    private BearingContraption redirectBearingContraption(boolean isWindmill, Direction facing) {
        if ((Object) this instanceof RadarBearingBlockEntity) {
            return new RadarContraption(facing);
        }
        return new BearingContraption(isWindmill, facing);
    }
}
