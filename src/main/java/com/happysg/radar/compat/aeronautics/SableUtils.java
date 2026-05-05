package com.happysg.radar.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.world.phys.AABB;

/**
 * Direct Sable API calls. Only loaded when Sable is present.
 * Always check Mods.SABLE.isLoaded() before calling anything here.
 */
public class SableUtils {

    /**
     * Returns true if the given BlockPos is inside a Sable SubLevel (ship/airship).
     */
    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos) != null;
    }

    /**
     * Project a block-center position from sub-level local space to global world space.
     */
    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
    }

    /**
     * Project a Vec3 from sub-level local space to global world space.
     */
    public static Vec3 getWorldVec(Level level, Vec3 vec) {
        return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, vec);
    }

    /**
     * Project a Vec3 from global world space to sub-level local space.
     */
    public static Vec3 getLocalVec(Level level, Vec3 vec) {
        dev.ryanhcode.sable.sublevel.SubLevel sl = getSubLevel(level);
        if (sl == null) return vec;
        var pose = sl.logicalPose();
        org.joml.Vector3d localPos = pose.transformPositionInverse(new org.joml.Vector3d(vec.x, vec.y, vec.z), new org.joml.Vector3d());
        return new Vec3(localPos.x, localPos.y, localPos.z);
    }

    /**
     * Project a BlockEntity's position from sub-level local to world space.
     */
    public static Vec3 getWorldVec(BlockEntity be) {
        return dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(be.getLevel(), Vec3.atCenterOf(be.getBlockPos()));
    }

    /**
     * Returns the world-space BlockPos for a BlockEntity on a ship.
     */
    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        return BlockPos.containing(getWorldVec(level, pos));
    }

    /**
     * Transform a direction vector from sub-level local to world space.
     */
    public static Vec3 getWorldVecDirectionTransform(Vec3 direction, BlockEntity be) {
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(be);
        if (subLevel == null) return direction;
        var pose = subLevel.logicalPose();
        org.joml.Vector3d res = new org.joml.Vector3d(direction.x, direction.y, direction.z);
        pose.transformNormal(res, res);
        return new Vec3(res.x, res.y, res.z);
    }

    /**
     * Transform a direction vector from world space to sub-level local space.
     */
    public static Vec3 getShipVec(Vec3 worldDirection, BlockEntity be) {
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(be);
        if (subLevel == null) return worldDirection;
        var pose = subLevel.logicalPose();
        org.joml.Vector3d res = new org.joml.Vector3d(worldDirection.x, worldDirection.y, worldDirection.z);
        pose.transformNormalInverse(res, res);
        return new Vec3(res.x, res.y, res.z);
    }

    /**
     * Returns the yaw in degrees for a position inside a sub-level.
     */
    public static float getShipYawDeg(Level level, BlockPos pos) {
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) return 0f;
        var pose = subLevel.logicalPose();
        org.joml.Vector3d fwd = new org.joml.Vector3d(0, 0, 1);
        pose.transformNormal(fwd, fwd);
        return (float) -Math.toDegrees(Math.atan2(fwd.x, fwd.z));
    }

    public static float getShipYawDeg(BlockEntity be) {
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(be);
        if (subLevel == null) return 0f;
        var pose = subLevel.logicalPose();
        org.joml.Vector3d fwd = new org.joml.Vector3d(0, 0, 1);
        pose.transformNormal(fwd, fwd);
        return (float) -Math.toDegrees(Math.atan2(fwd.x, fwd.z));
    }

    /**
     * Returns the current velocity of the sub-level at the given position, in blocks/tick.
     */
    public static Vec3 getVelocity(Level level, BlockPos pos) {
        Vec3 vel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(level, Vec3.atCenterOf(pos));
        if (vel == null) return Vec3.ZERO;
        return vel.scale(1.0 / 20.0);
    }

    public static Level getParentLevel(Level level, BlockPos pos) {
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) return subLevel.getLevel();
        return null;
    }

    public static Collection<? extends dev.ryanhcode.sable.sublevel.SubLevel> getLoadedSubLevels(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (serverLevel.getServer() == null) return java.util.Collections.emptyList();
            java.util.List<dev.ryanhcode.sable.sublevel.SubLevel> all = new java.util.ArrayList<>();
            for (net.minecraft.server.level.ServerLevel sll : serverLevel.getServer().getAllLevels()) {
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sll);
                if (container != null) {
                    all.addAll(container.getAllSubLevels());
                }
            }
            return all;
        } else {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container != null) return container.getAllSubLevels();
            return java.util.Collections.emptyList();
        }
    }

    public static dev.ryanhcode.sable.sublevel.SubLevel getSubLevelManagingPos(Level level, BlockPos pos) {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    public static dev.ryanhcode.sable.sublevel.SubLevel getSubLevelManagingPos(Level level, Entity entity) {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(entity);
    }
    public static String getSubLevelNamespace(dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        String name = subLevel.getName();
        return (name == null || name.isBlank()) ? subLevel.getUniqueId().toString() : name;
    }

    public static String getSubLevelName(Level level) {
        dev.ryanhcode.sable.sublevel.SubLevel sl = getSubLevel(level);
        if (sl != null) return getSubLevelNamespace(sl);
        return null;
    }

    public static dev.ryanhcode.sable.sublevel.SubLevel getSubLevel(Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        if (serverLevel.getServer() == null) return null;
        for (net.minecraft.server.level.ServerLevel sll : serverLevel.getServer().getAllLevels()) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sll);
            if (container == null) continue;
            for (dev.ryanhcode.sable.sublevel.SubLevel sl : container.getAllSubLevels()) {
                if (sl.getLevel() == level) return sl;
            }
        }
        return null;
    }

    public static String getSubLevelName(dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        return subLevel.getName();
    }
    public static Vec3 getWorldVec(dev.ryanhcode.sable.sublevel.SubLevel subLevel, Vec3 localVec) {
        var pose = subLevel.logicalPose();
        org.joml.Vector3d localPos = new org.joml.Vector3d(localVec.x, localVec.y, localVec.z);
        org.joml.Vector3d worldPos = pose.transformPosition(localPos, new org.joml.Vector3d());
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }

    public static boolean isInSafeZone(Level level, Vec3 worldPos, AABB localAABB, UUID subLevelId) {
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(level)) {
            if (subLevel.getUniqueId().equals(subLevelId)) {
                var pose = subLevel.logicalPose();
                org.joml.Vector3d local = pose.transformPositionInverse(new org.joml.Vector3d(worldPos.x, worldPos.y, worldPos.z), new org.joml.Vector3d());
                return localAABB.contains(local.x, local.y, local.z);
            }
        }
        return false;
    }

    public static boolean clipsSafeZone(Level level, Vec3 worldStart, Vec3 worldEnd, AABB localAABB, UUID subLevelId) {
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(level)) {
            if (subLevel.getUniqueId().equals(subLevelId)) {
                var pose = subLevel.logicalPose();
                org.joml.Vector3d localStart = pose.transformPositionInverse(new org.joml.Vector3d(worldStart.x, worldStart.y, worldStart.z), new org.joml.Vector3d());
                org.joml.Vector3d localEnd = pose.transformPositionInverse(new org.joml.Vector3d(worldEnd.x, worldEnd.y, worldEnd.z), new org.joml.Vector3d());

                Vec3 ls = new Vec3(localStart.x, localStart.y, localStart.z);
                Vec3 le = new Vec3(localEnd.x, localEnd.y, localEnd.z);

                return localAABB.contains(ls) || localAABB.contains(le) || localAABB.clip(ls, le).isPresent();
            }
        }
        return false;
    }

    public static AABB getWorldAABB(Level world, AABB localAABB, UUID subLevelId) {
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(world)) {
            if (subLevel.getUniqueId().equals(subLevelId)) {
                var pose = subLevel.logicalPose();
                Vec3[] corners = new Vec3[]{
                        new Vec3(localAABB.minX, localAABB.minY, localAABB.minZ),
                        new Vec3(localAABB.minX, localAABB.minY, localAABB.maxZ),
                        new Vec3(localAABB.minX, localAABB.maxY, localAABB.minZ),
                        new Vec3(localAABB.minX, localAABB.maxY, localAABB.maxZ),
                        new Vec3(localAABB.maxX, localAABB.minY, localAABB.minZ),
                        new Vec3(localAABB.maxX, localAABB.minY, localAABB.maxZ),
                        new Vec3(localAABB.maxX, localAABB.maxY, localAABB.minZ),
                        new Vec3(localAABB.maxX, localAABB.maxY, localAABB.maxZ)
                };

                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

                for (Vec3 corner : corners) {
                    Vec3 worldCorner = getWorldVec(subLevel, corner);
                    minX = Math.min(minX, worldCorner.x);
                    minY = Math.min(minY, worldCorner.y);
                    minZ = Math.min(minZ, worldCorner.z);
                    maxX = Math.max(maxX, worldCorner.x);
                    maxY = Math.max(maxY, worldCorner.y);
                    maxZ = Math.max(maxZ, worldCorner.z);
                }
                return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
        return localAABB;
    }

    public static Vec3 getWorldVec(Level world, BlockPos localPos, @Nullable java.util.UUID subLevelId) {
        if (subLevelId == null) return Vec3.atCenterOf(localPos);
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(world)) {
            if (subLevel.getUniqueId().equals(subLevelId)) {
                return getWorldVec(subLevel, Vec3.atCenterOf(localPos));
            }
        }
        return Vec3.atCenterOf(localPos);
    }

    public static Vec3 getVelocity(Level world, BlockPos localPos, @Nullable java.util.UUID subLevelId) {
        if (subLevelId == null) return Vec3.ZERO;
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(world)) {
            if (subLevel.getUniqueId().equals(subLevelId)) {
                Vec3 vel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(world, getWorldVec(subLevel, Vec3.atCenterOf(localPos)));
                if (vel == null) return Vec3.ZERO;
                return vel.scale(1.0 / 20.0);
            }
        }
        return Vec3.ZERO;
    }

    public static ClipResult multiLevelClip(Level contextLevel, Vec3 start, Vec3 end, net.minecraft.world.level.ClipContext.Block blockMode, net.minecraft.world.level.ClipContext.Fluid fluidMode, Entity entity) {
        net.minecraft.world.phys.BlockHitResult bestHit = net.minecraft.world.phys.BlockHitResult.miss(end, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.ZERO);
        double bestDistSq = Double.MAX_VALUE;
        dev.ryanhcode.sable.sublevel.SubLevel bestSubLevel = null;
        Vec3 bestWorldPos = end;

        // 1. Check all root dimensions
        java.util.List<Level> roots = new java.util.ArrayList<>();
        if (contextLevel instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.getServer() != null) {
            for (net.minecraft.server.level.ServerLevel sll : serverLevel.getServer().getAllLevels()) {
                if (getSubLevel(sll) == null) roots.add(sll);
            }
        } else {
            if (getSubLevel(contextLevel) == null) roots.add(contextLevel);
        }

        for (Level root : roots) {
            net.minecraft.world.phys.BlockHitResult hit = root.clip(new net.minecraft.world.level.ClipContext(start, end, blockMode, fluidMode, entity));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                double distSq = start.distanceToSqr(hit.getLocation());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestHit = hit;
                    bestSubLevel = null;
                    bestWorldPos = hit.getLocation();
                }
            }
        }

        // 2. Check all sub-levels (ships)
        for (dev.ryanhcode.sable.sublevel.SubLevel subLevel : getLoadedSubLevels(contextLevel)) {
            Level shipLevel = subLevel.getLevel();
            if (shipLevel == null) continue;

            var pose = subLevel.logicalPose();
            org.joml.Vector3d localStart = pose.transformPositionInverse(new org.joml.Vector3d(start.x, start.y, start.z), new org.joml.Vector3d());
            org.joml.Vector3d localEnd = pose.transformPositionInverse(new org.joml.Vector3d(end.x, end.y, end.z), new org.joml.Vector3d());

            net.minecraft.world.phys.BlockHitResult localHit = shipLevel.clip(new net.minecraft.world.level.ClipContext(
                    new Vec3(localStart.x, localStart.y, localStart.z),
                    new Vec3(localEnd.x, localEnd.y, localEnd.z),
                    blockMode, fluidMode, entity));

            if (localHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 worldHitLoc = getWorldVec(subLevel, localHit.getLocation());
                double distSq = start.distanceToSqr(worldHitLoc);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestHit = localHit;
                    bestSubLevel = subLevel;
                    bestWorldPos = worldHitLoc;
                }
            }
        }
        return new ClipResult(bestHit, bestSubLevel, bestWorldPos);
    }

    public record ClipResult(net.minecraft.world.phys.BlockHitResult hit, @Nullable dev.ryanhcode.sable.sublevel.SubLevel subLevel, Vec3 worldHitPos) {}
}
