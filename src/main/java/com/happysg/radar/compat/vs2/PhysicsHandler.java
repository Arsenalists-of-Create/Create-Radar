package com.happysg.radar.compat.vs2;

import com.happysg.radar.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Middle layer to avoid crashes when Valkyrien Skies 2 is not loaded.
 * No direct references to Valkyrien Skies 2 classes.
 * Reference VS2Utils for direct references to Valkyrien Skies 2 classes.
 * always check vs2 is loaded before calling VS2Utils
 * in future will be used to support aeronautics in similar way
 */
public class PhysicsHandler {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded())
            return pos;
        return SableUtils.getWorldPos(level, pos);
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        return SableUtils.getShipVec(vec3, be);
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        return SableUtils.getWorldVecDirectionTransform(vec3, be);
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded())
            return new Vec3(pos.getX(), pos.getY(), pos.getZ());
        return SableUtils.getWorldVec(level, pos);
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        return SableUtils.getWorldVec(level, vec3);
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        if (!Mods.SABLE.isLoaded())
            return blockEntity.getBlockPos().getCenter();
        return SableUtils.getWorldVec(blockEntity);
    }

    public static boolean isBlockInPlotyard(Level level, BlockPos blockPos) {
        if (!Mods.SABLE.isLoaded())
            return false;
        return SableUtils.isBlockInShipyard(level, blockPos);
    }
}
