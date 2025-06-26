package com.happysg.radar.block.controller.firing;

import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.screens.TargetingConfig;
import com.happysg.radar.compat.cbc.CannonUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.happysg.radar.compat.cbc.CannonTargeting.calculateProjectileYatX;

public class FiringControlBlockEntity {

    TargetingConfig targetingConfig = TargetingConfig.DEFAULT;
    Vec3 target;
    boolean firing;
    public CannonMountBlockEntity cannonMount;
    AutoPitchControllerBlockEntity pitchController;
    Level level;
    public List<AABB> safeZones = new ArrayList<>();


    public FiringControlBlockEntity(AutoPitchControllerBlockEntity controller, CannonMountBlockEntity cannonMount) {
        this.cannonMount = cannonMount;
        this.pitchController = controller;
        this.level = cannonMount.getLevel();
    }
    public void setSafeZones(List<AABB> safeZones) {
        this.safeZones = safeZones;
    }

    public void tick() {
        if (isTargetInRange() && targetingConfig.autoFire()) {
            tryFireCannon();
        } else {
            stopFireCannon();
        }
    }

    private boolean isTargetInRange() {
        return target != null && hasCorrectYawPitch() && !passesSafeZone();
    }
    private boolean passesSafeZone() {
        if(!(level instanceof ServerLevel)) { return false; }
        for (AABB aabb : safeZones) {
            if(aabb.contains(target)){
                return true;
            }
            Vec3 cannonPos = cannonMount.getBlockPos().getCenter();

            Optional<Vec3> optionalMinXMinY = aabb.clip(new Vec3(cannonPos.x, aabb.minY, cannonPos.z), new Vec3(target.x, aabb.minY, target.z));
            Optional<Vec3> optionalMaxXMinY = aabb.clip(new Vec3(target.x, aabb.minY, target.z), new Vec3(cannonPos.x, aabb.minY, cannonPos.z));
            if (optionalMinXMinY.isEmpty() || optionalMaxXMinY.isEmpty()) return false;
            Vec3 minX = optionalMinXMinY.get();
            Vec3 maxX = optionalMaxXMinY.get();

            double yMax = aabb.maxY - cannonMount.getBlockPos().getY();
            double yMin = aabb.minY - cannonMount.getBlockPos().getY();
            PitchOrientedContraptionEntity contraption = cannonMount.getContraption();
            if ( contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
                return false;
            }
            float speed = CannonUtil.getInitialVelocity(cannonContraption, (ServerLevel)  level);
            double drag = CannonUtil.getProjectileDrag(cannonContraption, (ServerLevel) level);
            double gravity = CannonUtil.getProjectileGravity(cannonContraption, (ServerLevel) level);
            int barrelLength = CannonUtil.getBarrelLength(cannonContraption);

            double thetaRad = Math.toRadians(cannonMount.getDisplayPitch());
            double projectileYatMinX = calculateProjectileYatX(speed, cannonPos.distanceTo(minX) - barrelLength*Math.cos(thetaRad), thetaRad, drag, gravity);
            double projectileYatMaxX = calculateProjectileYatX(speed, cannonPos.distanceTo(maxX) - barrelLength*Math.cos(thetaRad), thetaRad, drag, gravity);

            if((projectileYatMinX >= yMin && projectileYatMaxX <= yMax) || (projectileYatMinX<= yMin && projectileYatMaxX >= yMin)){
                return true;
            }
        }
        return false;
    }

    private boolean hasCorrectYawPitch() {
        BlockPos yawControllerPos = cannonMount.getBlockPos().below();
        if (! (level.getBlockEntity(yawControllerPos) instanceof AutoYawControllerBlockEntity) ) {
            yawControllerPos = cannonMount.getBlockPos().above();
        }
        if (level.getBlockEntity(yawControllerPos) instanceof AutoYawControllerBlockEntity yawController && pitchController != null) {
            return yawController.atTargetYaw() && pitchController.atTargetPitch();
        }
        return false;
    }

    public void setTarget(Vec3 target, TargetingConfig config) {
        if (target == null)
            stopFireCannon();
        this.target = target;
        this.targetingConfig = config;
    }

    private void stopFireCannon() {
        cannonMount.onRedstoneUpdate(true, true, false, true, 0);
        firing = false;
    }

    private void tryFireCannon() {
        cannonMount.onRedstoneUpdate(true, true, true, false, 15);
        firing = true;
    }


}
