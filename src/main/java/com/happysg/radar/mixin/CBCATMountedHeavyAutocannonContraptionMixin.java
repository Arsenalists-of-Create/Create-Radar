package com.happysg.radar.mixin;

import com.happysg.radar.item.radarproxfuze.AdvancedProximityFuze;
import com.happysg.radar.debug.ConflictTraceRecorder;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

@Pseudo
@Mixin(targets = "com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption", remap = false)
public class CBCATMountedHeavyAutocannonContraptionMixin {

    @Unique
    private boolean createRadar$hasFuzeLaunchContext;

    @Inject(method = "fireShot", at = @At("HEAD"), remap = false)
    private void createRadar$pushAdvancedProximityFuzeContext(ServerLevel level, PitchOrientedContraptionEntity contraptionEntity, CallbackInfo ci) {
        ConflictTraceRecorder.heartbeat("mixin_callback",
                "cbc_at_heavy_fire_head", level, true);
        this.createRadar$hasFuzeLaunchContext = AdvancedProximityFuze.pushLaunchContext(level, contraptionEntity);
    }

    @Inject(method = "fireShot", at = @At("RETURN"), remap = false)
    private void createRadar$popAdvancedProximityFuzeContext(ServerLevel level, PitchOrientedContraptionEntity contraptionEntity, CallbackInfo ci) {
        ConflictTraceRecorder.heartbeat("mixin_callback",
                "cbc_at_heavy_fire_return", level, true);
        if (this.createRadar$hasFuzeLaunchContext) {
            AdvancedProximityFuze.popLaunchContext();
            this.createRadar$hasFuzeLaunchContext = false;
        }
    }
}
