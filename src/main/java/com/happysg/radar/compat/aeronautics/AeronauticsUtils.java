package com.happysg.radar.compat.aeronautics;

import com.happysg.radar.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Middle layer that guards all Sable API calls.
 * Never reference SableUtils directly — always go through this class,
 * after checking Mods.SABLE.isLoaded().
 */
public class AeronauticsUtils {

    public static boolean isShip(Entity entity) {
        if (entity == null) return false;
        String name = entity.getClass().getName();
        return name.contains("aeronautics") || name.contains("simulated");
    }

    public static Object getShipManagingPos(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return null;
        return SableUtils.getSubLevelManagingPos(level, pos);
    }

    public static Object getSubLevelManagingPos(Level level, Entity entity) {
        if (!Mods.SABLE.isLoaded()) return null;
        return SableUtils.getSubLevelManagingPos(level, entity);
    }

    public static boolean isShipAlive(Level level, String id) {
        return true;
    }

    // -----------------------------------------------------------------------
    // Block / position queries
    // -----------------------------------------------------------------------

    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return false;
        return SableUtils.isBlockInShipyard(level, pos);
    }

    // -----------------------------------------------------------------------
    // World-space projection
    // -----------------------------------------------------------------------

    /** Convert local sub-level BlockPos to global world-space Vec3 (block center). */
    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return Vec3.atCenterOf(pos);
        return SableUtils.getWorldVec(level, pos);
    }

    /** Convert local sub-level Vec3 to global world-space Vec3. */
    public static Vec3 getWorldVec(Level level, Vec3 vec) {
        if (!Mods.SABLE.isLoaded()) return vec;
        return SableUtils.getWorldVec(level, vec);
    }

    /** Convert a BlockEntity's local position to world-space Vec3. */
    public static Vec3 getWorldVec(BlockEntity be) {
        if (!Mods.SABLE.isLoaded()) return Vec3.atCenterOf(be.getBlockPos());
        return SableUtils.getWorldVec(be);
    }

    /** Convert local sub-level BlockPos to global world-space BlockPos. */
    public static Vec3 getWorldPos(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return Vec3.atCenterOf(pos);
        return SableUtils.getWorldVec(level, pos);
    }

    // -----------------------------------------------------------------------
    // Direction / rotation transforms
    // -----------------------------------------------------------------------

    /** Rotate a direction vector from sub-level local space into world space. */
    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded()) return vec3;
        return SableUtils.getWorldVecDirectionTransform(vec3, be);
    }

    /** Rotate a world-space direction vector into sub-level local space. */
    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (!Mods.SABLE.isLoaded()) return vec3;
        return SableUtils.getShipVec(vec3, be);
    }

    // -----------------------------------------------------------------------
    // Orientation / velocity
    // -----------------------------------------------------------------------

    public static float getShipYawDeg(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return 0f;
        return SableUtils.getShipYawDeg(level, pos);
    }

    public static float getShipYawDeg(BlockEntity be) {
        if (!Mods.SABLE.isLoaded()) return 0f;
        return SableUtils.getShipYawDeg(be);
    }

    public static Vec3 getSubLevelVelocity(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return Vec3.ZERO;
        return SableUtils.getVelocity(level, pos);
    }

    public static Level getParentLevel(Level level, BlockPos pos) {
        if (!Mods.SABLE.isLoaded()) return null;
        return SableUtils.getParentLevel(level, pos);
    }

    /** Legacy overload kept for callers that pass a raw Object sub-level. */
    public static Vec3 getSubLevelVelocity(Object subLevel) {
        return Vec3.ZERO;
    }
    public static String getShipNamespace(Object subLevel) {
        if (!Mods.SABLE.isLoaded()) return "";
        if (subLevel instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            return SableUtils.getSubLevelNamespace(sl);
        }
        return "";
    }

    public static String getShipName(Object subLevel) {
        if (!Mods.SABLE.isLoaded()) return "";
        if (subLevel instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            return SableUtils.getSubLevelName(sl);
        }
        return "";
    }
}
