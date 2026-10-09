package com.happysg.radar.api.radar.rwr;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public record RadarRwrTarget(Vec3 position, @Nullable String targetId) {
    public RadarRwrTarget {
        if (position == null) {
            throw new IllegalArgumentException("position cannot be null");
        }
    }
}