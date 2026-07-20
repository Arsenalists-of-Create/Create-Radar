package com.happysg.radar.chaff;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Optional integration hook for entity-backed radar locks that can be broken by chaff.
 * Tracked entities do not need to implement a Create Radar interface themselves.
 */
public interface ChaffLockAdapter<T extends Entity> {
    @Nullable
    String getTargetId(T entity);

    void applySuppression(T entity, String targetId, long untilTick);

    /** Permanently clears this lock until its owning system explicitly acquires it again. */
    default void breakLock(T entity, String targetId) {
        applySuppression(entity, targetId, Long.MAX_VALUE);
    }
}
