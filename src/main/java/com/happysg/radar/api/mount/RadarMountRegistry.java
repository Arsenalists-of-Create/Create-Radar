package com.happysg.radar.api.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <p>Third-party mods can register {@link RadarMountProvider}s to allow
 * Create Radar to discover and control their weapon mounts.</p>
 */
public final class RadarMountRegistry {

    private static final List<RadarMountProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private RadarMountRegistry() {
    }

    /**
     * Registers a mount provider.
     *
     * <p>Providers should normally be registered once during mod
     * initialization.</p>
     *
     * @param provider provider to register; must not be {@code null}
     * @throws NullPointerException if {@code provider} is {@code null}
     */
    public static void register(RadarMountProvider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * Finds the first registered adapter that recognizes the mount at the
     * supplied position.
     *
     * @param level level containing the mount
     * @param pos position to inspect
     * @return the first valid adapter, or {@code null} if no registered
     * provider recognizes the mount or the chunk is not loaded
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