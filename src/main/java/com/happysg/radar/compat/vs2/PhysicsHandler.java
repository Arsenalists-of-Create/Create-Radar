package com.happysg.radar.compat.vs2;

import com.happysg.radar.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Middle layer to avoid crashes when Valkyrien Skies 2 is not loaded.
 * No direct references to Valkyrien Skies 2 classes.
 * Reference VS2Utils for direct references to Valkyrien Skies 2 classes.
 * always check vs2 is loaded before calling VS2Utils
 * in future will be used to support aeronautics in similar way
 */
public class PhysicsHandler {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return pos;
        return VS2Utils.getWorldPos(level, pos);
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return VS2Utils.getShipVec(vec3, be);
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return VS2Utils.getWorldVecDirectionTransform(vec3, be);
    }

    public static Vec3 getShipVecDirectionTransform(Vec3 worldDirection, BlockEntity blockEntity) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return worldDirection;
        return VS2Utils.getShipVecDirectionTransform(worldDirection, VS2Utils.getShipManagingPos(blockEntity));
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return pos.getCenter();
        return VS2Utils.getWorldVec(level, pos);
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return VS2Utils.getWorldVec(level, vec3);
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return blockEntity.getBlockPos().getCenter();
        return VS2Utils.getWorldVec(blockEntity);
    }

    public static boolean isBlockInPlotyard(Level level, BlockPos blockPos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return false;
        return VS2Utils.isBlockInShipyard(level, blockPos);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
        return isBlockInPlotyard(level, blockPos);
    }

    public static Set<UUID> getConnectedSublevelIds(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return Set.of();
        return VS2Utils.getConnectedSublevelIds(level, pos);
    }
}
