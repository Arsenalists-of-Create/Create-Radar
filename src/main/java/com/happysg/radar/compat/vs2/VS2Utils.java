package com.happysg.radar.compat.vs2;

import com.happysg.radar.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VS2Utils {
    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return pos;
        LoadedShip ship = getShipManagingPos(level, pos);
        if (ship != null) {
            Vector3d vec = ship.getShipToWorld().transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
            return new BlockPos((int) vec.x(), (int) vec.y(), (int) vec.z());
        }
        return pos;
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return getShipVec(vec3, getShipManagingPos(be));
    }

    public static Vec3 getShipVec(Vec3 vec3, Ship ship) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        if (ship != null) {
            Vector3d vec = ship.getWorldToShip().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        return getWorldVecDirectionTransform(vec3, getShipManagingPos(be));
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, Ship ship) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        if (ship != null) {
            Vector3d vec = ship.getShipToWorld().transformDirection(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static Vec3 getShipVecDirectionTransform(Vec3 vec3, Ship ship) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        if (ship != null) {
            Vector3d vec = ship.getWorldToShip().transformDirection(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return vec3;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Iterable<Ship> getLoadedShips(Level level, AABB aabb) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return List.of();
        return VSGameUtilsKt.getShipsIntersecting(level, aabb);
    }

    public static LoadedShip getShipManagingPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return null;
        return VSGameUtilsKt.getShipObjectManagingPos(level, pos);
    }

    public static LoadedShip getShipManagingPos(BlockEntity blockEntity) {
        return getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Set<UUID> getConnectedSublevelIds(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return Set.of();

        LoadedShip ship = getShipManagingPos(level, pos);
        if (ship == null)
            return Set.of();

        long shipId = ship.getId();
        if (!(level instanceof ServerLevel serverLevel))
            return Set.of(new UUID(0L, shipId));

        List<Long> connected = ValkyrienSkiesMod.getOrCreateGTPA(VSGameUtilsKt.getDimensionId(serverLevel)).getAllConnectedShips(shipId);
        Set<UUID> ids = new HashSet<>();
        ids.add(new UUID(0L, shipId));
        for (long id : connected) ids.add(new UUID(0L, id));
        return Set.copyOf(ids);
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return new Vec3(pos.getX(), pos.getY(), pos.getZ());
        LoadedShip ship = getShipManagingPos(level, pos);
        if (ship != null) {
            Vec3 center = pos.getCenter();
            Vector3d vec = ship.getShipToWorld().transformPosition(new Vector3d(center.x, center.y, center.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, vec3.x, vec3.y, vec3.z);
        return getWorldVec(vec3, ship);
    }

    public static Vec3 getWorldVec(Vec3 vec3, Ship ship) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return vec3;
        if (ship != null) {
            Vector3d vec = ship.getShipToWorld().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z));
            return new Vec3(vec.x(), vec.y(), vec.z());
        }
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

    public static Vector3d getVector3dFromVec3(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return false;
        return VSGameUtilsKt.isBlockInShipyard(level, blockPos);
    }
}
