package com.happysg.radar.mixin;

import com.happysg.radar.item.RadarCompassLink;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.navigation_targets.CompassNavigationTarget", remap = false)
public class SimulatedCompassNavigationTargetMixin {
    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void createRadar$resolveRadarTarget(NavTableBlockEntity navigationTable, ItemStack stack,
                                                CallbackInfoReturnable<Vec3> cir) {
        if (RadarCompassLink.isLinked(stack)) {
            cir.setReturnValue(RadarCompassLink.resolveNavigationTarget(navigationTable.getLevel(), stack));
        }
    }
}
