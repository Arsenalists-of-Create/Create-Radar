package com.happysg.radar.block.radar.skyradar;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.config.server.RadarServerConfig;
import com.happysg.radar.registry.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SkyRadarContraptionEntity extends ControlledContraptionEntity {
    public static final float PITCH_DEGREES = 15;
    private float pitch = PITCH_DEGREES;
    private float prevPitch = PITCH_DEGREES;
    private Direction receiverFacing = Direction.NORTH;
    private boolean targetLocked;

    public SkyRadarContraptionEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public static SkyRadarContraptionEntity create(Level world, IControlContraption controller, Contraption contraption) {
        SkyRadarContraptionEntity entity = new SkyRadarContraptionEntity(ModEntityTypes.SKY_RADAR_CONTRAPTION.get(), world);
        entity.controllerPos = controller.getBlockPosition();
        entity.setContraption(contraption);
        entity.setRotationAxis(Axis.Y);
        entity.pitch = PITCH_DEGREES;
        entity.prevPitch = PITCH_DEGREES;
        if (contraption instanceof SkyRadarContraption skyRadarContraption) {
            entity.receiverFacing = skyRadarContraption.getReceiverFacing();
        }
        return entity;
    }

    @Override
    protected void readAdditional(CompoundTag compound, boolean spawnPacket) {
        super.readAdditional(compound, spawnPacket);
        if (compound.contains("Pitch")) {
            pitch = compound.getFloat("Pitch");
            prevPitch = pitch;
        }
        if (compound.contains("ReceiverFacing", Tag.TAG_INT)) {
            receiverFacing = Direction.from3DDataValue(compound.getInt("ReceiverFacing"));
        }
        targetLocked = compound.getBoolean("TargetLocked");
    }

    @Override
    protected void writeAdditional(CompoundTag compound, HolderLookup.Provider registries, boolean spawnPacket) {
        super.writeAdditional(compound, registries, spawnPacket);
        compound.putFloat("Pitch", pitch);
        compound.putInt("ReceiverFacing", receiverFacing.get3DDataValue());
        compound.putBoolean("TargetLocked", targetLocked);
    }

    @Override
    public ContraptionRotationState getRotationState() {
        ContraptionRotationState state = super.getRotationState();
        if (targetLocked) {
            state.xRotation = getTargetPitchDegrees();
        } else if (getTiltAxis() == Axis.X) {
            state.xRotation = getFormedTiltDegrees();
        } else {
            state.zRotation = getFormedTiltDegrees();
        }
        return state;
    }

    @Override
    public Vec3 applyRotation(Vec3 localPos, float partialTicks) {
        localPos = super.applyRotation(localPos, partialTicks);
        return targetLocked
                ? VecHelper.rotate(localPos, getTargetPitchDegrees(partialTicks), Axis.X)
                : VecHelper.rotate(localPos, getFormedTiltDegrees(partialTicks), getTiltAxis());
    }

    @Override
    public Vec3 reverseRotation(Vec3 localPos, float partialTicks) {
        localPos = targetLocked
                ? VecHelper.rotate(localPos, -getTargetPitchDegrees(partialTicks), Axis.X)
                : VecHelper.rotate(localPos, -getFormedTiltDegrees(partialTicks), getTiltAxis());
        return super.reverseRotation(localPos, partialTicks);
    }

    public void setYaw(float yaw) {
        setAngle(yaw);
    }

    public void setTargetAim(float yaw, float pitch) {
        targetLocked = true;
        prevPitch = this.pitch;
        this.pitch = pitch;
        setYaw(yaw);
    }

    public void clearTargetAim(float yaw) {
        targetLocked = false;
        prevPitch = this.pitch;
        this.pitch = PITCH_DEGREES;
        setYaw(yaw);
    }

    public float getPitch() {
        return pitch;
    }

    public Direction getReceiverFacing() {
        return receiverFacing;
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (!level().isClientSide()) {
            return;
        }
        for (Entity entity : getPassengers()) {
            positionRider(entity);
        }
    }

    @Override
    protected StructureTransform makeStructureTransform() {
        BlockPos offset = BlockPos.containing(getAnchorVec().add(.5, .5, .5));
        return new StructureTransform(offset, 0, 0, 0);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void applyLocalTransforms(PoseStack matrixStack, float partialTicks) {
        TransformStack.of(matrixStack)
                .nudge(getId())
                .center()
                .rotateYDegrees(getAngle(partialTicks))
                .rotateDegrees(targetLocked ? getTargetPitchDegrees(partialTicks) : getFormedTiltDegrees(partialTicks), targetLocked ? Axis.X : getTiltAxis())
                .uncenter();
    }

    private Axis getTiltAxis() {
        Direction facing = receiverFacing.getAxis().isHorizontal() ? receiverFacing : Direction.NORTH;
        return facing.getAxis() == Axis.Z ? Axis.X : Axis.Z;
    }

    private float getFormedTiltDegrees() {
        return getFormedTiltDegrees(1);
    }

    private float getFormedTiltDegrees(float partialTicks) {
        float interpolatedPitch = getInterpolatedPitch(partialTicks);
        Direction facing = receiverFacing.getAxis().isHorizontal() ? receiverFacing : Direction.NORTH;
        return switch (facing) {
            case NORTH, EAST -> -interpolatedPitch;
            case SOUTH, WEST -> interpolatedPitch;
            default -> -interpolatedPitch;
        };
    }

    private float getTargetPitchDegrees() {
        return getTargetPitchDegrees(1);
    }

    private float getTargetPitchDegrees(float partialTicks) {
        return -getInterpolatedPitch(partialTicks);
    }

    private float getInterpolatedPitch(float partialTicks) {
        return Mth.lerp(partialTicks, prevPitch, pitch);
    }
}
