package com.happysg.radar.api.weapon;

@FunctionalInterface
public interface WeaponFirePreparation {
    WeaponFirePreparation READY = context -> true;

    /**
     * Runs immediately before Radar powers the fire controller.
     * Implementations must be idempotent and return false to fail closed.
     */
    boolean prepare(WeaponShotContext context);
}
