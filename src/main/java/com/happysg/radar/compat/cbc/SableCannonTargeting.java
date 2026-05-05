package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.aeronautics.AeronauticsUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.List;

public class SableCannonTargeting {
    
    public static List<List<Double>> calculatePitchAndYawSable(CannonMountBlockEntity mount, Vec3 targetPos, ServerLevel level) {
        if (mount == null || targetPos == null) return null;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) return null;
        AbstractMountedCannonContraption cannonContraption = null;
        if (contraption.getContraption() instanceof AbstractMountedCannonContraption _c) cannonContraption = _c;
        if (cannonContraption == null) return null;

        Vec3 mountPos = mount.getBlockPos().getCenter();
        int barrelLength = CannonUtil.getBarrelLength(cannonContraption);
        Direction initialDirection = cannonContraption.initialOrientation();

        float speed = CannonUtil.getInitialVelocity(cannonContraption, level);
        double drag = CannonUtil.getProjectileDrag(cannonContraption, level);
        double gravity = CannonUtil.getProjectileGravity(cannonContraption, level);

        if (speed <= 0) {
            Vec3 diff = targetPos.subtract(mountPos);
            double horizontal = Math.hypot(diff.x, diff.z);
            double pitch = Math.toDegrees(Math.atan2(diff.y, horizontal));
            double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x));
            return List.of(List.of(pitch, yaw));
        }

        var subLevel = Sable.HELPER.getContaining(level, mount.getBlockPos());
        if (subLevel == null) return null;

        var pose = subLevel.logicalPose();
        org.joml.Quaterniond q = new org.joml.Quaterniond();
        q.set(pose.orientation());
        Vector3d eulerAngles = new Vector3d();
        q.getEulerAnglesYXZ(eulerAngles);

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

        // We can reuse VS2TargetingSolver if we pass a fake "Ship" or modify it to take Pose
        // For now, let's see if we can use the same math
        SableTargetingSolver targetingSolver = new SableTargetingSolver(level, speed, drag, gravity, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, subLevel);
        return targetingSolver.solveThetaZeta();
    }
}
