package com.happysg.radar.api.weapon;

import com.happysg.radar.api.mount.RadarMountAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * Generic public weapon context.
 *
 */
public record RadarWeaponContext(ServerLevel level, BlockPos mountPos, @Nullable RadarMountAdapter mount, RadarWeaponAdapter weapon) {}