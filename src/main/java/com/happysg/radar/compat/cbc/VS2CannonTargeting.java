package com.happysg.radar.compat.cbc;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
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

    public static List<List<Double>> calculatePitchAndYawVS2(CannonMountBlockEntity mount, Vec3 targetPos, ServerLevel level) {
        if (mount == null || targetPos == null) {
            return null;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return null;
        }

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

    public static List<List<Double>> calculatePitchAndYawVS2(Level level, double speed, Vec3 targetPos, Vec3 mountPos, int barrelLength, Direction initialDirection, double drag, double gravity) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, mountPos);
        if (ship == null) {
            System.out.println("null");
            return null;
        }
        Vector3d right = ship.logicalPose().transformNormal(new Vector3d(1, 0, 0));
        Vector3d up    = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0));
        Vector3d fwd   = ship.logicalPose().transformNormal(new Vector3d(0, 0, 1));
        Matrix3d rot = new Matrix3d(right.x, right.y, right.z, up.x, up.y, up.z, fwd.x, fwd.y, fwd.z);
        Vector3d eulerAngles = new Vector3d();
        new Quaterniond().setFromNormalized(rot).getEulerAnglesYXZ(eulerAngles);
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
