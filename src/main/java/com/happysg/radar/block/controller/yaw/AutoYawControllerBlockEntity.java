package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
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
        if (level == null || level.isClientSide())
            return;
        if (!isRunning)
            return;
        CannonMountBlockEntity mount = getCannonMount();
        if (mount == null)
            return;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return;

        double currentYaw = contraption.yaw;
        if (currentYaw == targetAngle) {
            isRunning = false;
            return;
        }

        // Normalize both currentYaw and targetAngle to [0, 360)
        currentYaw = (currentYaw + 360) % 360;
        targetAngle = (targetAngle + 360) % 360;

        double yawDifference = targetAngle - currentYaw;
        // Normalize yawDifference to range [-180, 180]
        yawDifference = (yawDifference + 180) % 360 - 180;

        if (yawDifference > 180) {
            yawDifference -= 360; // Rotate counterclockwise
        } else if (yawDifference < -180) {
            yawDifference += 360; // Rotate clockwise
        }

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

        // Set the new yaw back to the contraption
        mount.setYaw((float) ((currentYaw + 360) % 360)); // Normalize back to [0, 360] if necessary
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
        if (level == null || level.isClientSide())
            return;
        if (PhysicsHandler.isBlockInShipyard(level, this.getBlockPos()))
            return;
        if (targetPos == null) {
            isRunning = false;
            return;
        }

        isRunning = true;
        Vec3 cannonCenter = getBlockPos().relative(getBlockState().getValue(AutoYawControllerBlock.FACING), 3).getCenter();
        double dx = cannonCenter.x - targetPos.x;
        double dz = cannonCenter.z - targetPos.z;

        targetAngle = Math.toDegrees(Math.atan2(dz, dx)) + 90;
        // Normalize yaw to 0-360 degrees
        if (targetAngle < 0) {
            targetAngle += 360;
        }
        notifyUpdate();
    }

    public CannonMountBlockEntity getCannonMount() {
        BlockPos pos = getBlockPos().relative(getBlockState().getValue(AutoYawControllerBlock.FACING));
        if (level != null && level.getBlockEntity(pos) instanceof CannonMountBlockEntity mount) {
            return mount;
        }
        return null;
    }

    public boolean atTargetYaw() {
        CannonMountBlockEntity mount = getCannonMount();
        if (mount == null)
            return false;
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return false;
        return Math.abs(contraption.yaw - targetAngle) < TOLERANCE;
    }
}
