package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2SafeZone;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record SafeZone(AABB bounds, @Nullable UUID subLevelId) {
    private static final String TAG_SUBLEVEL_ID = "SubLevelId";

    public static SafeZone between(Level level, BlockPos startPos, BlockPos endPos) {
        UUID startSubLevel = subLevelAt(level, startPos);
        UUID endSubLevel = subLevelAt(level, endPos);
        if (startSubLevel != null && startSubLevel.equals(endSubLevel)) {
            return new SafeZone(blockBounds(startPos, endPos), startSubLevel);
        }

        if (startSubLevel == null && endSubLevel == null) {
            return new SafeZone(blockBounds(startPos, endPos), null);
        }

        Vec3 start = toWorldCenter(level, startSubLevel, startPos);
        Vec3 end = toWorldCenter(level, endSubLevel, endPos);
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
        return bounds.contains(localStart) || bounds.contains(localEnd) || bounds.clip(localStart, localEnd).isPresent();
    }

    /** Returns a conservative world AABB for rendering and radar-display projection. */
    @Nullable
    public AABB worldBounds(Level level) {
        if (subLevelId == null) {
            return bounds;
        }
        if (!Mods.VALKYRIENSKIES.isLoaded()) {
            return null;
        }
        return VS2SafeZone.worldBounds(level, subLevelId, bounds);
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
        if (level == null || loaded.subLevelId != null || !Mods.VALKYRIENSKIES.isLoaded()) {
            return loaded;
        }

        BlockPos minPos = BlockPos.containing(
                loaded.bounds.minX, loaded.bounds.minY, loaded.bounds.minZ);
        BlockPos maxPos = BlockPos.containing(
                Math.nextDown(loaded.bounds.maxX),
                Math.nextDown(loaded.bounds.maxY),
                Math.nextDown(loaded.bounds.maxZ));
        UUID minSubLevel = subLevelAt(level, minPos);
        UUID maxSubLevel = subLevelAt(level, maxPos);
        if (minSubLevel != null && minSubLevel.equals(maxSubLevel)) {
            return new SafeZone(loaded.bounds, minSubLevel);
        }
        return loaded;
    }

    private Vec3 toLocal(Level level, Vec3 worldPosition) {
        if (subLevelId == null) {
            return worldPosition;
        }
        if (!Mods.VALKYRIENSKIES.isLoaded()) {
            return null;
        }
        return VS2SafeZone.localPosition(level, subLevelId, worldPosition);
    }

    @Nullable
    private static UUID subLevelAt(Level level, BlockPos pos) {
        return Mods.VALKYRIENSKIES.isLoaded() ? VS2SafeZone.shipIdAt(level, pos) : null;
    }

    private static Vec3 toWorldCenter(Level level, @Nullable UUID subLevel, BlockPos pos) {
        return subLevel == null ? pos.getCenter() : VS2SafeZone.worldCenter(level, pos);
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
