package com.happysg.radar.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AeronauticsUtils {

    public static boolean isShip(Entity entity) {
        if (entity == null) return false;
        String name = entity.getClass().getName();
        return name.contains("com.github.talrey.createdieselgenerators") || name.contains("aeronautics");
    }

    public static Entity getShipManagingPos(Level level, BlockPos pos) {
        return null;
    }

    public static boolean isShipAlive(Level level, String id) {
        return true;
    }

    public static float getShipYawDeg(Level level, BlockPos pos) {
        return 0f;
    }

    public static Vec3 getSubLevelVelocity(Object subLevel) {
        return Vec3.ZERO;
    }

    public static Object getSubLevelObjectAt(Level level, BlockPos pos) {
        try {
            Class<?> targetClass = Class.forName("dev.ryanhcode.sable.Sable");
            Field helperField = targetClass.getField("HELPER");
            Object helper = helperField.get(null);
            Method getContaining = helper.getClass().getMethod("getContaining", Level.class, BlockPos.class);
            return getContaining.invoke(helper, level, pos);
        } catch (Exception e) {
            return null;
        }
    }

    public static Vec3 getWorldPos(Level level, BlockPos pos) {
        Object subLevel = getSubLevelObjectAt(level, pos);
        if (subLevel instanceof Level sl) {
             try {
                 // Attempt to retrieve world-space origin from the sub-level environment
                 Method getOrigin = sl.getClass().getMethod("getOrigin");
                 Object origin = getOrigin.invoke(sl);
                 if (origin instanceof BlockPos originPos) {
                     return Vec3.atCenterOf(pos.offset(originPos));
                 }
             } catch (Exception ignored) {}
        }
        return Vec3.atCenterOf(pos);
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        return getWorldPos(level, pos);
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec) {
        return getWorldVec(level, BlockPos.containing(vec));
    }

    public static Vec3 getShipVec(Vec3 vec3, net.minecraft.world.level.block.entity.BlockEntity be) {
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, net.minecraft.world.level.block.entity.BlockEntity be) {
        return vec3;
    }

    public static boolean isBlockInShipyard(Level level, BlockPos pos) {
        return getSubLevelObjectAt(level, pos) != null;
    }
}
