package com.happysg.radar.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = RotationPropagator.class, priority = 500)
public abstract class RotationPropagatorMixin {
    @Inject(method = "getPotentialNeighbourLocations", at = @At("RETURN"), cancellable = true, remap = false)
    private static void create_radar$makePotentialListMutable(com.simibubi.create.content.kinetics.base.KineticBlockEntity be, CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> list = cir.getReturnValue();
        if (list == null) return;
        try {
            list.addAll(List.of()); // Check if it's immutable
        } catch (UnsupportedOperationException e) {
            cir.setReturnValue(new ArrayList<>(list));
        }
    }
}
