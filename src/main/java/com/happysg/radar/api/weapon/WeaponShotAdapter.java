package com.happysg.radar.api.weapon;

import javax.annotation.Nullable;

/**
 * Optional weapon integration hook used by Create: Radar's firing controller.
 * Returning {@code null} leaves the weapon to Radar's built-in cannon handling.
 */
@FunctionalInterface
public interface WeaponShotAdapter {
    @Nullable
    WeaponShotProfile resolve(WeaponShotContext context);
}
