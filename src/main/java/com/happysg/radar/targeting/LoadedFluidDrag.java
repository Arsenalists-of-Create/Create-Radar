
package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;

public final class LoadedFluidDrag {
    private static final Unavailable UNAVAILABLE = new Unavailable();

    private LoadedFluidDrag() {}

    public static double sample(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return 0.0;
        }

        if (!(level instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().isSameThread()) {
            throw UNAVAILABLE;
        }

        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);

        if (chunk == null) {
            throw UNAVAILABLE;
        }

        return FluidDragHandler.getFluidDrag(chunk.getFluidState(pos));
    }

    public static boolean isImmersed(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return false;
        }

        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            throw UNAVAILABLE;
        }

        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);

        if (chunk == null) {
            throw UNAVAILABLE;
        }

        return !chunk.getFluidState(pos).isEmpty();
    }

    public static final class Unavailable extends RuntimeException {
        private Unavailable() {
            super(null, null, false, false);
        }
    }
}
