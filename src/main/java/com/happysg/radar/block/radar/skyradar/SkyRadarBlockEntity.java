package com.happysg.radar.block.radar.skyradar;

import com.happysg.radar.api.arad.ARADTargeting;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.SkyRadarScanningBehavior;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrContactEvaluation;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
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
import java.util.UUID;

public class SkyRadarBlockEntity extends KineticBlockEntity implements IRadar, IControlContraption {
    private static final float SKY_RADAR_ROTATION_SCALE = 0.125f;
    private static final int ORIENTATION_VERSION = 1;

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
    private boolean autoDisassembledForAltitude;
    private UUID emitterId = UUID.randomUUID();
    private boolean orientationMigrated = true;

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

        migrateLegacyOrientation();

        if (level instanceof ServerLevel serverLevel) {
            ARADTargeting.heartbeatNativeRadar(serverLevel, this);
        }

        prevYawDeg = yawDeg;

        if (ponderVisualActive) {
            previousPonderYawDeg = ponderYawDeg;

            if (ponderYawTicksRemaining > 0) {
                ponderYawDeg = wrap360(ponderYawDeg + ponderYawPerTick);
                ponderYawTicksRemaining--;
            }
        }

        if (!level.isClientSide) {
            updateAltitudeAssemblyState();
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
            clearSublevelConnector();
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
        scanningBehavior.setRunning(running && isAtOperatingAltitude(pos));
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
        return running && isAtOperatingAltitude();
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
        return wrap360(getReceiverYawOffset() + yawDeg);
    }


    public void assemble() {
        tryAssemble();
    }

    private boolean tryAssemble() {
        if (level == null || level.isClientSide || !(level.getBlockState(getBlockPos()).getBlock() instanceof SkyRadarBlock)) {
            return false;
        }

        if (!isAtOperatingAltitude()) {
            updateScanningBehavior();
            notifyUpdate();
            return false;
        }

        clearSublevelConnector();

        SkyRadarContraption contraption = createContraption();
        if (contraption == null) {
            return false;
        }

        autoDisassembledForAltitude = false;
        updateContraptionData(contraption);
        notifyUpdate();
        return true;
    }

    public void disassemble() {
        disassemble(false);
    }

    private void disassemble(boolean causedByAltitude) {
        autoDisassembledForAltitude = causedByAltitude;

        if (level == null || (!running && movedContraption == null)) {
            notifyUpdate();
            setChanged();
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
        refreshSublevelConnector();
        updateScanningBehavior();
        notifyUpdate();
        setChanged();
    }

    private void placeSublevelConnector() {
        if (level == null || level.isClientSide || !Mods.SABLE.isLoaded()) {
            return;
        }
        if (!SableUtils.isBlockInShipyard(level, getBlockPos())) {
            return;
        }

        BlockPos connectorPos = getSublevelConnectorPos();
        if (level.getBlockState(connectorPos).isAir()) {
            level.setBlock(connectorPos, ModBlocks.SKY_RADAR_SUBLEVEL_CONNECTOR.getDefaultState(), 3);
        }
    }

    public void refreshSublevelConnector() {
        if (!running && movedContraption == null) {
            placeSublevelConnector();
        }
    }

    private void clearSublevelConnector() {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockPos connectorPos = getSublevelConnectorPos();
        if (ModBlocks.SKY_RADAR_SUBLEVEL_CONNECTOR.has(level.getBlockState(connectorPos))) {
            level.removeBlock(connectorPos, false);
        }
    }

    private BlockPos getSublevelConnectorPos() {
        return getBlockPos().above();
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
        float targetGlobalYaw = wrap360((float) Math.toDegrees(Math.atan2(diff.x, diff.z)));
        float targetYaw = wrap360(targetGlobalYaw - getReceiverYawOffset());
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

    private void updateAltitudeAssemblyState() {
        if (level == null || level.isClientSide || level.getGameTime() % 5 != 0) {
            return;
        }

        if (running && !isAtOperatingAltitude()) {
            disassemble(true);
            return;
        }

        if (!running && autoDisassembledForAltitude && isAtOperatingAltitude()) {
            tryAssemble();
            return;
        }

        refreshSublevelConnector();
    }

    private boolean isAtOperatingAltitude() {
        return isAtOperatingAltitude(PhysicsHandler.getWorldVec(this));
    }

    private static boolean isAtOperatingAltitude(Vec3 pos) {
        return pos.y >= SkyRadarScanningBehavior.getMinimumOperatingY();
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
        return getSafeReceiverFacing();
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

    public float getInterpolatedVisualYaw(float partialTick) {
        if (!isVisualUnlocked()) {
            BlockState state = getBlockState();
            Direction facing = state.hasProperty(SkyRadarBlock.FACING)
                    ? state.getValue(SkyRadarBlock.FACING)
                    : Direction.NORTH;
            return wrap360(facing.toYRot() + 180.0f);
        }
        return wrap360(getReceiverYawOffset() + getInterpolatedYaw(partialTick));
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
        if (!isAtOperatingAltitude()) {
            tooltip.add(Component.translatable(CreateRadar.MODID + ".radar.sky_radar_too_low",
                    SkyRadarScanningBehavior.getMinimumOperatingY()));
        }
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
    public float getSweepAngularSpeedDegreesPerTick() {
        return getEffectiveAngularSpeed();
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
        if (compound.hasUUID("EmitterId")) {
            emitterId = compound.getUUID("EmitterId");
        }
        if (compound.contains("YawDeg", Tag.TAG_FLOAT)) {
            yawDeg = wrap360(compound.getFloat("YawDeg"));
            prevYawDeg = yawDeg;
        }
        manualYawOverride = compound.getBoolean("ManualYawOverride");
        running = compound.getBoolean("Running");
        dishCount = compound.getInt("DishCount");
        creative = compound.getBoolean("Creative");
        autoDisassembledForAltitude = compound.getBoolean("AutoDisassembledForAltitude");
        hasOwnedTarget = compound.getBoolean("HasOwnedTarget");
        if (compound.contains("TargetPitchDeg", Tag.TAG_FLOAT)) {
            targetPitchDeg = compound.getFloat("TargetPitchDeg");
        }
        if (compound.contains("ReceiverFacing", Tag.TAG_INT)) {
            receiverFacing = Direction.from3DDataValue(compound.getInt("ReceiverFacing"));
        } else {
            receiverFacing = Direction.NORTH;
        }
        orientationMigrated = compound.getInt("OrientationVersion") >= ORIENTATION_VERSION;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putUUID("EmitterId", emitterId);
        compound.putFloat("YawDeg", wrap360(yawDeg));
        compound.putBoolean("ManualYawOverride", manualYawOverride);
        compound.putBoolean("Running", running);
        compound.putInt("DishCount", dishCount);
        compound.putBoolean("Creative", creative);
        compound.putBoolean("AutoDisassembledForAltitude", autoDisassembledForAltitude);
        compound.putBoolean("HasOwnedTarget", hasOwnedTarget);
        compound.putFloat("TargetPitchDeg", targetPitchDeg);
        compound.putInt("ReceiverFacing", getSafeReceiverFacing().get3DDataValue());
        compound.putInt("OrientationVersion", ORIENTATION_VERSION);
    }

    private static float wrap360(float deg) {
        deg %= 360.0f;
        if (deg < 0.0f) {
            deg += 360.0f;
        }
        return deg;
    }

    private float getReceiverYawOffset() {
        Direction facing = getSafeReceiverFacing();
        float receiverAngle = (float) Math.toDegrees(Math.atan2(
                facing.getStepX(), facing.getStepZ()));
        return wrap360(receiverAngle + 180.0f);
    }

    private Direction getSafeReceiverFacing() {
        Direction facing = receiverFacing;
        return facing != null && facing.getAxis().isHorizontal()
                ? facing
                : Direction.NORTH;
    }

    private void migrateLegacyOrientation() {
        if (orientationMigrated || level == null || level.isClientSide) {
            return;
        }

        orientationMigrated = true;
        BlockState state = level.getBlockState(getBlockPos());
        if (state.getBlock() instanceof SkyRadarBlock
                && state.hasProperty(SkyRadarBlock.FACING)) {
            Direction migratedFacing = getSafeReceiverFacing();
            if (state.getValue(SkyRadarBlock.FACING) != migratedFacing) {
                level.setBlockAndUpdate(getBlockPos(),
                        state.setValue(SkyRadarBlock.FACING, migratedFacing));
            }
        }
        setChanged();
        notifyUpdate();
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
    public UUID getEmitterId() {
        return emitterId;
    }

    @Override
    public RadarType getRadarTypeEnum() {
        return RadarType.SKY;
    }

    @Override
    public RwrContactEvaluation evaluateRwrContact(ServerLevel level, RwrTargetReference receiver, RwrTargetReference target) {
        boolean emitting = isRunning();
        if (!emitting || scanningBehavior == null) {
            return RwrContactEvaluation.notEmitting();
        }

        boolean detectable = scanningBehavior.canDetectRwrReceiver(receiver, level);
        boolean lockCapable = scanningBehavior.canLockRwrTarget(target, level);
        // Exact locks are explicit RWR runtime state; monitor/network selection and owned aim are intentionally ignored here.
        boolean locked = RadarContactRegistry.isExactLockedOn(level, getEmitterId(), target);
        float signalStrength = detectable ? scanningBehavior.signalStrengthForRwrReceiver(receiver, level) : 0.0F;
        return new RwrContactEvaluation(emitting, detectable, lockCapable, locked, signalStrength);
    }

    @Override
    public Direction getradarDirection() {
        return getSafeReceiverFacing();
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
