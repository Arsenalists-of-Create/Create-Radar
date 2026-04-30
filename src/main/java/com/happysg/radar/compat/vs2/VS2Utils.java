package com.happysg.radar.compat.vs2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.LoadedShip;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class VS2Utils {
    public static Vec3 getWorldPos(net.minecraft.world.level.Level level, BlockPos pos) {
        if (com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() && level != null) {
            LoadedShip ship = VSGameUtilsKt.getShipManagingPos(level, pos);
            if (ship != null) {
                org.joml.Vector3d res = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ship.getShipToWorld().transformPosition(res);
                return new Vec3(res.x, res.y, res.z);
            }
        }
        return Vec3.atCenterOf(pos);
    }

    public static Vec3 getWorldPos(BlockEntity be) {
        if (com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() && be.getLevel() != null) {
            return getWorldPos(be.getLevel(), be.getBlockPos());
        }
        return Vec3.atCenterOf(be.getBlockPos());
    }

    public static Vec3 getWorldVec(BlockEntity be) {
        return getWorldPos(be);
    }

    public static Vec3 getWorldVec(net.minecraft.world.level.Level level, BlockPos pos) {
        return getWorldPos(level, pos);
    }

    public static Vec3 getWorldVec(net.minecraft.world.level.Level level, Vec3 pos) {
        if (com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() && level != null) {
            LoadedShip ship = VSGameUtilsKt.getShipManagingPos(level, BlockPos.containing(pos));
            if (ship != null) {
                org.joml.Vector3d res = new org.joml.Vector3d(pos.x, pos.y, pos.z);
                ship.getShipToWorld().transformPosition(res);
                return new Vec3(res.x, res.y, res.z);
            }
        }
        return pos;
    }
    public static LoadedShip getShipManagingPos(BlockEntity be) { 
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || be.getLevel() == null) return null;
        return VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
    }
    public static LoadedShip getShipManagingPos(net.minecraft.world.level.Level level, BlockPos pos) { 
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || level == null) return null;
        return VSGameUtilsKt.getShipManagingPos(level, pos);
    }
    public static boolean isBlockInShipyard(net.minecraft.world.level.Level level, BlockPos pos) { 
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || level == null) return false;
        return VSGameUtilsKt.getShipManagingPos(level, pos) != null;
    }
    public static Collection<Ship> getLoadedShips(net.minecraft.world.level.Level level, AABB aabb) { 
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || level == null) return Collections.emptyList();
        List<Ship> ships = new ArrayList<>();
        // Using a more manual approach to compatibility
        try {
            var shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
            Object loaded = shipWorld.getLoadedShips();
            if (loaded instanceof Iterable) {
                for (Object o : (Iterable)loaded) {
                    if (o instanceof Ship s) {
                        if (s.getWorldAABB() != null && s.getWorldAABB().intersects(aabb)) ships.add(s);
                    }
                }
            }
        } catch (Exception e) {}
        return ships;
    }
    public static Vec3 getShipVec(Vec3 v, BlockEntity be) {
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || be.getLevel() == null) return v;
        LoadedShip ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
        if (ship == null) return v;
        org.joml.Vector3d res = new org.joml.Vector3d(v.x, v.y, v.z);
        ship.getTransform().getShipToWorldRotation().invert(new org.joml.Quaterniond()).transform(res);
        return new Vec3(res.x, res.y, res.z);
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 v, BlockEntity be) {
        if (!com.happysg.radar.compat.Mods.VALKYRIENSKIES.isLoaded() || be.getLevel() == null) return v;
        LoadedShip ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
        if (ship == null) return v;
        org.joml.Vector3d res = new org.joml.Vector3d(v.x, v.y, v.z);
        ship.getTransform().getShipToWorldRotation().transform(res);
        return new Vec3(res.x, res.y, res.z);
    }

    public static AABB getShipAABB(Ship ship) {
        if (ship == null || ship.getWorldAABB() == null) return new AABB(0,0,0,1,1,1);
        var box = ship.getWorldAABB();
        return new net.minecraft.world.phys.AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static Long getShipId(Level level, BlockPos pos) {
        Object ship = getShipManagingPos(level, pos);
        if (ship instanceof org.valkyrienskies.core.api.ships.Ship s) {
            return s.getId();
        }
        return null;
    }
}