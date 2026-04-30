package com.happysg.radar.block.guidance;

import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import riftyboi.cbcmodernwarfare.munitions.contraptions.MunitionsPhysicsContraptionEntity;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.GuidanceBlockEntity;

import javax.annotation.Nullable;

public class RadarGuidanceBlockEntity extends GuidanceBlockEntity {

    @Nullable
    private Vec3 target;
    private BlockPos monitorPos;

    public RadarGuidanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {}

    public boolean canFire(PitchOrientedContraptionEntity pitchOrientedContraptionEntity) {
        checkForTarget(pitchOrientedContraptionEntity.level());
        return target != null;
    }

    private void checkForTarget(Level server) {
        if (monitorPos == null) return;
        if (server.getBlockEntity(monitorPos) instanceof NetworkFiltererBlockEntity monitor) {
            if (monitor.activeTrackCache == null) return;
            target = monitor.activeTrackCache.getPosition();
        }
    }

    public void tickMissileGuidance(MunitionsPhysicsContraptionEntity missile) {
        if (missile.level().isClientSide) return;
        checkForTarget(missile.level());
        if (target == null) return;
        Vec3 missilePos = missile.position();
        Vec3 targetPos = target;
        Vec3 targetVelocity = Vec3.ZERO;
        Vec3 missileVelocity = missile.getDeltaMovement();
        Vec3 relativePos = targetPos.subtract(missilePos);
        Vec3 relativeVelocity = targetVelocity.subtract(missileVelocity);
        double missileSpeed = missileVelocity.length();
        double a = relativeVelocity.lengthSqr() - missileSpeed * missileSpeed;
        double b = 2.0 * relativePos.dot(relativeVelocity);
        double c = relativePos.lengthSqr();
        double discriminant = b * b - 4.0 * a * c;
        double timeToIntercept;
        double distance;
        if (discriminant > 0.0 && a != 0.0) {
            distance = (-b + Math.sqrt(discriminant)) / (2.0 * a);
            double t2 = (-b - Math.sqrt(discriminant)) / (2.0 * a);
            timeToIntercept = Math.min(distance, t2) > 0.0 ? Math.min(distance, t2) : Math.max(distance, t2);
        } else {
            distance = missile.position().distanceTo(target);
            timeToIntercept = distance / missileSpeed;
        }

        Vec3 interceptPoint = targetPos.add(targetVelocity.scale(timeToIntercept));
        Vec3 directionToIntercept = interceptPoint.subtract(missilePos).normalize();
        Vec3 currentDirection = missileVelocity.normalize();
        currentDirection.dot(directionToIntercept);
        double turningRate = this.calculateTurningSpeed(missile.getContraption().getBlocks().size(), missileSpeed, getBlockState());
        Vec3 adjustedDirection = currentDirection.lerp(directionToIntercept, turningRate).normalize();
        missile.setContraptionMotion(adjustedDirection.scale(missileSpeed));
    }

    private double calculateTurningSpeed(int blocks, double speed, BlockState state) {
        return 0.1; // Default stub value
    }

    public void read(CompoundTag pTag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        // super.read removed
        if (pTag.contains("filtererPos")) {
            monitorPos = BlockPos.of(pTag.getLong("filtererPos"));
        }
        if (pTag.contains("target")) {
            int[] targetArray = pTag.getIntArray("target");
            target = new Vec3(targetArray[0], targetArray[1], targetArray[2]);
        }
    }

    public void write(CompoundTag pTag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        // super.write removed
        if (monitorPos != null) {
            pTag.putLong("filtererPos", monitorPos.asLong());
        }
        if (target != null) {
            pTag.putIntArray("target", new int[]{(int) target.x, (int) target.y, (int) target.z});
        }
    }

}