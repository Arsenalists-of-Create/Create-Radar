package com.happysg.radar.mixin;

import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock", remap = false)
public abstract class SableCannonMountAssemblyMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        SableDataLinkRelocation.captureMountMove(originLevel, resultingLevel, oldPos, newPos);
    }
}