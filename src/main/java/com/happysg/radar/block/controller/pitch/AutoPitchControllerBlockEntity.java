package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.controller.firing.FiringControlBlockEntity;
import com.happysg.radar.block.datalink.screens.TargetingConfig;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.List;

public class AutoPitchControllerBlockEntity extends KineticBlockEntity {
    private static final double TOLERANCE = 0.1;
    private double targetAngle;
    private boolean isRunning;
    private boolean artillery = false;

    //abstract class for firing control to avoid cluttering pitch logic
    public FiringControlBlockEntity firingControl;

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public CannonMountBlockEntity getCannonMount() {
        BlockPos pos = getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
        if (level != null && level.getBlockEntity(pos) instanceof CannonMountBlockEntity mount) {
            return mount;
        }
        return null;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (Mods.CREATEBIGCANNONS.isLoaded()) {
            CannonMountBlockEntity mount = getCannonMount();
            if (mount != null) {
                firingControl = new FiringControlBlockEntity(this, mount);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (Mods.CREATEBIGCANNONS.isLoaded()) {
            if (isRunning)
                tryRotateCannon();
            if (firingControl != null)
                firingControl.tick();
        }

    }

    private void tryRotateCannon() {
        if (level == null || level.isClientSide())
            return;


        CannonMountBlockEntity mount = getCannonMount();
        if (mount == null)
            return;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return;

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption))
            return;

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double pitchDifference = targetAngle - currentPitch;
        double speedFactor = Math.abs(getSpeed()) / 32.0;


        if (Math.abs(pitchDifference) > TOLERANCE) {
            if (Math.abs(pitchDifference) > speedFactor) {
                currentPitch += Math.signum(pitchDifference) * speedFactor;
            } else {
                currentPitch = targetAngle;
            }
        } else {
            currentPitch = targetAngle;
        }


        mount.setPitch((float) currentPitch);
    }

    public boolean atTargetPitch() {
        CannonMountBlockEntity mount = getCannonMount();
        if (mount == null)
            return false;
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return false;
        int invert = -contraption.getInitialOrientation().getStepZ() + contraption.getInitialOrientation().getStepX();
        return Math.abs(contraption.pitch * invert - targetAngle) < TOLERANCE;
    }

    public void setTargetAngle(float targetAngle) {
        this.targetAngle = targetAngle;
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
        if (targetPos == null) {
            isRunning = false;
            return;
        }
        isRunning = true;

        CannonMountBlockEntity mount = getCannonMount();
        if (mount != null) {
            if (PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
                List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, (ServerLevel) level);
                if (angles == null) return;
                if (angles.isEmpty()) return;
                if (angles.get(0).isEmpty()) return;
                this.targetAngle = angles.get(0).get(0);
                if (firingControl == null) return;
                this.firingControl.cannonMount.setYaw(angles.get(0).get(1).floatValue());
            } else {
                List<Double> angles = CannonTargeting.calculatePitch(mount, targetPos, (ServerLevel) level);
                if (angles == null || angles.isEmpty()) { //TODO unreachable target stop targeting if auto targeting?
                    isRunning = false;
                    return;
                }
                List<Double> usableAngles = new ArrayList<>();
                for (double angle : angles) {
                    if (mount.getContraption() == null) break;
                    if (angle < mount.getContraption().maximumElevation() && angle > -mount.getContraption().maximumDepression()) {
                        usableAngles.add(angle);
                    }
                }
                if (artillery && usableAngles.size() == 2) {
                    targetAngle = angles.get(1);
                } else if (!usableAngles.isEmpty()) {
                    targetAngle = usableAngles.get(0);
                }
            }
        }
    }


    public void setFiringTarget(Vec3 targetPos, TargetingConfig targetingConfig) {
        if (firingControl == null)
            return;
        firingControl.setTarget(targetPos, targetingConfig);
    }

    public void setSafeZones(List<AABB> safeZones) {
        if (firingControl == null)
            return;
        firingControl.setSafeZones(safeZones);
    }
}
