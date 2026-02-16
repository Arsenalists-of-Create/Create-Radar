package com.happysg.radar.compat.vs2;

import com.happysg.radar.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.List;

public class VS2Utils {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return pos;
//        if (VSGameUtilsKt.getShipObjectManagingPos(level, pos) != null) {
//            final LoadedShip loadedShip = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
//            if(loadedShip == null) return null;
//            final Vector3d vec = loadedShip.getShipToWorld().transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
//            VectorConversionsMCKt.toMinecraft(vec);
//            return new BlockPos((int) vec.x(), (int) vec.y(), (int) vec.z());
//        }
        return pos;
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return getShipVec(vec3, getShipManagingPos(be));
    }

    public static Vec3 getShipVec(Vec3 vec3, Ship ship){
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
//        if (ship != null) {
//            ship.getShipToWorld();
//            final Vector3d vec = ship.getWorldToShip().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
//            VectorConversionsMCKt.toMinecraft(vec);
//            return new Vec3(vec.x(), vec.y(), vec.z());
//        }
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, Ship ship) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
//        if (ship != null) {
//            ship.getShipToWorld();
//            final Vector3d vec = ship.getShipToWorld().transformDirection(new Vector3d(vec3.x, vec3.y, vec3.z));
//            VectorConversionsMCKt.toMinecraft(vec);
//            return new Vec3(vec.x(), vec.y(), vec.z());
//        }
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return getWorldVecDirectionTransform(vec3, getShipManagingPos(be));
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Iterable<Ship> getLoadedShips(Level level, AABB aabb) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return List.of();
//        return VSGameUtilsKt.getShipsIntersecting(level, aabb);
        return List.of();
    }

    public static LoadedShip getShipManagingPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return null;
//        return VSGameUtilsKt.getShipObjectManagingPos(level, pos);
        return null;
    }

    public static LoadedShip getShipManagingPos(BlockEntity blockEntity) {
        return getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return new Vec3(pos.getX(), pos.getY(), pos.getZ());
//        if (VSGameUtilsKt.getShipObjectManagingPos(level, pos) != null) {
//            final LoadedShip loadedShip = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
//            Vec3 center = pos.getCenter();
//            if(loadedShip == null) return null;
//            final Vector3d vec = loadedShip.getShipToWorld().transformPosition(new Vector3d(center.x, center.y, center.z));
//            VectorConversionsMCKt.toMinecraft(vec);
//            return new Vec3(vec.x(), vec.y(), vec.z());
//        }
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }
    public static Vec3 getWorldVec(Level level, Vec3 vec3){
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
//        Vec3i vec3i = new Vec3i((int) vec3.x, (int) vec3.y, (int) vec3.z);
//        if (VSGameUtilsKt.getShipObjectManagingPos(level, vec3i) != null) {
//            final LoadedShip loadedShip = VSGameUtilsKt.getShipObjectManagingPos(level, vec3i);
//            if(loadedShip == null) return null;
//            final Vector3d vec = loadedShip.getShipToWorld().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
//            VectorConversionsMCKt.toMinecraft(vec);
//            return new Vec3(vec.x(), vec.y(), vec.z());
//        }
        return vec3;
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return blockEntity.getBlockPos().getCenter();
        return getWorldVec(blockEntity.getLevel(), blockEntity.getBlockPos());
    }
    public static Vec3 getVec3FromVector(Vector3d vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
    public static BlockPos getBlockPosFromVec3(Vec3 vec3) {
        return new BlockPos((int) vec3.x, (int) vec3.y, (int) vec3.z);
    }
    public static Vector3d  getVector3dFromVec3(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return false;
        return VSGameUtilsKt.isBlockInShipyard(level, blockPos);
    }
}
