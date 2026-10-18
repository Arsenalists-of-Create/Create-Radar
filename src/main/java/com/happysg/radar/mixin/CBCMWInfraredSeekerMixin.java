package com.happysg.radar.mixin;

import com.happysg.radar.compat.cbcmw.CBCMWGuidanceCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(
        targets = "riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.infrared_homing.InfraredSeekerGuidanceBlockEntity",
        remap = false
)
public abstract class CBCMWInfraredSeekerMixin {
    @ModifyExpressionValue(
            method = "tickGuidance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", remap = true),
            remap = false
    )

    private List<Entity> createRadar$preferDesignatedTarget(
            List<Entity> eligible,
            Level level,
            BlockPos position,
            BlockState state,
            BlockEntity blockEntity,
            OrientedContraptionEntity owner
    ) {
        return CBCMWGuidanceCompat.restrictSeekerCandidates(eligible, owner, level);
    }
}