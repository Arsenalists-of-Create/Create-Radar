package com.happysg.radar.api.physics;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Moving-frame-safe coordinate API.
 */
public final class RadarPhysics {

    private RadarPhysics() {}

    public static BlockPos worldBlockPos(Level level, BlockPos pos) {
        return PhysicsHandler.getWorldPos(level, pos);
    }

    public static Vec3 worldPosition(Level level, BlockPos pos) {
        return PhysicsHandler.getWorldVec(level, pos);
    }

    public static Vec3 worldPosition(Level level, Vec3 pos) {
        return PhysicsHandler.getWorldVec(level, pos);
    }

    public static Vec3 worldPosition(BlockEntity blockEntity) {
        return PhysicsHandler.getWorldVec(blockEntity);
    }

    public static Vec3 localPosition(Vec3 worldPosition, BlockEntity blockEntity) {
        return PhysicsHandler.getShipVec(worldPosition, blockEntity);
    }

    public static Vec3 worldDirection(Vec3 localDirection, BlockEntity blockEntity) {
        return PhysicsHandler.getWorldVecDirectionTransform(localDirection, blockEntity);
    }

    public static boolean isInMovingFrame(Level level, BlockPos pos) {
        return PhysicsHandler.isBlockInPlotyard(level, pos);
    }

    public static Set<UUID> connectedFrameIds(Level level, BlockPos pos) {
        return PhysicsHandler.getConnectedSublevelIds(level, pos);
    }
}