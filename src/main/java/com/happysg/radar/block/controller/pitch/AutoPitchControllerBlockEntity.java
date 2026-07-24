package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.behavior.networks.SafeZone;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.DebugSwivelFollow;
import com.happysg.radar.block.controller.kinetic.DebugSwivelSweep;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticControllerState;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticMountFrame;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class AutoPitchControllerBlockEntity extends GeneratingKineticBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double CBC_TOLERANCE = 0.1;
    private static final double PHYS_TOLERANCE_DEG = 0.1;
    private static final double DEADBAND_DEG = 0.25;

    private double minAngleDeg = -90.0;
    private double maxAngleDeg = 90.0;

    private double targetAngle = 0.0;
    public boolean isRunning = false;

    private boolean artillery = false;
    private boolean binoMode = false;

    @Nullable
    public RadarTrack track;

    @Nullable
    private Vec3 lastTargetPos = null;
    private CompoundTag targetingTag = defaultTargetingTag();

    public WeaponFiringControl firingControl;
    public AutoYawControllerBlockEntity autoyaw;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;

    private final KineticControllerState kineticControllerState =
            new KineticControllerState(CannonAxis.PITCH);
    private final DebugSwivelSweep debugSwivelSweep = new DebugSwivelSweep();
    private final DebugSwivelFollow debugSwivelFollow = new DebugSwivelFollow();

    private float generatedSpeed;
    private boolean isolatedGeneratorInitialized;

    @Nullable
    private Direction.Axis lastKineticAxis;

    private final CannonMountPitch cannonHandler;
    private final PhysBearingPitch physHandler;

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountPitch(this);
        this.physHandler = new PhysBearingPitch(this);
    }

    @Override
    public void tick() {
        initializeIsolatedGenerator();
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        if (firingControl == null) {
            getFiringControl();
        }

        debugSwivelSweep.enforce(targetAngle, this::applyDebugSwivelCommand);
        tickDebugSwivelFollow();
        boolean kineticSelected = tickKineticActuator();
        tickDebugSwivelSweep(kineticSelected);
        if (kineticSelected) {
            return;
        }

        Mount mount = resolveMount();
        if (mount == null) {
            isRunning = false;
            return;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            cannonHandler.tick(mount.cbc);
            return;
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.tick(mount.phys);
        }
    }

    public void getFiringControl() {
        if (firingControl != null) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var view = getWeaponGroup();
        if (view == null) {
            return;
        }

        BlockPos mountPos = view.mountPos();
        if (mountPos == null) {
            return;
        }

        BlockEntity be = level.getBlockEntity(mountPos);
        CannonMountContext mount = CannonMountContext.of(be);
        if (mount != null) {
            autoyaw = null;
            // A compact mount cannot be rotated directly, but its yaw controller may
            // still aim the complete weapon through a structural swivel bearing.
            if (view.yawPos() != null
                    && level.getBlockEntity(view.yawPos()) instanceof AutoYawControllerBlockEntity aYCBE) {
                autoyaw = aYCBE;
            }
            firingControl = new WeaponFiringControl(this, mount, autoyaw);
            LOGGER.debug("made new Weapon Config!");
        }
    }

    @Nullable
    private WeaponNetworkRuntime.WeaponGroupView getWeaponGroup() {
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }

        return WeaponNetworkRuntime.get(sl).getWeaponGroupViewFromEndpoint(worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (this.firingControl != null) {
            firingControl.clearBinoTarget();
        }

        if (level == null || level.isClientSide) {
            return;
        }

        setChanged();
    }

    public void setTargetAngle(float angle) {
        this.targetAngle = angle;
        this.isRunning = true;
        kineticControllerState.onTargetChanged(true, angle, DEADBAND_DEG);

        physHandler.reset();

        notifyUpdate();
        setChanged();
    }

    public double getTargetAngle() {
        return targetAngle;
    }

    public void stopController() {
        isRunning = false;
        kineticControllerState.onTargetChanged(false, targetAngle, DEADBAND_DEG);
        notifyUpdate();
        setChanged();
    }

    public DebugSwivelSweep.StartResult startDebugSwivelSweep(double degrees) {
        if (debugSwivelFollow.isActive()) {
            return DebugSwivelSweep.StartResult.failed("player_follow_active");
        }
        KineticMountAdapterResolution resolution = resolveKineticMount();
        if (!Double.isFinite(degrees) || Math.abs(degrees) > 180.0) {
            return DebugSwivelSweep.StartResult.failed("angle_out_of_range");
        }
        if (!resolution.hasAdapter() || resolution.adapter() == null) {
            return DebugSwivelSweep.StartResult.failed(resolution.reason());
        }

        KineticMountAdapter adapter = resolution.adapter();
        String unavailableReason = debugSwivelUnavailableReason(adapter);
        if (unavailableReason != null) {
            return DebugSwivelSweep.StartResult.failed(unavailableReason);
        }

        double physical = adapter.getPhysicalAngleDegrees();
        KineticMountFrame frame = adapter.frameIdentity();
        if (!Double.isFinite(physical) || frame == null) {
            return DebugSwivelSweep.StartResult.failed("physical_feedback_unavailable");
        }
        double capturedTarget = frame.controllerTargetFor(physical);
        double step = adapter.effectiveDegreesPerTick(
                adapter.maximumDriveRpm(getAvailableInputSpeed()));
        debugSwivelSweep.start(degrees, capturedTarget, targetAngle, isRunning,
                step, this::applyDebugSwivelCommand);
        return DebugSwivelSweep.StartResult.started(adapter.relativeDirection());
    }

    public DebugSwivelFollow.ToggleResult toggleDebugSwivelFollow(ServerPlayer player) {
        if (debugSwivelFollow.isActive()) {
            stopDebugSwivelFollow("stopped_by_command", false);
            return DebugSwivelFollow.ToggleResult.stopped();
        }
        if (debugSwivelSweep.isActive()) {
            return DebugSwivelFollow.ToggleResult.failed("swivel_sweep_active");
        }

        KineticMountAdapterResolution resolution = resolveKineticMount();
        if (!resolution.hasAdapter() || resolution.adapter() == null) {
            return DebugSwivelFollow.ToggleResult.failed(resolution.reason());
        }
        KineticMountAdapter adapter = resolution.adapter();
        String unavailableReason = debugSwivelFollowUnavailableReason(adapter);
        if (unavailableReason != null) {
            return DebugSwivelFollow.ToggleResult.failed(unavailableReason);
        }
        KineticMountFrame frame = adapter.frameIdentity();
        if (frame == null) {
            return DebugSwivelFollow.ToggleResult.failed("frame_unavailable");
        }

        Vec3 origin = worldPosition.relative(adapter.relativeDirection()).getCenter();
        double target = DebugSwivelFollow.pitchTargetDegrees(origin, player.getEyePosition());
        if (!isDebugFollowPitchAllowed(target)) {
            return DebugSwivelFollow.ToggleResult.failed(Double.isFinite(target)
                    ? "player_outside_pitch_limits" : "player_too_close_to_bearing");
        }

        kineticControllerState.release();
        commandGeneratedSpeed(0.0);
        kineticControllerState.beginContinuousTracking();
        debugSwivelFollow.start(player.getUUID(), frame, adapter.relativeDirection(),
                targetAngle, isRunning);
        debugSwivelFollow.update(target, this::applyDebugSwivelCommand);
        return DebugSwivelFollow.ToggleResult.started(adapter.relativeDirection());
    }

    @Nullable
    private String debugSwivelUnavailableReason(KineticMountAdapter adapter) {
        if (!adapter.isValid()) {
            return "swivel_adapter_invalid";
        }
        if (!adapter.isAssembled()) {
            return "swivel_not_assembled";
        }
        if (!adapter.isLocked()) {
            return "swivel_not_locked";
        }
        if (Math.abs(getAvailableInputSpeed()) <= 1.0E-5) {
            return "controller_has_no_kinetic_speed";
        }
        if (!adapter.isEndpointFree()) {
            return "swivel_endpoint_busy";
        }
        return null;
    }

    @Nullable
    private String debugSwivelFollowUnavailableReason(KineticMountAdapter adapter) {
        if (!adapter.isValid()) {
            return "swivel_adapter_invalid";
        }
        if (!adapter.isAssembled()) {
            return "swivel_not_assembled";
        }
        if (!adapter.isLocked()) {
            return "swivel_not_locked";
        }
        if (Math.abs(getAvailableInputSpeed()) <= 1.0E-5) {
            return "controller_has_no_kinetic_speed";
        }
        if (!adapter.isEndpointFree() && !adapter.isDrivenBy(worldPosition)) {
            return "swivel_endpoint_busy";
        }
        if (adapter.hasSequenceContext()) {
            return "swivel_sequence_context_present";
        }
        return null;
    }

    private void applyDebugSwivelCommand(double degrees, boolean running) {
        targetAngle = degrees;
        isRunning = running;
        kineticControllerState.onTargetChanged(running, degrees, DEADBAND_DEG);
        physHandler.reset();
        notifyUpdate();
        setChanged();
    }

    private void tickDebugSwivelFollow() {
        if (!debugSwivelFollow.isActive()) {
            return;
        }
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null) {
            stopDebugSwivelFollow(resolution.reason(), true);
            return;
        }
        String unavailableReason = debugSwivelFollowUnavailableReason(adapter);
        if (unavailableReason != null || kineticControllerState.isBlocked()) {
            stopDebugSwivelFollow(unavailableReason != null
                    ? unavailableReason : "controller_blocked", true);
            return;
        }
        KineticMountFrame frame = adapter.frameIdentity();
        if (frame == null || !debugSwivelFollow.matches(frame, adapter.relativeDirection())) {
            stopDebugSwivelFollow("swivel_frame_changed", true);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || debugSwivelFollow.playerId() == null) {
            stopDebugSwivelFollow("server_level_unavailable", true);
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList()
                .getPlayer(debugSwivelFollow.playerId());
        if (player == null || player.serverLevel() != serverLevel || !player.isAlive()) {
            stopDebugSwivelFollow("player_unavailable", true);
            return;
        }

        Vec3 origin = worldPosition.relative(adapter.relativeDirection()).getCenter();
        double target = DebugSwivelFollow.pitchTargetDegrees(origin, player.getEyePosition());
        if (!isDebugFollowPitchAllowed(target)) {
            stopDebugSwivelFollow(Double.isFinite(target)
                    ? "player_outside_pitch_limits" : "player_too_close_to_bearing", true);
            return;
        }
        debugSwivelFollow.update(target, this::applyDebugSwivelCommand);
    }

    private boolean isDebugFollowPitchAllowed(double target) {
        return Double.isFinite(target)
                && target >= minAngleDeg - 1.0e-6
                && target <= maxAngleDeg + 1.0e-6;
    }

    private void stopDebugSwivelFollow(String reason, boolean logCancellation) {
        if (!debugSwivelFollow.isActive()) {
            return;
        }
        kineticControllerState.release();
        commandGeneratedSpeed(0.0);
        debugSwivelFollow.stop(reason, this::applyDebugSwivelCommand);
        if (logCancellation) {
            LOGGER.warn("Cancelled pitch Swivel player follow controller={} reason={}",
                    worldPosition, reason);
        }
    }

    private void tickDebugSwivelSweep(boolean kineticSelected) {
        if (!debugSwivelSweep.isActive()) {
            return;
        }
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        boolean valid = kineticSelected && resolution.hasAdapter() && adapter != null
                && adapter.isValid() && adapter.isAssembled() && adapter.isLocked()
                && !kineticControllerState.isBlocked()
                && (adapter.isEndpointFree() || adapter.isDrivenBy(worldPosition));
        double step = adapter == null ? 0.0
                : adapter.effectiveDegreesPerTick(
                        adapter.maximumDriveRpm(getAvailableInputSpeed()));
        String outcome = debugSwivelSweep.tick(valid, kineticControllerState.isAtDestination(),
                step, this::applyDebugSwivelCommand);
        if (outcome != null && !"completed".equals(outcome)) {
            kineticControllerState.release();
            commandGeneratedSpeed(0.0);
            LOGGER.warn("Cancelled pitch Swivel sweep controller={} reason={}", worldPosition, outcome);
        }
    }

    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (targetPos == null) {
            returnToZero();
            return;
        }

        Mount mount = resolveMount();
        if (mount == null) {
            return;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            cannonHandler.setTarget(mount.cbc, targetPos);
            return;
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.setTarget(mount.phys, targetPos);
        }
    }

    public void returnToZero() {
        targetAngle = 0.0;
        isRunning = true;
        kineticControllerState.onTargetChanged(true, targetAngle, DEADBAND_DEG);
        lastTargetPos = null;
        physHandler.reset();

        notifyUpdate();
        setChanged();
    }

    public boolean atTargetPitch(boolean lag) {
        if (level == null) {
            return false;
        }

        if (debugSwivelSweep.isActive() || debugSwivelFollow.isActive()) {
            return false;
        }

        if (hasStructuralKineticSelection()) {
            return kineticControllerState.isReady(resolveKineticMount(), isRunning,
                    targetAngle, DEADBAND_DEG);
        }

        Mount mount = resolveMount();
        if (mount == null) {
            return false;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            return cannonHandler.atTargetPitch(mount.cbc, lag);
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            return physHandler.atTargetPitch(mount.phys, lag);
        }

        return false;
    }

    public void setAndAcquireTrack(@Nullable RadarTrack tTrack, TargetingConfig config) {
        if (level == null || level.isClientSide || binoMode) {
            return;
        }

        if (firingControl == null) {
            getFiringControl();
        }

        LOGGER.debug("PITCH setAndAcquireTrack track={} firingControl={}", tTrack == null ? "null" : tTrack.getId(), firingControl != null);

        if (tTrack == null) {
            track = null;
            if (firingControl != null) {
                firingControl.resetTarget();
            }
            returnToZero();
            if (autoyaw != null) {
                autoyaw.returnToInitialOrientation();
            }
            return;
        }

        if (tTrack != track) {
            track = tTrack;
        }

        if (firingControl == null) {
            return;
        }
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        var view = getWeaponGroup();
        if (view == null) {
            LOGGER.debug("PITCH {} getWeaponGroup() returned null - cannot aim/fire", worldPosition);
            return;
        }

        firingControl.setTarget(track.getPosition(), config, tTrack, view);
    }

    public void setAndAcquirePos(@Nullable BlockPos binoTargetPos, TargetingConfig config, boolean reset) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (reset || binoTargetPos == null) {
            this.binoMode = false;

            if (firingControl != null) {
                firingControl.clearBinoTarget();
            }
            return;
        }

        if (firingControl == null) {
            getFiringControl();
        }
        if (firingControl == null) {
            return;
        }
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        var view = getWeaponGroup();
        if (view == null) {
            return;
        }

        this.binoMode = true;
        firingControl.setBinoTarget(binoTargetPos, config, view, reset);
    }

    public void setTrack(RadarTrack track) {
        this.track = track;
    }

    public double getMaxEngagementRangeBlocks() {
        if (level == null || level.isClientSide) {
            return 0;
        }
        if (!(level instanceof ServerLevel sl)) {
            return 0;
        }

        Mount mount = resolveMount();
        if (mount == null) {
            return 0;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            return cannonHandler.getMaxEngagementRangeBlocks(mount.cbc, sl);
        }

        return 0;
    }

    @Nullable
    public Vec3 getRayStart() {
        if (firingControl == null) {
            getFiringControl();
        }

        return firingControl != null ? firingControl.getCannonRayStart() : null;
    }

    public void setSafeZones(List<SafeZone> safeZones) {
        if (firingControl == null) {
            return;
        }

        firingControl.setSafeZones(safeZones);
    }

    public boolean canEngageTrack(@Nullable RadarTrack track, boolean requireLos) {
        if (track == null) {
            return false;
        }
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }

        getFiringControl();
        if (firingControl == null) {
            return false;
        }

        Mount mount = resolveMount();
        if (mount == null) {
            return false;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            return cannonHandler.canEngageTrack(mount.cbc, track, requireLos, sl);
        }

        return firingControl.hasLineOfSightTo(track, requireLos);
    }

    public boolean canConstrainAutoTargeting() {
        if (!(level instanceof ServerLevel)) {
            return false;
        }

        getFiringControl();
        if (firingControl == null) {
            return false;
        }

        Mount mount = resolveMount();
        if (mount == null) {
            return false;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            return mount.cbc.getContraption() != null
                    && mount.cbc.getContraption().getContraption() instanceof AbstractMountedCannonContraption;
        }

        return getRayStart() != null;
    }

    @Nullable
    public BlockPos isFacingCannonMount(Level level, BlockPos pos, BlockState state) {
        if (level == null || state == null) {
            return null;
        }
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return null;
        }

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        return pos.relative(facing);
    }

    public void onRelevantNeighborChanged(BlockPos fromPos) {
        if (isPerpendicularNeighbor(fromPos)) {
            invalidateKineticActuator();
        }

        BlockPos mountPos = getMountPos();
        if (mountPos == null) {
            return;
        }

        if (fromPos.equals(mountPos)) {
            mountDirty = true;
        }
    }

    public void markMountDirtyExternal() {
        mountDirty = true;
    }

    @Nullable
    Mount resolveMount() {
        if (level == null) {
            return null;
        }

        if (mountDirty) {
            refreshMountCache();
        }

        return cachedMount;
    }

    private void refreshMountCache() {
        if (level == null) {
            return;
        }

        BlockPos mountPos = getMountPos();
        Mount newMount = null;

        if (mountPos != null) {
            BlockEntity be = level.getBlockEntity(mountPos);

            CannonMountContext cbc = Mods.CREATEBIGCANNONS.isLoaded() ? CannonMountContext.of(be) : null;
            if (cbc != null) {
                newMount = Mount.cbc(cbc);
            } else if (Mods.VS_CLOCKWORK.isLoaded() && be instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            }
        }

        cachedMount = newMount;
        mountDirty = false;

        if (newMount == null && !hasStructuralKineticSelection()) {
            isRunning = false;
            lastTargetPos = null;
            physHandler.reset();
        }

        setChanged();
        notifyUpdate();
    }

    @Nullable
    private BlockPos getMountPos() {
        if (level == null) {
            return null;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos linkedMount = WeaponNetworkRuntime.get(serverLevel).getMountForController(worldPosition);
            if (linkedMount != null) {
                return linkedMount;
            }
        }

        return isFacingCannonMount(level, worldPosition, getBlockState());
    }

    /** Input power is sampled, never connected to this isolated generator. */
    public double getAvailableInputSpeed() {
        return KineticPowerSource.strongestAdjacentShaftRpm(this, getControllerAxis());
    }

    private void initializeIsolatedGenerator() {
        if (isolatedGeneratorInitialized || level == null || level.isClientSide()) {
            return;
        }
        if (hasSource() || hasNetwork() || Math.abs(getTheoreticalSpeed()) > 1.0e-5) {
            detachKinetics();
            removeSource();
            setSpeed(0.0f);
            setNetwork(null);
        }
        sequenceContext = null;
        generatedSpeed = 0.0f;
        isolatedGeneratorInitialized = true;
    }

    private void commandGeneratedSpeed(double rpm) {
        sequenceContext = null;
        if (hasSource()) {
            generatedSpeed = 0.0f;
            detachKinetics();
            removeSource();
            setSpeed(0.0f);
            setNetwork(null);
            setChanged();
            return;
        }
        float next = Double.isFinite(rpm) ? (float) rpm : 0.0f;
        if (next == 0.0f && generatedSpeed == 0.0f) {
            return;
        }
        if (next != 0.0f && Math.abs(next - generatedSpeed) < 0.01f) {
            return;
        }
        generatedSpeed = next;
        updateGeneratedRotation();
        setChanged();
    }

    private boolean tickKineticActuator() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        boolean consumed = kineticControllerState.tick(
                this, resolution, isRunning, targetAngle, DEADBAND_DEG,
                getAvailableInputSpeed(), this::commandGeneratedSpeed);
        flushKineticStateSync();
        return consumed;
    }

    private boolean hasStructuralKineticSelection() {
        return resolveKineticMount().isStructuralSelection();
    }

    private KineticMountAdapterResolution resolveKineticMount() {
        Direction.Axis axis = getControllerAxis();
        if (lastKineticAxis != axis) {
            lastKineticAxis = axis;
            kineticControllerState.invalidate();
        }
        return Mods.SIMULATED.isLoaded()
                ? SimulatedSwivelMountAdapter.resolve(this, axis)
                : KineticMountAdapterResolution.absent("simulated_not_loaded");
    }

    private Direction.Axis getControllerAxis() {
        BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING).getAxis()
                : Direction.Axis.X;
    }

    private boolean isPerpendicularNeighbor(BlockPos pos) {
        int dx = pos.getX() - worldPosition.getX();
        int dy = pos.getY() - worldPosition.getY();
        int dz = pos.getZ() - worldPosition.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return false;
        }

        Direction.Axis neighborAxis = dx != 0 ? Direction.Axis.X
                : dy != 0 ? Direction.Axis.Y : Direction.Axis.Z;
        return neighborAxis != getControllerAxis();
    }

    public void invalidateKineticActuator() {
        kineticControllerState.invalidate();
        commandGeneratedSpeed(0.0);
    }

    public void releaseKineticActuator() {
        kineticControllerState.release();
        commandGeneratedSpeed(0.0);
        flushKineticStateSync();
    }

    private void flushKineticStateSync() {
        if (!kineticControllerState.consumeSyncRequested()) {
            return;
        }
        setChanged();
        notifyUpdate();
    }

    public double getMinAngleDeg() {
        return minAngleDeg;
    }

    public double getMaxAngleDeg() {
        return maxAngleDeg;
    }

    public void setMinAngleDeg(double v) {
        Mount mount = resolveMount();

        if (mount != null && mount.kind == MountKind.PHYS) {
            minAngleDeg = wrap360(v);
        } else {
            minAngleDeg = v;
            if (minAngleDeg > maxAngleDeg) {
                double tmp = minAngleDeg;
                minAngleDeg = maxAngleDeg;
                maxAngleDeg = tmp;
            }
        }

        notifyUpdate();
        setChanged();
    }

    public void setMaxAngleDeg(double v) {
        Mount mount = resolveMount();

        if (mount != null && mount.kind == MountKind.PHYS) {
            maxAngleDeg = wrap360(v);
        } else {
            maxAngleDeg = v;
            if (minAngleDeg > maxAngleDeg) {
                double tmp = minAngleDeg;
                minAngleDeg = maxAngleDeg;
                maxAngleDeg = tmp;
            }
        }

        notifyUpdate();
        setChanged();
    }

    public boolean snapping() {
        double rpm = Math.abs(getAvailableInputSpeed());
        return rpm == 256.0;
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries,clientPacket);

        targetAngle = compound.getDouble("TargetAngle");
        isRunning = compound.getBoolean("IsRunning");

        minAngleDeg = compound.contains("MinAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MinAngleDeg") : -90.0;
        maxAngleDeg = compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MaxAngleDeg") : 90.0;
        targetingTag = compound.contains("Targeting", Tag.TAG_COMPOUND) ? compound.getCompound("Targeting").copy() : defaultTargetingTag();

        if (minAngleDeg > maxAngleDeg) {
            double tmp = minAngleDeg;
            minAngleDeg = maxAngleDeg;
            maxAngleDeg = tmp;
        }

        lastTargetPos = null;
        physHandler.read(compound);
        kineticControllerState.read(compound, wasMoved, clientPacket);
        generatedSpeed = compound.getFloat("GeneratedSpeed");
        isolatedGeneratorInitialized = false;
        lastKineticAxis = null;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries,clientPacket);

        double storedTarget = clientPacket ? targetAngle
                : debugSwivelFollow.persistentTarget(targetAngle);
        boolean storedRunning = clientPacket ? isRunning
                : debugSwivelFollow.persistentRunning(isRunning);
        compound.putDouble("TargetAngle", storedTarget);
        compound.putBoolean("IsRunning", storedRunning);
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.put("Targeting", targetingTag.copy());

        physHandler.write(compound);
        kineticControllerState.write(compound, clientPacket);
        compound.putFloat("GeneratedSpeed", generatedSpeed);
    }

    @Override
    protected void copySequenceContextFrom(KineticBlockEntity sourceBE) {
        sequenceContext = null;
    }

    @Override
    public float getGeneratedSpeed() {
        return generatedSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return 256.0f;
    }

    public static Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public CompoundTag getTargetingTag() {
        return targetingTag.copy();
    }

    public void setTargetingTag(CompoundTag targetingTag) {
        this.targetingTag = targetingTag == null ? defaultTargetingTag() : targetingTag.copy();
        setChanged();
        notifyUpdate();
    }

    void setInternalTargetAngle(double targetAngle) {
        this.targetAngle = targetAngle;
        kineticControllerState.onTargetChanged(isRunning, targetAngle, DEADBAND_DEG);
    }

    void setRunning(boolean running) {
        this.isRunning = running;
        kineticControllerState.onTargetChanged(running, targetAngle, DEADBAND_DEG);
    }

    boolean isRunningController() {
        return isRunning;
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide()) {
            stopDebugSwivelFollow("controller_chunk_unloaded", false);
            releaseKineticActuator();
        }
        super.onChunkUnloaded();
    }

    boolean isArtillery() {
        return artillery;
    }

    void setLastTargetPos(@Nullable Vec3 pos) {
        this.lastTargetPos = pos;
    }

    @Nullable
    Vec3 getLastTargetPos() {
        return lastTargetPos;
    }

    static double getCbcTolerance() {
        return CBC_TOLERANCE;
    }

    static double getPhysToleranceDeg() {
        return PHYS_TOLERANCE_DEG;
    }

    static double getDeadbandDeg() {
        return DEADBAND_DEG;
    }

    static double wrap360(double a) {
        a %= 360.0;
        if (a < 0) {
            a += 360.0;
        }
        return a;
    }

    static double wrap180(double deg) {
        deg = wrap360(deg);
        if (deg >= 180.0) {
            deg -= 360.0;
        }
        return deg;
    }

    static double shortestDelta(double from, double to) {
        return ((to - from + 540.0) % 360.0) - 180.0;
    }

    static double unwrapNear(double lastContinuous, double newWrapped) {
        double lastWrapped = wrap360(lastContinuous);
        return lastContinuous + shortestDelta(lastWrapped, newWrapped);
    }

    private static CompoundTag defaultTargetingTag() {
        CompoundTag root = new CompoundTag();
        root.put("targeting", TargetingConfig.DEFAULT.toTag());
        return root;
    }

    enum MountKind {
        CBC,
        PHYS
    }

    static class Mount {
        final MountKind kind;
        final CannonMountContext cbc;
        final PhysBearingBlockEntity phys;

        private Mount(MountKind kind, @Nullable CannonMountContext cbc, @Nullable PhysBearingBlockEntity phys) {
            this.kind = kind;
            this.cbc = cbc;
            this.phys = phys;
        }

        static Mount cbc(CannonMountContext cbc) {
            return new Mount(MountKind.CBC, cbc, null);
        }

        static Mount phys(PhysBearingBlockEntity phys) {
            return new Mount(MountKind.PHYS, null, phys);
        }
    }
}
