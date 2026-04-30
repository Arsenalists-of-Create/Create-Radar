package com.happysg.radar.compat;

import com.happysg.radar.compat.aeronautics.AeronauticsUtils;
import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import com.happysg.radar.compat.vs2.VS2Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Unified Physics Handler for Valkyrien Skies 2 and Create Aeronautics based simulation environments.
 */
public class PhysicsHandler {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            if (VS2Utils.isBlockInShipyard(level, pos))
                return BlockPos.containing(VS2Utils.getWorldPos(level, pos));
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            if (AeronauticsUtils.isBlockInShipyard(level, pos))
                return BlockPos.containing(AeronauticsUtils.getWorldPos(level, pos));
        }
        return pos;
    }

    public static Vec3 getShipVec(Vec3 vec3, BlockEntity be) {
        if (Mods.VALKYRIENSKIES.isLoaded())
            return VS2Utils.getShipVec(vec3, be);
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded())
            return AeronauticsUtils.getShipVec(vec3, be);
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        if (Mods.VALKYRIENSKIES.isLoaded())
            return VS2Utils.getWorldVecDirectionTransform(vec3, be);
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded())
            return AeronauticsUtils.getWorldVecDirectionTransform(vec3, be);
        return vec3;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            if (VS2Utils.isBlockInShipyard(level, pos))
                return VS2Utils.getWorldVec(level, pos);
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            if (AeronauticsUtils.isBlockInShipyard(level, pos))
                return AeronauticsUtils.getWorldVec(level, pos);
        }

        // Standard Create Contraptions support
        for (com.simibubi.create.content.contraptions.AbstractContraptionEntity entity : level.getEntitiesOfClass(com.simibubi.create.content.contraptions.AbstractContraptionEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2))) {
            if (entity.getContraption() != null && entity.getContraption().getBlocks().containsKey(pos)) {
                return entity.toGlobalVector(Vec3.atCenterOf(pos), 1.0f);
            }
        }

        return Vec3.atCenterOf(pos);
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3) {
        if (Mods.VALKYRIENSKIES.isLoaded())
            return VS2Utils.getWorldVec(level, vec3);
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded())
            return AeronauticsUtils.getWorldVec(level, vec3);
        return vec3;
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        if (level == null) return Vec3.atCenterOf(pos);

        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            if (AeronauticsUtils.isBlockInShipyard(level, pos)) {
                return AeronauticsUtils.getWorldVec(level, pos);
            }
        }

        if (Mods.VALKYRIENSKIES.isLoaded()) {
            if (VS2Utils.isBlockInShipyard(level, pos)) {
                return VS2Utils.getWorldVec(blockEntity);
            }
        }

        return Vec3.atCenterOf(pos);
    }

    public static boolean isBlockInShipyard(Level level, BlockPos blockPos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            if (VS2Utils.isBlockInShipyard(level, blockPos)) return true;
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            if (AeronauticsUtils.isBlockInShipyard(level, blockPos)) return true;
        }
        return false;
    }

    public static boolean isShipAlive(Level level, String id) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            // VS2 ship alive check logic...
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            return AeronauticsUtils.isShipAlive(level, id);
        }
        return true;
    }

    public static float getShipYawDeg(Level level, BlockPos worldPosition) {
        if (level == null) return 0f;

        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Object ship = VS2Utils.getShipManagingPos(level, worldPosition);
            if (ship instanceof org.valkyrienskies.core.api.ships.Ship s) {
                org.joml.Quaterniondc rot = s.getTransform().getShipToWorldRotation();
                org.joml.Vector3d fwd = new org.joml.Vector3d(0, 0, 1);
                rot.transform(fwd);
                return (float) -Math.toDegrees(Math.atan2(fwd.x, fwd.z));
            }
        }

        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            return AeronauticsUtils.getShipYawDeg(level, worldPosition);
        }

        return 0f;
    }

    public static Vec3 getShipVelocity(Level level, BlockPos pos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Object ship = VS2Utils.getShipManagingPos(level, pos);
            if (ship instanceof org.valkyrienskies.core.api.ships.Ship s) {
                return VS2ShipVelocityTracker.getShipVelocityPerTick(s);
            }
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            Object subLevel = AeronauticsUtils.getSubLevelObjectAt(level, pos);
            if (subLevel != null) {
                return AeronauticsUtils.getSubLevelVelocity(subLevel);
            }
            Entity ship = AeronauticsUtils.getShipManagingPos(level, pos);
            if (ship != null) {
                return ship.getDeltaMovement();
            }
        }
        return Vec3.ZERO;
    }

    public static String getShipId(Level level, BlockPos pos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Object ship = VS2Utils.getShipManagingPos(level, pos);
            if (ship instanceof org.valkyrienskies.core.api.ships.Ship s) {
                return String.valueOf(s.getId());
            }
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            Entity ship = AeronauticsUtils.getShipManagingPos(level, pos);
            if (ship != null) {
                return ship.getUUID().toString();
            }
        }
        return null;
    }

    public static boolean isAeronauticsShip(Entity entity) {
        return (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded() || Mods.SABLE.isLoaded()) && AeronauticsUtils.isShip(entity);
    }

    public static Object getShipManagingPos(Level level, BlockPos pos) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            Object res = VS2Utils.getShipManagingPos(level, pos);
            if (res != null) return res;
        }
        if (Mods.AERONAUTICS.isLoaded() || Mods.SIMULATED.isLoaded()) {
            return AeronauticsUtils.getShipManagingPos(level, pos);
        }
        return null;
    }

    public static Vec3 getScanningVec(BlockEntity blockEntity) {
        return blockEntity.getBlockPos().getCenter();
    }
}
