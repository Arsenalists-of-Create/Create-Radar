package com.happysg.radar.mixin;

import com.happysg.radar.block.controller.kinetic.KineticPlacement;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RotatedPillarKineticBlock.class, remap = false)
public abstract class RotatedPillarKineticBlockPlacementMixin {
    @Redirect(
            method = "getPreferredAxis",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/base/IRotate;"
                            + "hasShaftTowards(Lnet/minecraft/world/level/LevelReader;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;)Z"
            )
    )
    private static boolean createRadar$includePlacementShaftTargets(
            IRotate rotate, LevelReader world, BlockPos pos,
            BlockState state, Direction face
    ) {
        return KineticPlacement.hasShaftOrPlacementHint(
                rotate, world, pos, state, face);
    }
}
