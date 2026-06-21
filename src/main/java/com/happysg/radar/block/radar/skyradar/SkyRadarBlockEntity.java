package com.happysg.radar.block.radar.skyradar;


import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.SkyRadarScanningBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SkyRadarBlockEntity extends KineticBlockEntity implements IRadar, IControlContraption {
    private static final float SKY_RADAR_ROTATION_SCALE = 0.125f;

    private SkyRadarScanningBehavior scanningBehavior;
    private float yawDeg;
    private float prevYawDeg;
    private boolean manualYawOverride;
    private boolean running;
    private int dishCount;
    private boolean creative;
    private Direction receiverFacing = Direction.NORTH;
    private SkyRadarContraptionEntity movedContraption;
    private boolean hasOwnedTarget;
    private float targetPitchDeg = SkyRadarContraptionEntity.PITCH_DEGREES;

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

        if (ponderVisualActive) {
            previousPonderYawDeg = ponderYawDeg;

            if (ponderYawTicksRemaining > 0) {
                ponderYawDeg = wrap360(ponderYawDeg + ponderYawPerTick);
                ponderYawTicksRemaining--;
            }
        }

        if (!level.isClientSide && running) {
            updateOwnedTargetAim();
        }

        if (running && !manualYawOverride && !hasOwnedTarget) {
            yawDeg = wrap360(yawDeg + getEffectiveAngularSpeed());
        }

        if (movedContraption != null) {
            if (hasOwnedTarget) {
                movedContraption.setTargetAim(yawDeg, targetPitchDeg);
            } else {
                movedContraption.clearTargetAim(yawDeg);
            }
        }

        updateScanningBehavior();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scanningBehavior = new SkyRadarScanningBehavior(this);
        scanningBehavior.setTrackExpiration(100);
        updateScanningBehavior();
        behaviours.add(scanningBehavior);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            disassemble();
        }
        super.remove();
    }

    private void updateScanningBehavior() {
        if (scanningBehavior == null)
            return;

        Vec3 pos = PhysicsHandler.getWorldVec(this);
        scanningBehavior.setScanPos(pos);
        scanningBehavior.setRange(getRange());
        scanningBehavior.setFov(RadarConfig.server().skyRadarFOV.get());
        scanningBehavior.setTrackExpiration(100);
        scanningBehavior.setAngle(getGlobalAngle());
        scanningBehavior.setRunning(running && pos.y >= SkyRadarScanningBehavior.MIN_OPERATING_Y);
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        if (scanningBehavior == null)
            return List.of();
        return scanningBehavior.getRadarTracks();
    }

    @Override
    public float getRange() {
        if (creative) {
            return RadarConfig.server().maxSkyRadarRange.get();
        }
        return Math.min( dishCount * RadarConfig.server().skyRadarDishRangeIncrease.get(),
                RadarConfig.server().maxSkyRadarRange.get());
    }

    @Override
    public boolean isRunning() {
        return running && PhysicsHandler.getWorldVec(this).y >= SkyRadarScanningBehavior.MIN_OPERATING_Y;
    }
    public void setPonderVisualState() {
        this.yawDeg++;
    }
    @Override
    public BlockPos getWorldPos() {
        return getBlockPos();
    }

    @Override
    public float getGlobalAngle() {
        return yawDeg;
    }


    public void assemble() {
        if (level == null || level.isClientSide || !(level.getBlockState(getBlockPos()).getBlock() instanceof SkyRadarBlock)) {
            return;
        }

        SkyRadarContraption contraption = createContraption();
        if (contraption == null) {
            return;
        }

        updateContraptionData(contraption);
        notifyUpdate();
    }

    public void disassemble() {
        if (level == null || (!running && movedContraption == null)) {
            return;
        }

        if (movedContraption != null) {
            movedContraption.disassemble();
        }

        movedContraption = null;
        running = false;
        dishCount = 0;
        creative = false;
        receiverFacing = Direction.NORTH;
        updateScanningBehavior();
        notifyUpdate();
        setChanged();
    }

    private SkyRadarContraption createContraption() {
        SkyRadarContraption contraption = new SkyRadarContraption();
        try {
            if (!contraption.assemble(level, getBlockPos())) {
                return null;
            }
        } catch (AssemblyException e) {
            return null;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        resetAssembledAim();
        movedContraption = SkyRadarContraptionEntity.create(level, this, contraption);
        BlockPos anchor = getBlockPosition().above(2);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setYaw(yawDeg);
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, getBlockPos());
        running = true;
        return contraption;
    }

    private void updateOwnedTargetAim() {
        if (!(level instanceof ServerLevel sl)) {
            clearOwnedTarget();
            return;
        }

        NetworkData.Group group = NetworkData.get(sl).getGroupForEndpoint(sl.dimension(), getBlockPos());
        if (group == null || group.selectedTargetId == null || group.selectedTargetId.isBlank()) {
            clearOwnedTarget();
            return;
        }

        OwnedTarget ownedTarget = findOwnedTarget(sl, group, group.selectedTargetId);
        if (ownedTarget == null || !ownedTarget.radarPos().equals(getBlockPos())) {
            clearOwnedTarget();
            return;
        }

        Vec3 radarPos = PhysicsHandler.getWorldVec(this);
        Vec3 diff = ownedTarget.targetPos().subtract(radarPos);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw = wrap360((float) Math.toDegrees(Math.atan2(diff.x, diff.z)));
        float targetPitch = (float) Math.toDegrees(Math.atan2(diff.y, horizontal));
        float maxStep = Math.abs(getEffectiveAngularSpeed());
        float newYaw = moveTowardWrapped(yawDeg, targetYaw, maxStep);
        float newPitch = moveToward(targetPitchDeg, targetPitch, maxStep);
        boolean changed = !hasOwnedTarget
                || Math.abs(Mth.wrapDegrees(newYaw - yawDeg)) > 0.001f
                || Math.abs(newPitch - targetPitchDeg) > 0.001f;

        hasOwnedTarget = true;
        yawDeg = newYaw;
        targetPitchDeg = newPitch;
        if (changed) {
            notifyUpdate();
            setChanged();
        }
    }

    private void clearOwnedTarget() {
        if (!hasOwnedTarget && targetPitchDeg == SkyRadarContraptionEntity.PITCH_DEGREES) {
            return;
        }
        hasOwnedTarget = false;
        targetPitchDeg = SkyRadarContraptionEntity.PITCH_DEGREES;
        notifyUpdate();
        setChanged();
    }

    private OwnedTarget findOwnedTarget(ServerLevel sl, NetworkData.Group group, String selectedId) {
        BlockPos closestRadarPos = null;
        RadarTrack closestTrack = null;
        double closestDistance = Double.MAX_VALUE;

        for (NetworkData.RadarEndpoint endpoint : group.getRadarEndpoints()) {
            BlockEntity be = sl.getBlockEntity(endpoint.pos());
            if (!(be instanceof IRadar radar) || !radar.isRunning()) {
                continue;
            }

            RadarTrack track = findTrack(radar, selectedId);
            if (track == null || track.position() == null) {
                continue;
            }

            Vec3 radarPos = PhysicsHandler.getWorldVec(sl, endpoint.pos());
            double distance = radarPos.distanceToSqr(track.position());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRadarPos = endpoint.pos();
                closestTrack = track;
            }
        }

        if (closestRadarPos == null || closestTrack == null) {
            return null;
        }

        return new OwnedTarget(closestRadarPos, closestTrack.position());
    }

    private static RadarTrack findTrack(IRadar radar, String selectedId) {
        for (RadarTrack track : radar.getTracks()) {
            if (track == null) {
                continue;
            }
            if (selectedId.equals(track.getId()) || selectedId.equals(track.id())) {
                return track;
            }
        }
        return null;
    }

    private record OwnedTarget(BlockPos radarPos, Vec3 targetPos) {}

    private void updateContraptionData(SkyRadarContraption contraption) {
        dishCount = contraption.getDishCount();
        receiverFacing = contraption.getReceiverFacing();
        creative = contraption.isCreative();
        updateScanningBehavior();
    }

    public int getDishCount() {
        return dishCount;
    }

    public Direction getReceiverFacing() {
        return receiverFacing;
    }

    public boolean isVisualUnlocked() {
        return running || ponderVisualActive;
    }

    public float getInterpolatedYaw(float partialTick) {
        if (ponderVisualActive) {
            float delta = Mth.wrapDegrees(ponderYawDeg - previousPonderYawDeg);
            return wrap360(previousPonderYawDeg + delta * partialTick);
        }

        float delta = Mth.wrapDegrees(yawDeg - prevYawDeg);
        return wrap360(prevYawDeg + delta * partialTick);
    }
    public boolean isAssembled() {
        return running;
    }

    public Optional<SkyRadarContraption> getContraption() {
        return Optional.ofNullable(movedContraption)
                .map(ControlledContraptionEntity::getContraption)
                .filter(c -> c instanceof SkyRadarContraption)
                .map(c -> (SkyRadarContraption) c);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable(CreateRadar.MODID + ".radar.dish_count", dishCount));
        tooltip.add(Component.translatable(CreateRadar.MODID + ".radar.range", getRange()));
        return true;
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

    public float getEffectiveAngularSpeed() {
        if (getSpeed() == 0) {
            return 0;
        }
        return convertToAngular(getSpeed()) * SKY_RADAR_ROTATION_SCALE;
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(Mth.clamp(speed, -getMaxSkyRadarRpm(), getMaxSkyRadarRpm()));
    }

    private static float getMaxSkyRadarRpm() {
        return AllConfigs.server().kinetics.maxRotationSpeed.get().floatValue();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("YawDeg", Tag.TAG_FLOAT)) {
            yawDeg = wrap360(compound.getFloat("YawDeg"));
            prevYawDeg = yawDeg;
        }
        manualYawOverride = compound.getBoolean("ManualYawOverride");
        running = compound.getBoolean("Running");
        dishCount = compound.getInt("DishCount");
        creative = compound.getBoolean("Creative");
        hasOwnedTarget = compound.getBoolean("HasOwnedTarget");
        if (compound.contains("TargetPitchDeg", Tag.TAG_FLOAT)) {
            targetPitchDeg = compound.getFloat("TargetPitchDeg");
        }
        if (compound.contains("ReceiverFacing", Tag.TAG_INT)) {
            receiverFacing = Direction.from3DDataValue(compound.getInt("ReceiverFacing"));
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("YawDeg", wrap360(yawDeg));
        compound.putBoolean("ManualYawOverride", manualYawOverride);
        compound.putBoolean("Running", running);
        compound.putInt("DishCount", dishCount);
        compound.putBoolean("Creative", creative);
        compound.putBoolean("HasOwnedTarget", hasOwnedTarget);
        compound.putFloat("TargetPitchDeg", targetPitchDeg);
        compound.putInt("ReceiverFacing", receiverFacing.get3DDataValue());
    }

    private static float wrap360(float deg) {
        deg %= 360.0f;
        if (deg < 0.0f) {
            deg += 360.0f;
        }
        return deg;
    }

    private void resetAssembledAim() {
        yawDeg = 0;
        prevYawDeg = 0;
        targetPitchDeg = SkyRadarContraptionEntity.PITCH_DEGREES;
        hasOwnedTarget = false;
        manualYawOverride = false;
    }

    private static float moveTowardWrapped(float current, float target, float maxStep) {
        if (maxStep <= 0) {
            return wrap360(current);
        }

        float delta = Mth.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return wrap360(target);
        }
        return wrap360(current + Math.copySign(maxStep, delta));
    }

    private static float moveToward(float current, float target, float maxStep) {
        if (maxStep <= 0) {
            return current;
        }

        float delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }

    @Override
    public String getRadarType() {
        return "sky";
    }

    @Override
    public Direction getradarDirection() {
        return receiverFacing;
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        if (contraption instanceof SkyRadarContraptionEntity skyRadarContraptionEntity) {
            movedContraption = skyRadarContraptionEntity;
            running = true;
        }
    }

    @Override
    public void onStall() {
        notifyUpdate();
    }

    @Override
    public boolean isValid() {
        return !isRemoved() && level != null && ModBlocks.SKY_RADAR.has(getBlockState());
    }

    @Override
    public BlockPos getBlockPosition() {
        return getBlockPos();
    }


    private boolean ponderAnimationActive = false;
    private float ponderYaw = 0.0f;
    private float previousPonderYaw = 0.0f;
    private float ponderYawPerTick = 0.0f;
    private int ponderYawTicksRemaining = 0;
    private boolean ponderVisualActive;
    private float ponderYawDeg;
    private float previousPonderYawDeg;



    public void tickPonderYaw() {
        previousPonderYaw = ponderYaw;

        if (ponderYawTicksRemaining <= 0)
            return;

        ponderYaw += ponderYawPerTick;
        ponderYawTicksRemaining--;
    }
    public void beginPonderVisual() {
        ponderVisualActive = true;
        ponderYawDeg = 0.0f;
        previousPonderYawDeg = 0.0f;
        ponderYawPerTick = 0.0f;
        ponderYawTicksRemaining = 0;
    }

    public void animatePonderYaw(float degrees, int ticks) {
        if (!ponderVisualActive) {
            beginPonderVisual();
        }

        int safeTicks = Math.max(1, ticks);
        ponderYawPerTick = degrees / safeTicks;
        ponderYawTicksRemaining = safeTicks;
    }

}
