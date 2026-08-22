package com.happysg.radar.api.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for third-party weapon mount compatibility.
 */
public final class RadarMountRegistry {

    private static final List<RadarMountProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private RadarMountRegistry() {
    }

    /**
     * Registers a mount provider.
     */
    public static void register(RadarMountProvider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * Finds the first registered adapter that recognizes the block at this position.
     */
    @Nullable
    public static RadarMountAdapter find(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return null;
        }

        for (RadarMountProvider provider : PROVIDERS) {
            RadarMountAdapter adapter = provider.find(level, pos);

            if (adapter != null && adapter.isValid()) {
                return adapter;
            }
        }

        return null;
    }
}