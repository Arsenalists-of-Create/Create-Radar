package com.happysg.radar.block.guidance;

import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

    public boolean canFire(PitchOrientedContraptionEntity pitchOrientedContraptionEntity) {
        checkForTarget(pitchOrientedContraptionEntity.level());
        return target != null;
    }

    private void checkForTarget(Level server) {
        if (monitorPos == null) return;
        if (server.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor) {
            target = monitor.getController().getTargetPos(TargetingConfig.DEFAULT);
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

    @Override
    public void loadAdditional(CompoundTag pTag, HolderLookup.Provider provider) {
        super.loadAdditional(pTag, provider);
        if (pTag.contains("monitorPos")) {
            monitorPos = BlockPos.of(pTag.getLong("monitorPos"));
        }
        if (pTag.contains("target")) {
            int[] targetArray = pTag.getIntArray("target");
            target = new Vec3(targetArray[0], targetArray[1], targetArray[2]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider provider) {
        super.saveAdditional(pTag, provider);
        if (monitorPos != null) {
            pTag.putLong("monitorPos", monitorPos.asLong());
        }
        if (target != null) {
            pTag.putIntArray("target", new int[]{(int) target.x, (int) target.y, (int) target.z});
        }
    }

}
