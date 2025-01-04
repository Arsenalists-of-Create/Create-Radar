package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;


public class AutoYawControllerBlockEntity extends GeneratingKineticBlockEntity {
    private static final double TOLERANCE = 0.1;
    private double targetAngle;
    private boolean isRunning;

    public AutoYawControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (Mods.CREATEBIGCANNONS.isLoaded())
            tryRotateCannon();

    }

    private void tryRotateCannon() {
        if (level.isClientSide())
            return;
        if (!isRunning)
            return;
        if (!(level.getBlockEntity(getBlockPos().above()) instanceof CannonMountBlockEntity mount))
            return;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return;

        double currentYaw = contraption.yaw;
        if (currentYaw == targetAngle)
            return;

        double yawDifference = targetAngle - currentYaw;
        double speedFactor = Math.abs(getSpeed()) / 32.0;


        if (Math.abs(yawDifference) > TOLERANCE) {
            if (Math.abs(yawDifference) > speedFactor) {
                currentYaw += Math.signum(yawDifference) * speedFactor;
            } else {
                currentYaw = targetAngle;
            }
        } else {
            currentYaw = targetAngle;
        }

        mount.setYaw((float) currentYaw);
        mount.notifyUpdate();
    }



    public void setTargetAngle(float targetAngle) {
        this.targetAngle = targetAngle;
        notifyUpdate();
    }


    public double getTargetAngle() {
        return targetAngle;
    }

    @Override
    protected void copySequenceContextFrom(KineticBlockEntity sourceBE) {
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        targetAngle = compound.getDouble("TargetAngle");
        isRunning = compound.getBoolean("IsRunning");
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putDouble("TargetAngle", targetAngle);
        compound.putBoolean("IsRunning", isRunning);
    }

    public void setTarget(Vec3 targetPos) {
        if (level.isClientSide())
            return;
        if (targetPos == null) {
            isRunning = false;
            return;
        }
        isRunning = true;
        Vec3 cannonCenter = getBlockPos().above(3).getCenter();
        double dx = cannonCenter.x - targetPos.x;
        double dz = cannonCenter.z - targetPos.z;

        targetAngle = Math.toDegrees(Math.atan2(dz, dx)) + 90;
        // Normalize yaw to 0-360 degrees
        if (targetAngle < 0) {
            targetAngle += 360;
        }
        notifyUpdate();
    }

    public boolean atTargetYaw() {
        BlockPos turretPos = getBlockPos().above();
        if (!(level.getBlockEntity(turretPos) instanceof CannonMountBlockEntity mount))
            return false;
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return false;
        return Math.abs(contraption.yaw - targetAngle) < TOLERANCE;
    }
}