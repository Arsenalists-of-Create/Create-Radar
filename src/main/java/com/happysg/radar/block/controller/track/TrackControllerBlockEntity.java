package com.happysg.radar.block.controller.track;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.valkyrienskies.core.api.ships.LoadedShip;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

public class TrackControllerBlockEntity extends SplitShaftBlockEntity {

    public Vec3 target;
    public float leftSpeed;
    public float rightSpeed;

    public TrackControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face == getSourceFacing())
            return 1f;
        if (target == null)
            return 0;
        if (face == getSourceFacing().getCounterClockWise())
            return leftSpeed;
        if (face == getSourceFacing().getClockWise())
            return rightSpeed;
        return 1f;
    }


    @Override
    public Direction getSourceFacing() {
        return getBlockState().getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public void tick() {
        super.tick();
        boolean changed = false;
        //testing
        if (Math.abs(getAngleDifference()) < 20) {
            if (leftSpeed == 1 || rightSpeed == 1)
                changed = true;
            leftSpeed = 0;
            rightSpeed = 0;
        } else {
            if (getAngleDifference() < 180) {
                if (leftSpeed == 0)
                    changed = true;
                leftSpeed = 1;
                rightSpeed = 0;
            } else {
                if (rightSpeed == 0)
                    changed = true;
                leftSpeed = 0;
                rightSpeed = 1;
            }
        }
        if (changed) {
            detachKinetics();
            attachKinetics();
            updateSpeed = true;
            notifyUpdate();
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        compound.getFloat("leftSpeed");
        compound.getFloat("rightSpeed");
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("leftSpeed", leftSpeed);
        compound.putFloat("rightSpeed", rightSpeed);
    }

    private double getAngleDifference() {
        return getAngleToTarget() - getYaw();
    }

    private double getAngleToTarget() {
        if (target == null)
            return getYaw();
        Vec3 center = VS2Utils.getWorldVec(this);
        Vec3 relative = center.subtract(target);
        double yaw = Math.toDegrees(Math.atan2(relative.z, relative.x)) - 90;
        yaw = (yaw + 360) % 360; // Normalize to range [0, 360)
        return yaw;
    }

    private double getYaw() {
        return ((toYRot() - getAngleOffsetToWorld() + 360)) % 360;
    }

    private double getAngleOffsetToWorld() {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return 0;
        LoadedShip ship = VS2Utils.getShipManagingPos(this);
        if (ship == null)
            return 0;
        Quaterniondc quaterniondc = ship.getTransform().getShipToWorldRotation();
        // Extract yaw directly from quaternion
        double qw = quaterniondc.w();
        double qx = quaterniondc.x();
        double qy = quaterniondc.y();
        double qz = quaterniondc.z();

        // Calculate yaw in radians
        double yaw = Math.atan2(2.0 * (qw * qy + qx * qz), 1.0 - 2.0 * (qy * qy + qz * qz));

        // Convert to degrees and normalize to range [-180, 180]
        yaw = Math.toDegrees(yaw);
        yaw = (yaw + 360) % 360; // Normalize to range [0, 360)
        return yaw;
    }

    private double toYRot() {
        Direction direction = getBlockState().getValue(HORIZONTAL_FACING);
        return switch (direction) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
            default -> 0;
        };
    }

    public void setTarget(Vec3 targetPos) {
        this.target = targetPos;
    }

    public Vec3 getTarget() {
        return target;
    }
}
