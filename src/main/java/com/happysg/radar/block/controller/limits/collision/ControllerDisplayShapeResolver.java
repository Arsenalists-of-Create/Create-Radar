package com.happysg.radar.block.controller.limits.collision;

import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Resolves one clean block envelope plus renderer-like kinetic shafts. */
final class ControllerDisplayShapeResolver {
    private static final double SHAFT_MIN = 6.0 / 16.0;
    private static final double SHAFT_MAX = 10.0 / 16.0;
    private static final double CENTER = 0.5;
    private static final double EPSILON = 1.0e-6;

    private ControllerDisplayShapeResolver() {
    }

    static List<ControllerVisualBox> resolve(
            LevelReader level, BlockPos position, BlockState state
    ) {
        VoxelShape collision = state.getCollisionShape(level, position,
                CollisionContext.empty());
        VoxelShape outline = state.getShape(level, position,
                CollisionContext.empty());
        AABB envelope = envelope(collision, outline);
        ArrayList<ControllerVisualBox> result = new ArrayList<>();
        if (envelope != null) {
            result.add(ControllerVisualBox.axisAligned(envelope));
        }

        if (state.getBlock() instanceof IRotate rotating) {
            for (Direction direction : Direction.values()) {
                if (!rotating.hasShaftTowards(level, position, state,
                        direction)) {
                    continue;
                }
                AABB shaft = shaftBox(direction);
                if (envelope == null || !contains(envelope, shaft)) {
                    result.add(ControllerVisualBox.axisAligned(shaft));
                }
            }
        }
        return List.copyOf(result);
    }

    static AABB envelope(VoxelShape collision, VoxelShape outline) {
        AABB result = null;
        for (AABB box : collision.toAabbs()) {
            result = union(result, box);
        }
        for (AABB box : outline.toAabbs()) {
            result = union(result, box);
        }
        return result;
    }

    static AABB shaftBox(Direction direction) {
        return switch (direction) {
            case DOWN -> new AABB(SHAFT_MIN, 0, SHAFT_MIN,
                    SHAFT_MAX, CENTER, SHAFT_MAX);
            case UP -> new AABB(SHAFT_MIN, CENTER, SHAFT_MIN,
                    SHAFT_MAX, 1, SHAFT_MAX);
            case NORTH -> new AABB(SHAFT_MIN, SHAFT_MIN, 0,
                    SHAFT_MAX, SHAFT_MAX, CENTER);
            case SOUTH -> new AABB(SHAFT_MIN, SHAFT_MIN, CENTER,
                    SHAFT_MAX, SHAFT_MAX, 1);
            case WEST -> new AABB(0, SHAFT_MIN, SHAFT_MIN,
                    CENTER, SHAFT_MAX, SHAFT_MAX);
            case EAST -> new AABB(CENTER, SHAFT_MIN, SHAFT_MIN,
                    1, SHAFT_MAX, SHAFT_MAX);
        };
    }

    private static AABB union(AABB first, AABB second) {
        return first == null ? second : new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ));
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX + EPSILON
                && outer.minY <= inner.minY + EPSILON
                && outer.minZ <= inner.minZ + EPSILON
                && outer.maxX >= inner.maxX - EPSILON
                && outer.maxY >= inner.maxY - EPSILON
                && outer.maxZ >= inner.maxZ - EPSILON;
    }
}
