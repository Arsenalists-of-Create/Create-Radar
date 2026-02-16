package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
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
    public static List<List<Double>> calculatePitchAndYawVS2(CannonMountBlockEntity mount, Vec3 targetPos, ServerLevel level) {
        if(!Mods.VALKYRIENSKIES.isLoaded()) return null;
        if (mount == null || targetPos == null) {
            return null;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return null;
        }
        float chargePower = CannonUtil.getInitialVelocity(cannonContraption, level);

        Vec3 mountPos;
        if(CannonUtil.isUp(mount)) {
            mountPos = mount.getBlockPos().above(3).getCenter();
        }else {
            mountPos = mount.getBlockPos().below(3).getCenter();
        }
        int barrelLength = CannonUtil.getBarrelLength(cannonContraption);
        Direction initialDirection = cannonContraption.initialOrientation();

        double drag = CannonUtil.getProjectileDrag(cannonContraption, level);
        double gravity = CannonUtil.getProjectileGravity(cannonContraption, level);

        return calculatePitchAndYawVS2(level, chargePower, targetPos, mountPos, barrelLength, initialDirection, drag, gravity);
    }

    public static List<List<Double>> calculatePitchAndYawVS2(Level level, double speed, Vec3 targetPos, Vec3 mountPos, int barrelLength, Direction initialDirection, double drag, double gravity) {
        //LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level,mountPos.x,mountPos.y,mountPos.z);
//        if (ship == null) {
//            System.out.println("null");
//            return null;
//        }
        Vector3d eulerAngles = new Vector3d();
        //ship.getTransform().getShipToWorldRotation().getEulerAnglesYXZ(eulerAngles);
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

        //VS2TargetingSolver targetingSolver = new VS2TargetingSolver(level, speed, drag, gravity, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, ship);
        //return targetingSolver.solveThetaZeta();
        return null;
    }
}
