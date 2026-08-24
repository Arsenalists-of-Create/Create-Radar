package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.api.arad.ARADTargetDesignationEvent;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.api.arad.RollingRpmTracker;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class JammerBlockEntity extends KineticBlockEntity {
    private static final float DEFAULT_YAW = 270.0f;
    private static final float DEFAULT_PITCH = 0.0f;
    private static final float ANGLE_EPSILON = 1.0e-4f;

    public int range = 128;
    public boolean enabled = true;

    public record SelectedEmitterInfo(
            String sourceId,
            @Nullable UUID emitterId,
            BlockPos radarPos,
            Vec3 position,
            RadarType radarType,
            float rollingRpm,
            float rollingRate
    ) {
        public SelectedEmitterInfo {
            Objects.requireNonNull(sourceId, "sourceId");
            radarPos = Objects.requireNonNull(radarPos, "radarPos").immutable();
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(radarType, "radarType");
        }
    }

    private float yaw;
    private float previousYaw;
    private float targetYaw;
    private float pitch;
    private float previousPitch;
    private float targetPitch;
    private BlockPos lastKnownPos;
    private boolean aradLinked;
    private @Nullable String selectedEmitterSource;
    private @Nullable BlockPos selectingMonitorPos;
    private @Nullable ARADTargetDesignationEvent.Target selectedTarget;
    private @Nullable Vec3 selectedEmitterPosition;
    private RadarType selectedRadarType = RadarType.GROUND;
    private float selectedRollingRpm;
    private float selectedRollingRate;

    public JammerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Direction mountFacing = state.hasProperty(JammerBlock.FACING)
                ? state.getValue(JammerBlock.FACING) : Direction.UP;
        yaw = defaultYaw(mountFacing, null);
        previousYaw = yaw;
        targetYaw = yaw;
        pitch = DEFAULT_PITCH;
        previousPitch = pitch;
        targetPitch = pitch;
        lastKnownPos = pos.immutable();
    }

    @Override
    public void tick() {
        super.tick();
        previousYaw = yaw;
        previousPitch = pitch;

        if (level instanceof ServerLevel serverLevel) {
            tickAradLink(serverLevel);
        }

        float maximumStep = degreesPerTick(getSpeed());
        if (maximumStep <= 0.0f) {
            return;
        }

        float nextYaw = moveTowardWrapped(yaw, targetYaw, maximumStep);
        float nextPitch = moveToward(pitch, targetPitch, maximumStep);
        if (Math.abs(Mth.wrapDegrees(nextYaw - yaw)) <= ANGLE_EPSILON
                && Math.abs(nextPitch - pitch) <= ANGLE_EPSILON) {
            return;
        }

        yaw = nextYaw;
        pitch = nextPitch;
        if (level != null && !level.isClientSide) {
            setChanged();
        }
    }

    private void tickAradLink(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        if (gameTime % RollingRpmTracker.SAMPLE_INTERVAL_TICKS == 0) {
            refreshAradLinkState();
            refreshSelectedEmitterInfo(serverLevel);
        }
        if (gameTime % 40 != 0 || lastKnownPos.equals(worldPosition)) {
            return;
        }

        ARADData aradData = ARADData.get(serverLevel);
        if (aradData.isJammerLinked(serverLevel.dimension(), worldPosition)
                || aradData.updateJammerPosition(serverLevel.dimension(),
                lastKnownPos, worldPosition)) {
            lastKnownPos = worldPosition.immutable();
            setChanged();
        }
    }

    public void refreshAradLinkState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean linked = ARADData.get(serverLevel)
                .isJammerLinked(serverLevel.dimension(), worldPosition);
        if (aradLinked == linked) {
            return;
        }
        aradLinked = linked;
        if (!linked) {
            clearAradSelectionInternal();
        }
        setChanged();
        notifyUpdate();
    }

    void receiveAradSelection(
            BlockPos monitorPos,
            @Nullable String sourceId,
            @Nullable ARADTargetDesignationEvent.Target target,
            RadarType radarType
    ) {
        if (sourceId == null || sourceId.isBlank() || target == null) {
            return;
        }
        aradLinked = true;
        selectingMonitorPos = monitorPos.immutable();
        selectedEmitterSource = sourceId;
        selectedTarget = target;
        selectedEmitterPosition = target.noisyWorldPosition();
        selectedRadarType = radarType;
        selectedRollingRpm = 0.0f;
        selectedRollingRate = 0.0f;
        if (level instanceof ServerLevel serverLevel) {
            refreshSelectedEmitterInfo(serverLevel);
        }
        setChanged();
        notifyUpdate();
    }

    void clearAradSelection(BlockPos monitorPos) {
        if (selectingMonitorPos != null && !selectingMonitorPos.equals(monitorPos)) {
            return;
        }
        if (selectedEmitterSource == null) {
            return;
        }
        clearAradSelectionInternal();
        setChanged();
        notifyUpdate();
    }

    private void clearAradSelectionInternal() {
        selectingMonitorPos = null;
        selectedEmitterSource = null;
        selectedTarget = null;
        selectedEmitterPosition = null;
        selectedRadarType = RadarType.GROUND;
        selectedRollingRpm = 0.0f;
        selectedRollingRate = 0.0f;
    }

    private void refreshSelectedEmitterInfo(ServerLevel serverLevel) {
        if (selectedEmitterSource == null || selectedTarget == null) {
            return;
        }

        var radar = ARADTargeting.resolveNativeRadar(serverLevel,
                selectedEmitterSource).orElse(null);
        Vec3 position;
        RadarType radarType;
        RollingRpmTracker.Snapshot telemetry;
        if (radar != null) {
            telemetry = ARADTargeting.resolveRpmTelemetry(serverLevel,
                    selectedEmitterSource);
            position = ARADTargeting.resolveTargetPosition(serverLevel,
                    selectedTarget).orElse(selectedTarget.noisyWorldPosition());
            radarType = radar.getRadarTypeEnum();
        } else {
            ExternalRwrEmitterRegistry.EmitterState external =
                    ExternalRwrEmitterRegistry.resolveSelectable(serverLevel,
                            selectedEmitterSource).orElse(null);
            if (external == null || external.selectionMetadata() == null) {
                return;
            }
            position = external.position();
            radarType = external.radarType();
            telemetry = new RollingRpmTracker.Snapshot(
                    external.selectionMetadata().rollingRpm(),
                    external.selectionMetadata().rollingRate()
            );
        }
        boolean changed = selectedEmitterPosition == null
                || selectedEmitterPosition.distanceToSqr(position) > 1.0e-8
                || selectedRadarType != radarType
                || Math.abs(selectedRollingRpm - telemetry.rollingRpm()) > ANGLE_EPSILON
                || Math.abs(selectedRollingRate - telemetry.rollingRate()) > ANGLE_EPSILON;
        selectedEmitterPosition = position;
        selectedRadarType = radarType;
        selectedRollingRpm = telemetry.rollingRpm();
        selectedRollingRate = telemetry.rollingRate();
        if (changed) {
            setChanged();
            notifyUpdate();
        }
    }

    public boolean isAradLinked() {
        return aradLinked;
    }

    public @Nullable String getSelectedEmitterSource() {
        return selectedEmitterSource;
    }

    public @Nullable Vec3 getSelectedEmitterPosition() {
        return selectedEmitterPosition;
    }

    public float getSelectedRollingRpm() {
        return selectedRollingRpm;
    }

    /** Absolute change in rolling RPM, in RPM per second. */
    public float getSelectedRollingRate() {
        return selectedRollingRate;
    }

    public @Nullable SelectedEmitterInfo getSelectedEmitterInfo() {
        if (selectedEmitterSource == null || selectedTarget == null
                || selectedEmitterPosition == null) {
            return null;
        }
        return new SelectedEmitterInfo(
                selectedEmitterSource,
                selectedTarget.emitterId(),
                selectedTarget.radarPos(),
                selectedEmitterPosition,
                selectedRadarType,
                selectedRollingRpm,
                selectedRollingRate
        );
    }

    void initializePlacedAim(Direction mountFacing,
                             Direction placerHorizontalFacing) {
        float placedYaw = defaultYaw(mountFacing, placerHorizontalFacing);
        yaw = placedYaw;
        previousYaw = placedYaw;
        targetYaw = placedYaw;
        pitch = DEFAULT_PITCH;
        previousPitch = DEFAULT_PITCH;
        targetPitch = DEFAULT_PITCH;
        setChanged();
        notifyUpdate();
    }

    /** Sets the absolute world-space yaw target. */
    public void setYaw(float yawDegrees) {
        float wrapped = wrap360(yawDegrees);
        if (Math.abs(Mth.wrapDegrees(wrapped - targetYaw)) <= ANGLE_EPSILON) {
            return;
        }
        targetYaw = wrapped;
        setChanged();
        notifyUpdate();
    }

    /** Sets the absolute world-space pitch target; positive values aim upward. */
    public void setPitch(float pitchDegrees) {
        float clamped = clampPitch(pitchDegrees);
        if (Math.abs(clamped - targetPitch) <= ANGLE_EPSILON) {
            return;
        }
        targetPitch = clamped;
        setChanged();
        notifyUpdate();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getTargetYaw() {
        return targetYaw;
    }

    public float getTargetPitch() {
        return targetPitch;
    }

    public float getInterpolatedYaw(float partialTick) {
        return wrap360(previousYaw
                + Mth.wrapDegrees(yaw - previousYaw) * partialTick);
    }

    public float getInterpolatedPitch(float partialTick) {
        return Mth.lerp(partialTick, previousPitch, pitch);
    }

    public Direction getMountFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(JammerBlock.FACING)
                ? state.getValue(JammerBlock.FACING) : Direction.UP;
    }

    public Direction getInputShaftDirection() {
        return getMountFacing().getOpposite();
    }

    public boolean affects(BlockPos radarPos) {
        if (!enabled) return false;
        return radarPos.closerThan(worldPosition, range);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries,
                        boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("Yaw", Tag.TAG_FLOAT)) {
            yaw = wrap360(compound.getFloat("Yaw"));
        }
        targetYaw = compound.contains("TargetYaw", Tag.TAG_FLOAT)
                ? wrap360(compound.getFloat("TargetYaw")) : yaw;
        if (compound.contains("Pitch", Tag.TAG_FLOAT)) {
            pitch = clampPitch(compound.getFloat("Pitch"));
        }
        targetPitch = compound.contains("TargetPitch", Tag.TAG_FLOAT)
                ? clampPitch(compound.getFloat("TargetPitch"))
                : pitch;
        previousYaw = yaw;
        previousPitch = pitch;
        lastKnownPos = compound.contains("LastKnownPos", Tag.TAG_LONG)
                ? BlockPos.of(compound.getLong("LastKnownPos"))
                : worldPosition.immutable();
        aradLinked = compound.getBoolean("AradLinked");
        readSelectedEmitter(compound);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries,
                         boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Yaw", wrap360(yaw));
        compound.putFloat("TargetYaw", wrap360(targetYaw));
        compound.putFloat("Pitch", clampPitch(pitch));
        compound.putFloat("TargetPitch", clampPitch(targetPitch));
        compound.putLong("LastKnownPos", lastKnownPos.asLong());
        compound.putBoolean("AradLinked", aradLinked);
        writeSelectedEmitter(compound);
    }

    private void writeSelectedEmitter(CompoundTag compound) {
        if (selectedEmitterSource == null || selectedTarget == null) {
            return;
        }
        compound.putString("SelectedEmitterSource", selectedEmitterSource);
        if (selectingMonitorPos != null) {
            compound.put("SelectingMonitorPos",
                    NbtUtils.writeBlockPos(selectingMonitorPos));
        }
        compound.put("SelectedRadarPos",
                NbtUtils.writeBlockPos(selectedTarget.radarPos()));
        if (selectedTarget.emitterId() != null) {
            compound.putUUID("SelectedEmitterId", selectedTarget.emitterId());
        }
        compound.putDouble("SelectedRangeRatio", selectedTarget.rangeRatio());
        putVec3(compound, "SelectedNoisyPosition",
                selectedTarget.noisyWorldPosition());
        if (selectedTarget.targetSublevelId() != null
                && selectedTarget.targetLocalPosition() != null) {
            compound.putUUID("SelectedTargetSublevelId",
                    selectedTarget.targetSublevelId());
            putVec3(compound, "SelectedTargetLocalPosition",
                    selectedTarget.targetLocalPosition());
        }
        if (selectedEmitterPosition != null) {
            putVec3(compound, "SelectedEmitterPosition",
                    selectedEmitterPosition);
        }
        compound.putString("SelectedRadarType", selectedRadarType.name());
        compound.putFloat("SelectedRollingRpm", selectedRollingRpm);
        compound.putFloat("SelectedRollingRate", selectedRollingRate);
    }

    private void readSelectedEmitter(CompoundTag compound) {
        clearAradSelectionInternal();
        if (!compound.contains("SelectedEmitterSource", Tag.TAG_STRING)
                || !compound.contains("SelectedRadarPos", Tag.TAG_COMPOUND)
                || !hasVec3(compound, "SelectedNoisyPosition")) {
            return;
        }

        selectedEmitterSource = compound.getString("SelectedEmitterSource");
        selectingMonitorPos = NbtUtils
                .readBlockPos(compound, "SelectingMonitorPos").orElse(null);
        BlockPos radarPos = NbtUtils.readBlockPos(compound,
                "SelectedRadarPos").orElse(null);
        Vec3 noisyPosition = getVec3(compound, "SelectedNoisyPosition");
        if (selectedEmitterSource.isBlank() || radarPos == null
                || noisyPosition == null) {
            clearAradSelectionInternal();
            return;
        }

        UUID emitterId = compound.hasUUID("SelectedEmitterId")
                ? compound.getUUID("SelectedEmitterId") : null;
        UUID sublevelId = compound.hasUUID("SelectedTargetSublevelId")
                ? compound.getUUID("SelectedTargetSublevelId") : null;
        Vec3 localPosition = getVec3(compound,
                "SelectedTargetLocalPosition");
        if ((sublevelId == null) != (localPosition == null)) {
            sublevelId = null;
            localPosition = null;
        }
        selectedTarget = new ARADTargetDesignationEvent.Target(
                radarPos,
                emitterId,
                compound.getDouble("SelectedRangeRatio"),
                noisyPosition,
                sublevelId,
                localPosition
        );
        selectedEmitterPosition = getVec3(compound,
                "SelectedEmitterPosition");
        if (selectedEmitterPosition == null) {
            selectedEmitterPosition = noisyPosition;
        }
        if (compound.contains("SelectedRadarType", Tag.TAG_STRING)) {
            try {
                selectedRadarType = RadarType.valueOf(
                        compound.getString("SelectedRadarType"));
            } catch (IllegalArgumentException ignored) {
                selectedRadarType = RadarType.GROUND;
            }
        }
        selectedRollingRpm = compound.getFloat("SelectedRollingRpm");
        selectedRollingRate = compound.getFloat("SelectedRollingRate");
    }

    private static void putVec3(CompoundTag parent, String key, Vec3 value) {
        CompoundTag vector = new CompoundTag();
        vector.putDouble("X", value.x);
        vector.putDouble("Y", value.y);
        vector.putDouble("Z", value.z);
        parent.put(key, vector);
    }

    private static @Nullable Vec3 getVec3(CompoundTag parent, String key) {
        if (!hasVec3(parent, key)) {
            return null;
        }
        CompoundTag vector = parent.getCompound(key);
        Vec3 value = new Vec3(vector.getDouble("X"),
                vector.getDouble("Y"), vector.getDouble("Z"));
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) ? value : null;
    }

    private static boolean hasVec3(CompoundTag parent, String key) {
        return parent.contains(key, Tag.TAG_COMPOUND);
    }

    static float defaultYaw(Direction mountFacing,
                            Direction placerHorizontalFacing) {
        Direction aimDirection = mountFacing.getAxis().isHorizontal()
                ? mountFacing
                : placerHorizontalFacing == null
                ? Direction.NORTH : placerHorizontalFacing.getOpposite();
        return yawForDirection(aimDirection);
    }

    static float yawForDirection(Direction direction) {
        return switch (direction) {
            case EAST -> 0.0f;
            case SOUTH -> 90.0f;
            case WEST -> 180.0f;
            case NORTH -> 270.0f;
            case UP, DOWN -> DEFAULT_YAW;
        };
    }

    static float degreesPerTick(float rpm) {
        return Math.abs(convertToAngular(rpm));
    }

    static float moveTowardWrapped(float current, float target,
                                   float maximumStep) {
        if (maximumStep <= 0.0f) {
            return wrap360(current);
        }
        float delta = Mth.wrapDegrees(target - current);
        if (Math.abs(delta) <= maximumStep) {
            return wrap360(target);
        }
        return wrap360(current + Math.copySign(maximumStep, delta));
    }

    static float moveToward(float current, float target, float maximumStep) {
        if (maximumStep <= 0.0f) {
            return current;
        }
        float delta = target - current;
        if (Math.abs(delta) <= maximumStep) {
            return target;
        }
        return current + Math.copySign(maximumStep, delta);
    }

    static float wrap360(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    static float clampPitch(float degrees) {
        return Mth.clamp(degrees, -90.0f, 90.0f);
    }
}
