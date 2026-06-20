package com.happysg.radar.block.radar.skyradar;


import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.SkyRadarScanningBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

public class SkyRadarBlockEntity extends KineticBlockEntity implements IRadar {
    private SkyRadarScanningBehavior scanningBehavior;
    private float yawDeg;
    private float prevYawDeg;
    private boolean manualYawOverride;

    public SkyRadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateScanningBehavior();
    }

    @Override
    public void tick() {
        super.tick();
        prevYawDeg = yawDeg;
        if (!manualYawOverride) {
            yawDeg = wrap360(yawDeg + convertToAngular(getSpeed()));
        }
        updateScanningBehavior();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scanningBehavior = new SkyRadarScanningBehavior(this);
        scanningBehavior.setTrackExpiration(1);
        updateScanningBehavior();
        behaviours.add(scanningBehavior);
    }

    private void updateScanningBehavior() {
        if (scanningBehavior == null)
            return;

        Vec3 pos = PhysicsHandler.getWorldVec(this);
        scanningBehavior.setScanPos(pos);
        scanningBehavior.setRange(getRange());
        scanningBehavior.setFov(RadarConfig.server().skyRadarFOV.get());
        scanningBehavior.setAngle(getGlobalAngle());
        scanningBehavior.setRunning(pos.y >= SkyRadarScanningBehavior.MIN_OPERATING_Y);
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        if (scanningBehavior == null)
            return List.of();
        return scanningBehavior.getRadarTracks();
    }

    @Override
    public float getRange() {
        return Math.min(RadarConfig.server().skyRadarBaseRange.get(), RadarConfig.server().maxSkyRadarRange.get());
    }

    @Override
    public boolean isRunning() {
        return PhysicsHandler.getWorldVec(this).y >= SkyRadarScanningBehavior.MIN_OPERATING_Y;
    }

    @Override
    public BlockPos getWorldPos() {
        return getBlockPos();
    }

    @Override
    public float getGlobalAngle() {
        return yawDeg;
    }

    public void setYaw(float yawDeg) {
        this.yawDeg = wrap360(yawDeg);
        this.prevYawDeg = this.yawDeg;
        this.manualYawOverride = true;
        notifyUpdate();
        setChanged();
    }

    public void resumeShaftRotation() {
        manualYawOverride = false;
        notifyUpdate();
        setChanged();
    }

    public boolean isManualYawOverride() {
        return manualYawOverride;
    }

    public float getInterpolatedYaw(float partialTick) {
        float delta = Mth.wrapDegrees(yawDeg - prevYawDeg);
        return wrap360(prevYawDeg + delta * partialTick);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("YawDeg", Tag.TAG_FLOAT)) {
            yawDeg = wrap360(compound.getFloat("YawDeg"));
            prevYawDeg = yawDeg;
        }
        manualYawOverride = compound.getBoolean("ManualYawOverride");
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("YawDeg", wrap360(yawDeg));
        compound.putBoolean("ManualYawOverride", manualYawOverride);
    }

    private static float wrap360(float deg) {
        deg %= 360.0f;
        if (deg < 0.0f) {
            deg += 360.0f;
        }
        return deg;
    }

    @Override
    public String getRadarType() {
        return "sky";
    }

    @Override
    public Direction getradarDirection() {
        return Direction.NORTH;
    }
}
