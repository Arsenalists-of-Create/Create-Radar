package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RadarDataLinkRegistry {
    private static final List<RadarDataLinkProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private RadarDataLinkRegistry() {}

    public static void register(RadarDataLinkProvider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    @Nullable
    public static RadarDataLinkEndpoint find(Level level, BlockPos clickedPos) {
        if (level == null || clickedPos == null || !level.getChunkSource().hasChunk(clickedPos.getX() >> 4, clickedPos.getZ() >> 4)) return null;
        for (RadarDataLinkProvider provider : PROVIDERS) {
            RadarDataLinkEndpoint endpoint = provider.find(level, clickedPos);
            if (endpoint != null) return endpoint;
        }
        return null;
    }
}
