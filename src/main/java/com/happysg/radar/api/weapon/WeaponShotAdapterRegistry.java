package com.happysg.radar.api.weapon;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WeaponShotAdapterRegistry {
    private static final Map<String, WeaponShotAdapter> ADAPTERS = new LinkedHashMap<>();
    private WeaponShotAdapterRegistry() {}

    public static synchronized void register(String id, WeaponShotAdapter adapter) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Weapon shot adapter id must not be blank");
        }

        ADAPTERS.put(id, Objects.requireNonNull(adapter, "adapter"));
    }

    @Nullable
    public static WeaponShotProfile resolve(WeaponShotContext context) {
        Objects.requireNonNull(context, "context");
        List<WeaponShotAdapter> adapters;

        synchronized (WeaponShotAdapterRegistry.class) {
            adapters = List.copyOf(ADAPTERS.values());
        }

        for (WeaponShotAdapter adapter : adapters) {
            WeaponShotProfile profile = adapter.resolve(context);

            if (profile != null) {
                return profile;
            }
        }

        return null;
    }
}