package com.happysg.radar.block.behavior.networks.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public record SafeZone(AABB aabb, @Nullable UUID subLevelId) {

    public boolean contains(Vec3 worldPos, net.minecraft.world.level.Level level) {
        if (subLevelId == null) {
            return aabb.contains(worldPos);
        }

        if (com.happysg.radar.compat.Mods.SABLE.isLoaded()) {
            return com.happysg.radar.compat.aeronautics.SableUtils.isInSafeZone(level, worldPos, aabb, subLevelId);
        }
        
        return false;
    }

    public boolean clips(Vec3 worldStart, Vec3 worldEnd, net.minecraft.world.level.Level level) {
        if (subLevelId == null) {
            return aabb.contains(worldStart) || aabb.contains(worldEnd) || aabb.clip(worldStart, worldEnd).isPresent();
        }

        if (com.happysg.radar.compat.Mods.SABLE.isLoaded()) {
            return com.happysg.radar.compat.aeronautics.SableUtils.clipsSafeZone(level, worldStart, worldEnd, aabb, subLevelId);
        }

        return false;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("minX", aabb.minX);
        tag.putDouble("minY", aabb.minY);
        tag.putDouble("minZ", aabb.minZ);
        tag.putDouble("maxX", aabb.maxX);
        tag.putDouble("maxY", aabb.maxY);
        tag.putDouble("maxZ", aabb.maxZ);
        if (subLevelId != null) {
            tag.putUUID("subLevelId", subLevelId);
        }
        return tag;
    }

    public static SafeZone fromTag(CompoundTag tag) {
        AABB aabb = new AABB(
                tag.getDouble("minX"),
                tag.getDouble("minY"),
                tag.getDouble("minZ"),
                tag.getDouble("maxX"),
                tag.getDouble("maxY"),
                tag.getDouble("maxZ")
        );
        UUID subLevelId = tag.hasUUID("subLevelId") ? tag.getUUID("subLevelId") : null;
        return new SafeZone(aabb, subLevelId);
    }
}
