package com.happysg.radar.api.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for third-party weapon endpoints.
 */
public final class RadarWeaponRegistry {
    private static final List<RadarWeaponProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private RadarWeaponRegistry() {}

    public static void register(RadarWeaponProvider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    @Nullable
    public static RadarWeaponAdapter find(ServerLevel level, BlockPos mountPos) {
        if (level == null || mountPos == null || !level.hasChunkAt(mountPos)) {
            return null;
        }

        for (RadarWeaponProvider provider : PROVIDERS) {
            RadarWeaponAdapter adapter = provider.find(level, mountPos);

            if (adapter != null && adapter.isValid()) {
                return adapter;
            }
        }

        return null;
    }
}