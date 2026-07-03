package com.happysg.radar.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Server-side collision-shape scanner for building a SubLevelSilhouette cache.
 *
 * <p>The supplied bounds and block positions must be in the sub-level's plot/local coordinates,
 * not its projected world coordinates. In other words, use the same coordinates that Sable's
 * logicalPose() expects as input.</p>
 */
public final class SubLevelSilhouetteScanner {

    private SubLevelSilhouetteScanner() {
    }

    public static SubLevelSilhouette scan(final Level level,
                                          final BlockPos minInclusive,
                                          final BlockPos maxInclusive,
                                          final Predicate<BlockState> includeState,
                                          final int maxBlocksToInspect) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(minInclusive, "minInclusive");
        Objects.requireNonNull(maxInclusive, "maxInclusive");
        Objects.requireNonNull(includeState, "includeState");

        if (maxBlocksToInspect < 1) {
            throw new IllegalArgumentException("maxBlocksToInspect must be positive");
        }

        final int minX = Math.min(minInclusive.getX(), maxInclusive.getX());
        final int minY = Math.min(minInclusive.getY(), maxInclusive.getY());
        final int minZ = Math.min(minInclusive.getZ(), maxInclusive.getZ());
        final int maxX = Math.max(minInclusive.getX(), maxInclusive.getX());
        final int maxY = Math.max(minInclusive.getY(), maxInclusive.getY());
        final int maxZ = Math.max(minInclusive.getZ(), maxInclusive.getZ());

        final SubLevelSilhouette.Builder builder = SubLevelSilhouette.builder();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int inspected = 0;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (++inspected > maxBlocksToInspect) {
                        throw new IllegalStateException("Sub-level silhouette scan exceeded maxBlocksToInspect=" + maxBlocksToInspect);
                    }

                    cursor.set(x, y, z);
                    final BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !includeState.test(state)) {
                        continue;
                    }

                    final VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (final AABB localShapeBox : shape.toAabbs()) {
                        builder.add(
                            localShapeBox.minX + x,
                            localShapeBox.minY + y,
                            localShapeBox.minZ + z,
                            localShapeBox.maxX + x,
                            localShapeBox.maxY + y,
                            localShapeBox.maxZ + z
                        );
                    }
                }
            }
        }

        return builder.build();
    }

    public static SubLevelSilhouette scanLoadedChunks(final SubLevel subLevel,
                                                      final Predicate<BlockState> includeState,
                                                      final int maxBlocksToInspect,
                                                      final int maxCollisionBoxes) {
        Objects.requireNonNull(subLevel, "subLevel");
        Objects.requireNonNull(includeState, "includeState");
        if (maxBlocksToInspect < 1) {
            throw new IllegalArgumentException("maxBlocksToInspect must be positive");
        }
        if (maxCollisionBoxes < 1) {
            throw new IllegalArgumentException("maxCollisionBoxes must be positive");
        }

        final LevelPlot plot = subLevel.getPlot();
        final BoundingBox3ic bounds = plot.getBoundingBox();
        if (isEmpty(bounds)) {
            return SubLevelSilhouette.builder().build();
        }

        final Level level = subLevel.getLevel();
        final SubLevelSilhouette.Builder builder = SubLevelSilhouette.builder();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int inspected = 0;
        int boxes = 0;

        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final ChunkPos chunkPos = holder.getPos();
            final int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) {
                continue;
            }

            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        if (++inspected > maxBlocksToInspect) {
                            throw new ScanLimitExceededException("blocks", maxBlocksToInspect);
                        }

                        cursor.set(x, y, z);
                        final BlockState state = level.getBlockState(cursor);
                        if (state.isAir() || !includeState.test(state)) {
                            continue;
                        }

                        final VoxelShape shape = state.getCollisionShape(level, cursor);
                        if (shape.isEmpty()) {
                            continue;
                        }

                        for (final AABB localShapeBox : shape.toAabbs()) {
                            if (++boxes > maxCollisionBoxes) {
                                throw new ScanLimitExceededException("collision_boxes", maxCollisionBoxes);
                            }
                            builder.add(
                                localShapeBox.minX + x,
                                localShapeBox.minY + y,
                                localShapeBox.minZ + z,
                                localShapeBox.maxX + x,
                                localShapeBox.maxY + y,
                                localShapeBox.maxZ + z
                            );
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    public static SubLevelSilhouette boundingBoxFallback(final SubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (isEmpty(bounds)) {
            return SubLevelSilhouette.builder().build();
        }
        return SubLevelSilhouette.builder()
            .add(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0)
            .build();
    }

    public static Predicate<BlockState> defaultHullFilter() {
        return state -> !state.isAir();
    }

    private static boolean isEmpty(final BoundingBox3ic bounds) {
        return bounds == null || bounds.maxX() < bounds.minX() || bounds.maxY() < bounds.minY() || bounds.maxZ() < bounds.minZ();
    }

    public static final class ScanLimitExceededException extends IllegalStateException {
        public ScanLimitExceededException(String limitName, int limit) {
            super("Sub-level silhouette scan exceeded " + limitName + "=" + limit);
        }
    }
}
