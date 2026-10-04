package com.happysg.radar.compat.cbc;

import com.happysg.radar.api.weapon.RadarWeaponRegistry;

public final class CbcRadarWeaponRegistration {
    private static boolean registered;
    private CbcRadarWeaponRegistration() {}

    public static void register() {
        if (registered) {
            return;
        }

        RadarWeaponRegistry.register((level, mountPos) -> {
            CannonMountContext context = CannonMountContext.resolveEndpoint(level, mountPos);

            if (context == null || !context.isCurrent()) {
                return null;
            }

            return new CbcRadarWeaponAdapter(level, context);
        });

        registered = true;
    }
}