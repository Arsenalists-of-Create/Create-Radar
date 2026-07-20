package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * A cannon exclusion volume. When anchored to a Sable sublevel, {@code bounds}
 * are stored in that sublevel's local coordinates and therefore move and rotate
 * with Aeronautics contraptions.
 */
public record SafeZone(AABB bounds, @Nullable UUID subLevelId) {
    private static final String TAG_SUBLEVEL_ID = "SubLevelId";

    public static SafeZone between(Level level, BlockPos startPos, BlockPos endPos) {
        SubLevelAccess startSubLevel = subLevelAt(level, startPos);
        SubLevelAccess endSubLevel = subLevelAt(level, endPos);
        if (startSubLevel != null && endSubLevel != null
                && startSubLevel.getUniqueId().equals(endSubLevel.getUniqueId())) {
            return new SafeZone(blockBounds(startPos, endPos), startSubLevel.getUniqueId());
        }

        if (startSubLevel == null && endSubLevel == null) {
            return new SafeZone(blockBounds(startPos, endPos), null);
        }

        // A zone whose corners belong to different coordinate spaces cannot move
        // coherently, so preserve it as a static world-space volume.
        Vec3 start = toWorldCenter(startSubLevel, startPos);
        Vec3 end = toWorldCenter(endSubLevel, endPos);
        return new SafeZone(new AABB(
                Math.min(start.x, end.x) - 0.5D,
                Math.min(start.y, end.y) - 0.5D,
                Math.min(start.z, end.z) - 0.5D,
                Math.max(start.x, end.x) + 0.5D,
                Math.max(start.y, end.y) + 0.5D,
                Math.max(start.z, end.z) + 0.5D
        ), null);
    }

    public boolean contains(Level level, Vec3 worldPosition) {
        Vec3 localPosition = toLocal(level, worldPosition);
        return localPosition != null && bounds.contains(localPosition);
    }

    public boolean intersects(Level level, Vec3 worldStart, Vec3 worldEnd) {
        Vec3 localStart = toLocal(level, worldStart);
        Vec3 localEnd = toLocal(level, worldEnd);
        if (localStart == null || localEnd == null) {
            return false;
        }
        return bounds.contains(localStart)
                || bounds.contains(localEnd)
                || bounds.clip(localStart, localEnd).isPresent();
    }

    /** Returns a conservative world AABB for rendering and radar-display projection. */
    @Nullable
    public AABB worldBounds(Level level) {
        SubLevelAccess subLevel = resolveSubLevel(level);
        if (subLevel == null) {
            return subLevelId == null ? bounds : null;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{bounds.minX, bounds.maxX}) {
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    Vector3d world = subLevel.logicalPose().transformPosition(new Vector3d(x, y, z));
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("minX", bounds.minX);
        tag.putDouble("minY", bounds.minY);
        tag.putDouble("minZ", bounds.minZ);
        tag.putDouble("maxX", bounds.maxX);
        tag.putDouble("maxY", bounds.maxY);
        tag.putDouble("maxZ", bounds.maxZ);
        if (subLevelId != null) {
            tag.putUUID(TAG_SUBLEVEL_ID, subLevelId);
        }
        return tag;
    }

    public static SafeZone load(CompoundTag tag) {
        AABB bounds = new AABB(
                tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ")
        );
        UUID subLevelId = tag.hasUUID(TAG_SUBLEVEL_ID) ? tag.getUUID(TAG_SUBLEVEL_ID) : null;
        return new SafeZone(bounds, subLevelId);
    }

    public static SafeZone load(@Nullable Level level, CompoundTag tag) {
        SafeZone loaded = load(tag);
        if (level == null || loaded.subLevelId != null || !Mods.SABLE.isLoaded()) {
            return loaded;
        }

        BlockPos minPos = BlockPos.containing(
                loaded.bounds.minX, loaded.bounds.minY, loaded.bounds.minZ);
        BlockPos maxPos = BlockPos.containing(
                Math.nextDown(loaded.bounds.maxX),
                Math.nextDown(loaded.bounds.maxY),
                Math.nextDown(loaded.bounds.maxZ));
        SubLevelAccess minSubLevel = subLevelAt(level, minPos);
        SubLevelAccess maxSubLevel = subLevelAt(level, maxPos);
        if (minSubLevel != null && maxSubLevel != null
                && minSubLevel.getUniqueId().equals(maxSubLevel.getUniqueId())) {
            return new SafeZone(loaded.bounds, minSubLevel.getUniqueId());
        }
        return loaded;
    }

    private Vec3 toLocal(Level level, Vec3 worldPosition) {
        SubLevelAccess subLevel = resolveSubLevel(level);
        if (subLevelId != null && subLevel == null) {
            return null;
        }
        if (subLevel == null) {
            return worldPosition;
        }
        Vector3d local = subLevel.logicalPose().transformPositionInverse(
                new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z));
        return new Vec3(local.x, local.y, local.z);
    }

    @Nullable
    private SubLevelAccess resolveSubLevel(Level level) {
        if (subLevelId == null || !Mods.SABLE.isLoaded()) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(subLevelId);
    }

    @Nullable
    private static SubLevelAccess subLevelAt(Level level, BlockPos pos) {
        return Mods.SABLE.isLoaded() ? SableUtils.getShipManagingPos(level, pos) : null;
    }

    private static Vec3 toWorldCenter(@Nullable SubLevelAccess subLevel, BlockPos pos) {
        Vec3 center = pos.getCenter();
        if (subLevel == null) {
            return center;
        }
        Vector3d world = subLevel.logicalPose().transformPosition(
                new Vector3d(center.x, center.y, center.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private static AABB blockBounds(BlockPos startPos, BlockPos endPos) {
        return new AABB(
                Math.min(startPos.getX(), endPos.getX()),
                Math.min(startPos.getY(), endPos.getY()),
                Math.min(startPos.getZ(), endPos.getZ()),
                Math.max(startPos.getX(), endPos.getX()) + 1.0D,
                Math.max(startPos.getY(), endPos.getY()) + 1.0D,
                Math.max(startPos.getZ(), endPos.getZ()) + 1.0D
        );
    }
}
