package com.happysg.radar.api.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * Finds a weapon adapter for a weapon mounted at a canonical mount position.
 */
@FunctionalInterface
public interface RadarWeaponProvider {

    /**
     * @param level server level containing the weapon
     * @param mountPos canonical mount position
     * @return weapon adapter, or null when this provider does not recognize it
     */
    @Nullable
    RadarWeaponAdapter find(ServerLevel level, BlockPos mountPos);
}