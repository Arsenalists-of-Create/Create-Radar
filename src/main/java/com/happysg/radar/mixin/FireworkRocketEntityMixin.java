package com.happysg.radar.mixin;

import com.happysg.radar.chaff.ChaffManager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @Shadow(remap = false)
    private int life;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void createRadar$captureChaffLaunch(CallbackInfo ci) {
        if (life == 0) {
            ChaffManager.captureLaunch((FireworkRocketEntity) (Object) this);
        }
    }

    @Inject(method = "explode", at = @At("HEAD"), remap = false)
    private void createRadar$applyChaff(CallbackInfo ci) {
        ChaffManager.onFireworkExplode((FireworkRocketEntity) (Object) this);
    }
}
