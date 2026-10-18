package com.happysg.radar.mixin;

import com.happysg.radar.compat.cbcmw.CBCMWGuidanceCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMunitionsLauncherContraption;

@Pseudo
@Mixin(targets = "riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMunitionsLauncherContraption", remap = false)
public abstract class CBCMWLauncherDesignationMixin {
    @ModifyArg(
            method = "fireShot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = false),
            index = 0,
            remap = false
    )

    private Entity createRadar$attachTarget(Entity spawned) {
        CBCMWGuidanceCompat.attachToSpawn((MountedMunitionsLauncherContraption) (Object) this, spawned);
        return spawned;
    }
}