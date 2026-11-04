package com.happysg.radar.block.controller.kinetic;

import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class SwivelReorientationState {
    private boolean hadTrackingSwivel;
    private boolean reorienting;
    private int stableTicks;

    public void observe(KineticMountAdapterResolution resolution, boolean running) {
        if (!running) {
            hadTrackingSwivel = false;
            reorienting = false;
            stableTicks = 0;
            return;
        }

        if (resolution == null) {
            return;
        }

        if (resolution.isStructuralSelection()) {
            hadTrackingSwivel = true;
        }

        if (!reorienting) {
            return;
        }

        KineticMountAdapter adapter = resolution.adapter();

        if (resolution.hasAdapter() && adapter != null && adapter.isValid() && adapter.isAssembled() && adapter.isLocked() && adapter.frameIdentity() != null) {
            if (++stableTicks >= 4) {
                reorienting = false;
                stableTicks = 0;
            }
        } else {
            stableTicks = 0;
        }
    }

    public void onNeighborChanged(Level level, BlockPos controller, BlockPos changed) {
        if (!hadTrackingSwivel || level == null || level.isClientSide() || !level.hasChunkAt(changed) || !isAdjacent(controller, changed)) {
            return;
        }

        if (level.getBlockState(changed).getBlock() instanceof SwivelBearingBlock) {
            reorienting = true;
            stableTicks = 0;
        }
    }

    public boolean shouldPreserve(Level level, BlockPos controller) {
        if (!hadTrackingSwivel || !reorienting || level == null) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = controller.relative(direction);
            if (level.hasChunkAt(neighbor) && level.getBlockState(neighbor).getBlock() instanceof SwivelBearingBlock) {
                return true;
            }
        }

        return false;
    }

    private static boolean isAdjacent(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) == 1;
    }
}