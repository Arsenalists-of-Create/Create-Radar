package com.happysg.radar.compat.cbc.controller;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

import java.util.Optional;

public class CannonControllerBlockEntity extends KineticBlockEntity {

    Vec3 requestedTarget;
    Vec3 currentTarget;
    double currentYaw;
    double currentPitch;
    double targetYaw;
    double targetPitch;

    public CannonControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() == 0)
            return;
        if (Mods.CREATEBIGCANNONS.isLoaded())
            getCannon().ifPresent(this::aimCannonAtTarget);
    }

    private void aimCannonAtTarget(CannonMountBlockEntity cannon) {

        if (requestedTarget == null || requestedTarget.equals(Vec3.ZERO))
            return;

        if (currentTarget != null && currentTarget.equals(requestedTarget))
            return;

        if (cannon.getContraption() == null)
            return;

        Vec3 cannonCenter = getBlockPos().above(2).getCenter();
        double dx = requestedTarget.x - cannonCenter.x;
        double dy = requestedTarget.y - cannonCenter.y;
        double dz = requestedTarget.z - cannonCenter.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        targetPitch = Math.toDegrees(Math.atan2(dy, horizontalDistance));

        // Normalize yaw to 0-360 degrees
        if (targetYaw < 0) {
            targetYaw += 360;
        }

        // Ensure pitch is within -90 to 90 degrees
        if (targetPitch < -90) {
            targetPitch = -90;
        } else if (targetPitch > 90) {
            targetPitch = 90;
        }

        double yawDifference = targetYaw - currentYaw;
        double pitchDifference = targetPitch - currentPitch;

        double speedFactor = Math.abs(getSpeed()) / 100.0;

        if (Math.abs(yawDifference) > speedFactor) {
            currentYaw += Math.signum(yawDifference) * speedFactor;
        } else {
            currentYaw = targetYaw;
        }

        if (Math.abs(pitchDifference) > speedFactor) {
            currentPitch += Math.signum(pitchDifference) * speedFactor;
        } else {
            currentPitch = targetPitch;
        }

        cannon.setYaw((float) currentYaw);
        cannon.setPitch((float) currentPitch);
        cannon.getContraption().yaw = (float) currentYaw;
        cannon.getContraption().pitch = (float) currentPitch;
        currentTarget = requestedTarget;
        cannon.notifyUpdate();
    }

    private Optional<CannonMountBlockEntity> getCannon() {
        BlockEntity be = level.getBlockEntity(getBlockPos().above());
        if (be == null)
            return Optional.empty();
        if (be instanceof CannonMountBlockEntity)
            return Optional.of((CannonMountBlockEntity) be);
        return Optional.empty();
    }

    public void setTarget(Vec3 pos) {
        if (pos == null)
            return;
        if (requestedTarget != null && requestedTarget.equals(pos))
            return;
        this.requestedTarget = pos;

        Vec3 cannonCenter = getBlockPos().above(2).getCenter();
        double dx = pos.x - cannonCenter.x;
        double dy = pos.y - cannonCenter.y;
        double dz = pos.z - cannonCenter.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        targetPitch = Math.toDegrees(Math.atan2(dy, horizontalDistance));

        // Normalize yaw to 0-360 degrees
        if (targetYaw < 0) {
            targetYaw += 360;
        }

        // Ensure pitch is within -90 to 90 degrees
        if (targetPitch < -90) {
            targetPitch = -90;
        } else if (targetPitch > 90) {
            targetPitch = 90;
        }

        notifyUpdate();
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (compound.contains("targetx")) {
            requestedTarget = new Vec3(compound.getDouble("targetx"), compound.getDouble("targety"), compound.getDouble("targetz"));
        }
        if (compound.contains("currentx")) {
            currentTarget = new Vec3(compound.getDouble("currentx"), compound.getDouble("currenty"), compound.getDouble("currentz"));
        }
        if (compound.contains("currentYaw")) {
            currentYaw = compound.getDouble("currentYaw");
        }
        if (compound.contains("currentPitch")) {
            currentPitch = compound.getDouble("currentPitch");
        }
        if (compound.contains("targetYaw")) {
            targetYaw = compound.getDouble("targetYaw");
        }
        if (compound.contains("targetPitch")) {
            targetPitch = compound.getDouble("targetPitch");
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        if (requestedTarget != null) {
            compound.putDouble("targetx", requestedTarget.x);
            compound.putDouble("targety", requestedTarget.y);
            compound.putDouble("targetz", requestedTarget.z);
        }
        if (currentTarget != null) {
            compound.putDouble("currentx", currentTarget.x);
            compound.putDouble("currenty", currentTarget.y);
            compound.putDouble("currentz", currentTarget.z);
        }
        compound.putDouble("currentYaw", currentYaw);
        compound.putDouble("currentPitch", currentPitch);
        compound.putDouble("targetYaw", targetYaw);
        compound.putDouble("targetPitch", targetPitch);
    }
}
