package com.happysg.radar.api.weapon;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class WeaponShotAdapterRegistry {
    private static final Map<String, WeaponShotAdapter> ADAPTERS = new LinkedHashMap<>();

    private WeaponShotAdapterRegistry() {
    }

    /**
     * Registers or replaces an adapter while preserving deterministic insertion order.
     */
    public static synchronized void register(String id, WeaponShotAdapter adapter) {
        if (id == null || id.isBlank() || adapter == null) {
            throw new IllegalArgumentException("Weapon adapter id and adapter must be non-null");
        }
        ADAPTERS.put(id, adapter);
    }

    @Nullable
    public static synchronized WeaponShotProfile resolve(WeaponShotContext context) {
        if (context == null) {
            return null;
        }
        for (WeaponShotAdapter adapter : ADAPTERS.values()) {
            WeaponShotProfile profile = adapter.resolve(context);
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }
}
