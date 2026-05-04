package com.happysg.radar.compat.vs2;

import com.happysg.radar.compat.Mods;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SableUtils {


    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded())
            return pos;
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        if (subLevel != null) {
            final Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
            return new BlockPos((int) vec.x(), (int) vec.y(), (int) vec.z());
        }
        return pos;
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        return getShipVec(vec3, getShipManagingPos(be));
    }

    public static Vec3 getShipVec(Vec3 vec3, SubLevelAccess subLevel){
        if (!Mods.SABLE.isLoaded())
            return vec3;
        if (subLevel != null) {
            final Vector3d vec = subLevel.logicalPose().transformPositionInverse(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        return getWorldVecDirectionTransform(vec3, getShipManagingPos(be));
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, SubLevelAccess subLevel) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        if (subLevel != null) {
            final Vector3d vec = subLevel.logicalPose().transformNormal(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static Vec3 getShipVecDirectionTransform(Vec3 vec3, SubLevelAccess subLevel) {
        if (!Mods.SABLE.isLoaded())
            return vec3;
        if (subLevel != null) {
            final Vector3d vec = subLevel.logicalPose().transformNormalInverse(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Iterable<SubLevel> getLoadedShips(Level level, AABB aabb) {
        if (!Mods.SABLE.isLoaded())
            return List.of();
        BoundingBox3dc boundingBox = new BoundingBox3d(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        return Objects.requireNonNull(SubLevelContainer.getContainer(level)).queryIntersecting(boundingBox);
    }


    public static SubLevelAccess getShipManagingPos(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded())
            return null;
        return SableCompanion.INSTANCE.getContaining(level, pos);
    }

    public static SubLevelAccess getShipManagingPos(BlockEntity blockEntity) {
        return getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded())
            return new Vec3(pos.getX(), pos.getY(), pos.getZ());
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        if (subLevel != null) {
            Vec3 center = pos.getCenter();
            final Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d(center.x, center.y, center.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3){
        if (!Mods.SABLE.isLoaded())
            return vec3;
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, vec3);
        if (subLevel != null) {
            final Vector3d vec = subLevel.logicalPose().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        if (!Mods.SABLE.isLoaded())
            return blockEntity.getBlockPos().getCenter();
        return getWorldVec(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getVec3FromVector(Vector3d vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }

    public static BlockPos getBlockPosFromVec3(Vec3 vec3) {
        return new BlockPos((int) vec3.x, (int) vec3.y, (int) vec3.z);
    }
    public static Vector3d getVector3dFromVec3(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
        if (!Mods.SABLE.isLoaded())
            return false;
        return SableCompanion.INSTANCE.getContaining(level, blockPos) != null;
    }
}
