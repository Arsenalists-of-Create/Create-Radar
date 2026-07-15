package com.happysg.radar.chaff;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/** Registry for optional mods that expose entity-backed radar locks to chaff. */
public final class ChaffLockRegistry {
    private static final Map<EntityType<?>, ChaffLockAdapter<?>> ADAPTERS = new IdentityHashMap<>();

    private ChaffLockRegistry() {
    }

    public static synchronized <T extends Entity> void register(EntityType<T> entityType,
                                                                 ChaffLockAdapter<? super T> adapter) {
        if (entityType == null || adapter == null) {
            throw new IllegalArgumentException("Entity type and chaff adapter must be non-null");
        }
        ADAPTERS.put(entityType, adapter);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static synchronized ChaffLockAdapter<Entity> find(Entity entity) {
        return (ChaffLockAdapter<Entity>) ADAPTERS.get(entity.getType());
    }
}
