package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.ProjectileModel;

/** Marker for flight models that must be solved by the CBCMS solver. */
public interface CBCMSProjectileModel extends ProjectileModel {
    int maxFlightTicks();

    @Override
    default boolean cbcPhysics() {
        return true;
    }

    @Override
    default boolean usesCustomDynamics() {
        return true;
    }
}
