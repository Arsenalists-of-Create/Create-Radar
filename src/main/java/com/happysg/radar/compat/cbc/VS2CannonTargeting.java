package com.happysg.radar.compat.cbc;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.List;

public class VS2CannonTargeting {
    private static List<List<Double>> directAimToTarget(Vec3 mountPos, Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(mountPos);
        double horizontal = Math.hypot(diff.x, diff.z);
        double pitch = Math.toDegrees(Math.atan2(diff.y, horizontal));
        double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x));
        if (yaw < 0) {
            yaw += 360.0;
        }
        return List.of(List.of(pitch, yaw));
    }

    public static List<List<Double>> calculatePitchAndYawVS2(CannonMountBlockEntity mount, Vec3 targetPos, net.minecraft.server.level.ServerLevel level) {
        if (mount == null || targetPos == null) {
            return null;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) return null;
        AbstractMountedCannonContraption cannonContraption = null;
        if (contraption.getContraption() instanceof AbstractMountedCannonContraption _c) cannonContraption = _c;
        if (cannonContraption == null) return null;

        Vec3 mountPos = mount.getBlockPos().getCenter();
        int barrelLength = CannonUtil.getBarrelLength(cannonContraption);
        Direction initialDirection = cannonContraption.initialOrientation();

        if (CannonUtil.isLaserCannon(cannonContraption)) {
            return calculatePitchAndYawVS2(level, 10000.0, targetPos, mountPos, barrelLength, initialDirection, 0.0, 0.0);
        }

        float chargePower = CannonUtil.getInitialVelocity(cannonContraption, level);
        double drag = CannonUtil.getProjectileDrag(cannonContraption, level);
        double gravity = CannonUtil.getProjectileGravity(cannonContraption, level);

        if (chargePower <= 0) {
            return directAimToTarget(mountPos, targetPos);
        }

        return calculatePitchAndYawVS2(level, chargePower, targetPos, mountPos, barrelLength, initialDirection, drag, gravity);
    }

    public static List<List<Double>> calculatePitchAndYawVS2(net.minecraft.world.level.Level level, double speed, Vec3 targetPos, Vec3 mountPos, int barrelLength, Direction initialDirection, double drag, double gravity) {
        LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level,mountPos.x,mountPos.y,mountPos.z);
        if (ship == null) {
            return null;
        }
        Vector3d eulerAngles = new Vector3d();
        ship.getTransform().getShipToWorldRotation().getEulerAnglesYXZ(eulerAngles);
        double x = eulerAngles.x;
        double z = eulerAngles.z;
        double initialZeta = -eulerAngles.y; // Yaw
        double initialPsi = 0; // Roll
        double initialTheta = 0; // Pitch

        if (initialDirection == Direction.NORTH) {
            initialPsi = -z;
            initialTheta = x;
        } else if (initialDirection == Direction.SOUTH) {
            initialPsi = z;
            initialTheta = -x;
        } else if (initialDirection == Direction.EAST) {
            initialPsi = x;
            initialTheta = z;
        } else if (initialDirection == Direction.WEST) {
            initialPsi = -x;
            initialTheta = -z;
        }

        VS2TargetingSolver targetingSolver = new VS2TargetingSolver(level, speed, drag, gravity, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, ship);
        return targetingSolver.solveThetaZeta();
    }
}