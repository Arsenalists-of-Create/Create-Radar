package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.behavior.networks.SafeZone;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.ControllerInputShaft;
import com.happysg.radar.block.controller.kinetic.DebugSwivelFollow;
import com.happysg.radar.block.controller.kinetic.DebugSwivelSweep;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticControllerState;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticMountFrame;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionSource;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
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
import net.minecraft.network.chat.Component;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AutoPitchControllerBlockEntity extends GeneratingKineticBlockEntity
        implements ControllerLimitAccess, ControllerCollisionSource,
        ControllerInputShaft {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double CBC_TOLERANCE = 0.1;
    private static final double PHYS_TOLERANCE_DEG = 0.1;
    private static final double DEADBAND_DEG = 0.25;
    private static final int MOVEMENT_LIMITS_VERSION = 1;

    private double minAngleDeg = -90.0;
    private double maxAngleDeg = 90.0;
    private double requestedTargetAngle = 0.0;
    private boolean targetLimitConstrained;

    private double targetAngle = 0.0;
    public boolean isRunning = false;

    private boolean artillery = false;
    private boolean binoMode = false;

    @Nullable
    public RadarTrack track;
    private TargetingConfig radarTargetingConfig = TargetingConfig.DEFAULT;
    private List<SafeZone> safeZones = List.of();

    @Nullable
    private Vec3 lastTargetPos = null;
    private CompoundTag targetingTag = defaultTargetingTag();

    public WeaponFiringControl firingControl;
    public AutoYawControllerBlockEntity autoyaw;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;
    private long mountResolutionTick = Long.MIN_VALUE;
    @Nullable
    private BlockPos cachedMountEndpointPos;

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

        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.get(serverLevel)
                    .advertiseContactController(this);
        }
        getFiringControl();

        debugSwivelSweep.enforce(targetAngle, this::applyDebugSwivelCommand);
        tickDebugSwivelFollow();
        boolean kineticSelected = tickKineticActuator();
        tickDebugSwivelSweep(kineticSelected);
        if (kineticSelected) {
            return;
        }

        List<CannonMountContext> cbcMounts = resolveControlledCbcMounts();
        if (Mods.CREATEBIGCANNONS.isLoaded() && !cbcMounts.isEmpty()) {
            for (CannonMountContext mount : cbcMounts) {
                cannonHandler.tick(mount);
            }
            return;
        }

        Mount mount = supportsPhysBearingMounts() ? resolveMount() : null;
        if (mount != null && mount.kind == MountKind.PHYS
                && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.tick(mount.phys);
            return;
        }

        isRunning = false;
    }

    public void getFiringControl() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var view = getWeaponGroup();
        if (view == null) {
            clearFiringControl();
            return;
        }

        BlockPos mountPos = view.mountPos();
        if (mountPos == null) {
            clearFiringControl();
            return;
        }

        CannonMountContext mount = resolvePrimaryCbcMount();
        if (mount == null || !isFiringControlMount(view, mount)
                || !mount.isCurrent()) {
            clearFiringControl();
            return;
        }
        if (firingControl != null
                && firingControl.cannonMount.sameMount(mount)
                && firingControl.cannonMount.isCurrent()) {
            return;
        }

        clearFiringControl();
        autoyaw = null;
        // A compact mount cannot be rotated directly, but its yaw controller may
        // still aim the complete weapon through a structural swivel bearing.
        if (view.yawPos() != null
                && level.getBlockEntity(view.yawPos()) instanceof AutoYawControllerBlockEntity aYCBE) {
            autoyaw = aYCBE;
        }
        firingControl = new WeaponFiringControl(this, mount, autoyaw);
        firingControl.setSafeZones(safeZones);
        if (!binoMode && track != null) {
            firingControl.setTarget(track.getPosition(), radarTargetingConfig,
                    track, view);
        }
        LOGGER.debug("made new Weapon Config!");
    }

    protected boolean isFiringControlMount(
            WeaponNetworkRuntime.WeaponGroupView view,
            CannonMountContext mount
    ) {
        return view != null && mount != null
                && mount.getBlockPos().equals(view.mountPos());
    }

    private void clearFiringControl() {
        if (firingControl != null) {
            firingControl.resetTarget();
            firingControl = null;
        }
        autoyaw = null;
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

        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.get(serverLevel)
                    .advertiseContactController(this);
        }
        setChanged();
    }

    public void setTargetAngle(float angle) {
        kineticControllerState.endContinuousTracking();
        applyTargetRequest(angle, true, true);
    }

    public double getTargetAngle() {
        return targetAngle;
    }

    public double getRequestedTargetAngle() {
        return requestedTargetAngle;
    }

    private void applyTargetRequest(double requestedAngle, boolean running,
                                    boolean resetPhysicalTracking) {
        if (!Double.isFinite(requestedAngle)) {
            return;
        }
        requestedTargetAngle = requestedAngle;
        ControllerMovementLimits effective = getMovementLimits()
                .intersection(getSupportedMovementLimits()).orElse(null);
        if (effective == null) {
            targetLimitConstrained = true;
            isRunning = false;
            kineticControllerState.onTargetChanged(
                    false, targetAngle, DEADBAND_DEG);
            if (resetPhysicalTracking) {
                physHandler.reset();
            }
            notifyUpdate();
            setChanged();
            return;
        }
        double applied = effective.clampControllerTarget(
                requestedAngle, 0.0);
        targetLimitConstrained = !effective.allowsControllerTarget(
                requestedAngle, 0.0);
        targetAngle = applied;
        isRunning = running;
        kineticControllerState.onTargetChanged(running, applied, DEADBAND_DEG);
        if (resetPhysicalTracking) {
            physHandler.reset();
        }
        notifyUpdate();
        setChanged();
    }

    public void stopController() {
        kineticControllerState.endContinuousTracking();
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
        if (!Double.isFinite(target)) {
            return DebugSwivelFollow.ToggleResult.failed("player_too_close_to_bearing");
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
        applyTargetRequest(degrees, running, true);
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
        if (!Double.isFinite(target)) {
            stopDebugSwivelFollow("player_too_close_to_bearing", true);
            return;
        }
        debugSwivelFollow.update(target, this::applyDebugSwivelCommand);
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

        KineticMountAdapterResolution kineticResolution = resolveKineticMount();
        if (kineticResolution.isStructuralSelection()) {
            Vec3 origin = PhysicsHandler.getWorldVec(this);
            setStructuralAimDirection(targetPos.subtract(origin), false);
            return;
        }

        CannonMountContext cbcMount = resolvePrimaryCbcMount();
        if (cbcMount != null && Mods.CREATEBIGCANNONS.isLoaded()) {
            cannonHandler.setTarget(cbcMount, targetPos);
            return;
        }

        Mount mount = supportsPhysBearingMounts() ? resolveMount() : null;
        if (mount != null && mount.kind == MountKind.PHYS
                && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.setTarget(mount.phys, targetPos);
        }
    }

    public void returnToZero() {
        kineticControllerState.endContinuousTracking();
        applyTargetRequest(0.0, true, true);
        lastTargetPos = null;
    }

    public boolean atTargetPitch(boolean lag) {
        return atTargetPitch(lag, 0.0);
    }

    public boolean atTargetPitch(boolean lag, double minimumToleranceDegrees) {
        if (level == null || targetLimitConstrained) {
            return false;
        }

        if (debugSwivelSweep.isActive() || debugSwivelFollow.isActive()) {
            return false;
        }

        if (hasStructuralKineticSelection()) {
            return kineticControllerState.isReady(resolveKineticMount(), isRunning,
                    targetAngle, DEADBAND_DEG);
        }

        List<CannonMountContext> cbcMounts = resolveControlledCbcMounts();
        if (Mods.CREATEBIGCANNONS.isLoaded() && !cbcMounts.isEmpty()) {
            for (CannonMountContext mount : cbcMounts) {
                if (!cannonHandler.atTargetPitch(
                        mount, lag, minimumToleranceDegrees)) {
                    return false;
                }
            }
            return true;
        }

        Mount mount = supportsPhysBearingMounts() ? resolveMount() : null;
        if (mount != null && mount.kind == MountKind.PHYS
                && Mods.VS_CLOCKWORK.isLoaded()) {
            return physHandler.atTargetPitch(mount.phys, lag, minimumToleranceDegrees);
        }

        return false;
    }

    public boolean isAlignedForFiring(boolean lag) {
        return isAlignedForFiring(lag, 0.0);
    }

    public boolean isAlignedForFiring(boolean lag, double minimumToleranceDegrees) {
        if (level == null || targetLimitConstrained
                || debugSwivelSweep.isActive() || debugSwivelFollow.isActive()) {
            return false;
        }
        if (!hasStructuralKineticSelection()) {
            return atTargetPitch(lag, minimumToleranceDegrees);
        }

        double tolerance = DEADBAND_DEG;
        if (!lag) {
            tolerance += RadarConfig.server().targetLoosenAmount.get();
        }
        if (Double.isFinite(minimumToleranceDegrees)) {
            tolerance = Math.max(tolerance, Math.max(0.0, minimumToleranceDegrees));
        }
        return kineticControllerState.isAlignedForFiring(
                resolveKineticMount(), worldPosition, isRunning, targetAngle, tolerance);
    }

    /**
     * Evaluates one CBC mount against this controller's shared pitch command.
     * T-Pitch firing uses this per mount so one side can fire while the other
     * side is still settling.
     */
    public boolean isCbcMountAlignedForFiring(
            @Nullable CannonMountContext mount,
            boolean lag,
            double minimumToleranceDegrees
    ) {
        if (level == null || mount == null || targetLimitConstrained
                || debugSwivelSweep.isActive()
                || debugSwivelFollow.isActive()) {
            return false;
        }
        for (CannonMountContext controlled : resolveControlledCbcMounts()) {
            if (controlled.sameMount(mount)) {
                return cannonHandler.atTargetPitch(
                        controlled, lag, minimumToleranceDegrees);
            }
        }
        return false;
    }

    public void setAndAcquireTrack(@Nullable RadarTrack tTrack, TargetingConfig config) {
        if (level == null || level.isClientSide) {
            return;
        }

        track = tTrack;
        radarTargetingConfig = config == null
                ? TargetingConfig.DEFAULT : config;
        if (binoMode) {
            return;
        }

        if (firingControl == null) {
            getFiringControl();
        }

        LOGGER.debug("PITCH setAndAcquireTrack track={} firingControl={}", tTrack == null ? "null" : tTrack.getId(), firingControl != null);

        if (tTrack == null) {
            if (firingControl != null) {
                firingControl.resetTarget();
            }
            returnToZero();
            returnControlledYawToInitialOrientation();
            return;
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

        firingControl.setTarget(track.getPosition(), radarTargetingConfig,
                track, view);
    }

    private void returnControlledYawToInitialOrientation() {
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.WeaponControlView controlView =
                    WeaponNetworkRuntime.get(serverLevel)
                            .getWeaponControlViewFromPitch(worldPosition);
            if (controlView != null) {
                Set<BlockPos> visitedYawPositions = new HashSet<>();
                for (WeaponNetworkRuntime.MountChannelView channel
                        : controlView.channels()) {
                    BlockPos yawPos = channel.yawPos();
                    if (yawPos == null || !visitedYawPositions.add(yawPos)) {
                        continue;
                    }
                    if (level.getBlockEntity(yawPos)
                            instanceof AutoYawControllerBlockEntity yaw) {
                        yaw.returnToInitialOrientation();
                    }
                }
                return;
            }
        }

        if (autoyaw != null) {
            autoyaw.returnToInitialOrientation();
        }
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

        CannonMountContext mount = resolvePrimaryCbcMount();
        if (mount != null && Mods.CREATEBIGCANNONS.isLoaded()) {
            return cannonHandler.getMaxEngagementRangeBlocks(mount, sl);
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
        this.safeZones = safeZones == null || safeZones.isEmpty()
                ? List.of() : List.copyOf(safeZones);
        if (firingControl == null) {
            return;
        }

        firingControl.setSafeZones(this.safeZones);
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

        CannonMountContext mount = resolvePrimaryCbcMount();
        if (mount != null && Mods.CREATEBIGCANNONS.isLoaded()) {
            return cannonHandler.canEngageTrack(mount, track, requireLos, sl);
        }

        return supportsPhysBearingMounts()
                && firingControl.hasLineOfSightTo(track, requireLos);
    }

    public boolean canConstrainAutoTargeting() {
        if (!(level instanceof ServerLevel)) {
            return false;
        }

        getFiringControl();
        if (firingControl == null) {
            return false;
        }

        CannonMountContext mount = resolvePrimaryCbcMount();
        if (mount != null && Mods.CREATEBIGCANNONS.isLoaded()) {
            return mount.getContraption() != null
                    && mount.getContraption().getContraption()
                    instanceof AbstractMountedCannonContraption;
        }

        return supportsPhysBearingMounts() && getRayStart() != null;
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
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.get(serverLevel)
                    .markContactTopologyDirty();
        }
    }


    protected List<CannonMountContext> resolveControlledCbcMounts() {
        Mount mount = resolveMount();
        if (mount == null || mount.kind != MountKind.CBC || mount.cbc == null) {
            return List.of();
        }
        return List.of(mount.cbc);
    }

    @Nullable
    protected CannonMountContext resolvePrimaryCbcMount() {
        List<CannonMountContext> mounts = resolveControlledCbcMounts();
        return mounts.isEmpty() ? null : mounts.getFirst();
    }

    /** Server-side mount data used by the controller collision-view snapshot. */
    @Override
    public List<CannonMountContext> resolveCollisionCbcMounts() {
        return resolveControlledCbcMounts();
    }

    /** Includes an idle/expected mount position when no cannon is assembled. */
    @Override
    public List<BlockPos> resolveCollisionMountPositions() {
        List<CannonMountContext> mounts = resolveControlledCbcMounts();
        if (!mounts.isEmpty()) {
            return mounts.stream().map(CannonMountContext::getBlockPos).toList();
        }
        BlockPos mountPos = getMountPos();
        return mountPos == null ? List.of() : List.of(mountPos.immutable());
    }

    @Override
    public List<UUID> resolveCollisionSublevelIds() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        KineticMountFrame frame = adapter == null ? null
                : adapter.frameIdentity();
        return resolution.hasAdapter() && adapter.isValid()
                && adapter.isAssembled() && frame != null
                && frame.assemblyId() != null
                ? List.of(frame.assemblyId()) : List.of();
    }

    @Override
    public Vec3 resolveCollisionCannonForward() {
        return getStructuralPhysicalWorldDirection();
    }

    @Override
    public Vec3 resolveCollisionViewOrigin() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()
                || level == null) {
            return null;
        }
        return PhysicsHandler.getWorldVec(level,
                worldPosition.relative(adapter.relativeDirection())
                        .getCenter());
    }

    @Override
    public Vec3 resolveCollisionNeutralForward() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null
                || !adapter.isValid()) {
            return null;
        }
        KineticAimFrame aim = adapter.aimFrame();
        if (aim == null) {
            return null;
        }
        KineticMountFrame identity = adapter.frameIdentity();
        Direction initial = identity == null
                ? Direction.SOUTH : identity.cannonInitialOrientation();
        double yaw = switch (initial == null ? Direction.SOUTH : initial) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
        return aim.worldDirection(yaw, 0.0);
    }

    protected boolean supportsPhysBearingMounts() {
        return true;
    }

    protected boolean supportsStructuralKineticMounts() {
        return true;
    }

    @Nullable
    Mount resolveMount() {
        if (level == null) {
            return null;
        }

        BlockPos mountPos = getMountPos();
        long gameTime = level.getGameTime();
        if (mountDirty || mountResolutionTick != gameTime
                || !java.util.Objects.equals(cachedMountEndpointPos, mountPos)
                || (cachedMount != null
                    && cachedMount.kind == MountKind.CBC
                    && (cachedMount.cbc == null
                        || !cachedMount.cbc.isCurrent()))) {
            refreshMountCache(mountPos);
        }

        return cachedMount;
    }

    private void refreshMountCache(@Nullable BlockPos mountPos) {
        if (level == null) {
            return;
        }

        Mount oldMount = cachedMount;
        Mount newMount = null;

        if (mountPos != null) {
            BlockEntity be = level.hasChunkAt(mountPos)
                    ? level.getBlockEntity(mountPos)
                    : null;

            CannonMountContext cbc = Mods.CREATEBIGCANNONS.isLoaded()
                    ? CannonMountContext.resolveEndpoint(level, mountPos)
                    : null;
            if (cbc != null) {
                newMount = Mount.cbc(cbc);
            } else if (supportsPhysBearingMounts()
                    && Mods.VS_CLOCKWORK.isLoaded()
                    && be instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            }
        }

        cachedMount = newMount;
        cachedMountEndpointPos = mountPos == null ? null : mountPos.immutable();
        mountResolutionTick = level.getGameTime();
        mountDirty = false;

        if (oldMount != null && !sameMount(oldMount, newMount)) {
            clearFiringControl();
            track = null;
            isRunning = false;
            lastTargetPos = null;
        }

        if (newMount == null && !hasStructuralKineticSelection()) {
            isRunning = false;
            lastTargetPos = null;
            physHandler.reset();
        }

        setChanged();
        notifyUpdate();
    }

    private static boolean sameMount(@Nullable Mount first, @Nullable Mount second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.kind != second.kind) {
            return false;
        }
        return first.kind == MountKind.CBC
                ? first.cbc != null && first.cbc.sameMount(second.cbc)
                : first.phys == second.phys;
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
    @Override
    public double getAvailableInputSpeed() {
        return KineticPowerSource.strongestAdjacentShaftRpm(this, getControllerAxis());
    }

    @Override
    public Direction getInputShaftDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING).getOpposite()
                : Direction.SOUTH;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip,
                                      boolean isPlayerSneaking) {
        return false;
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

    public boolean hasStructuralKineticSelectionForTargeting() {
        return hasStructuralKineticSelection();
    }

    @Nullable
    public KineticAimFrame getStructuralAimFrame() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()
                || !adapter.isAssembled() || !adapter.isLocked()
                || adapter.frameIdentity() == null) {
            return null;
        }
        return adapter.aimFrame();
    }

    @Nullable
    public Double getStructuralPhysicalControllerAngle() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()) {
            return null;
        }
        double angle = adapter.getPhysicalControllerAngleDegrees();
        return Double.isFinite(angle) ? angle : null;
    }

    @Nullable
    public Vec3 getStructuralPhysicalWorldDirection() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()) {
            return null;
        }
        Vec3 direction = adapter.getPhysicalWorldDirection();
        return direction.lengthSqr() < 1.0e-12 ? null : direction;
    }

    public double getStructuralEffectiveDegreesPerTick() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()) {
            return 0.0;
        }
        double rpm = adapter.maximumDriveRpm(getAvailableInputSpeed());
        return adapter.effectiveDegreesPerTick(rpm);
    }

    public boolean setRadarAimDirection(@Nullable Vec3 worldAimDirection) {
        return setStructuralAimDirection(worldAimDirection, true);
    }

    public void endRadarTracking() {
        kineticControllerState.endContinuousTracking();
    }

    public void failClosedRadarAim() {
        kineticControllerState.endContinuousTracking();
        isRunning = false;
        kineticControllerState.onTargetChanged(false, targetAngle, DEADBAND_DEG);
        commandGeneratedSpeed(0.0);
        flushKineticStateSync();
        notifyUpdate();
        setChanged();
    }

    private boolean setStructuralAimDirection(@Nullable Vec3 worldAimDirection,
                                              boolean continuous) {
        if (debugSwivelSweep.isActive() || debugSwivelFollow.isActive()) {
            return false;
        }
        if (level == null || level.isClientSide() || worldAimDirection == null
                || worldAimDirection.lengthSqr() < 1.0e-12) {
            if (continuous && level != null && !level.isClientSide()) {
                failClosedRadarAim();
            }
            return false;
        }
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()
                || !adapter.isAssembled() || !adapter.isLocked()
                || adapter.frameIdentity() == null || adapter.aimFrame() == null) {
            if (continuous) {
                failClosedRadarAim();
            }
            return false;
        }
        double target = adapter.controllerTargetForWorldDirection(worldAimDirection);
        if (!Double.isFinite(target)) {
            if (continuous) {
                failClosedRadarAim();
            }
            return false;
        }

        if (continuous) {
            kineticControllerState.beginContinuousTracking();
        } else {
            kineticControllerState.endContinuousTracking();
        }
        applyTargetRequest(target, true, true);
        lastTargetPos = null;
        return !targetLimitConstrained;
    }

    private KineticMountAdapterResolution resolveKineticMount() {
        if (!supportsStructuralKineticMounts()) {
            return KineticMountAdapterResolution.absent(
                    "controller_does_not_support_structural_mounts");
        }
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
        return getMovementLimits().minDegrees();
    }

    public double getMaxAngleDeg() {
        return getMovementLimits().maxDegrees();
    }

    @Override
    public CannonAxis getControlledAxis() {
        return CannonAxis.PITCH;
    }

    @Override
    public ControllerMovementLimits getMovementLimits() {
        ControllerMovementLimits configured =
                new ControllerMovementLimits(CannonAxis.PITCH,
                        minAngleDeg, maxAngleDeg);
        return configured.intersection(getSupportedMovementLimits())
                .orElse(configured);
    }

    @Override
    public ControllerMovementLimits getSupportedMovementLimits() {
        ControllerMovementLimits defaults =
                ControllerMovementLimits.defaults(CannonAxis.PITCH);
        KineticMountAdapterResolution kinetic = resolveKineticMount();
        if (kinetic.isStructuralSelection()) {
            return defaults;
        }

        double minimum = defaults.minDegrees();
        double maximum = defaults.maxDegrees();
        boolean foundCannonLimits = false;
        for (CannonMountContext mount : resolveControlledCbcMounts()) {
            CannonMountContext.PitchLimits limits = mount.pitchLimits();
            if (limits == null) {
                continue;
            }
            foundCannonLimits = true;
            minimum = Math.max(minimum, limits.minDegrees());
            maximum = Math.min(maximum, limits.maxDegrees());
        }
        if (!foundCannonLimits) {
            return defaults;
        }
        return ControllerMovementLimits.validated(
                CannonAxis.PITCH, minimum, maximum).orElse(defaults);
    }

    @Override
    public boolean hasAssembledControlledMount() {
        KineticMountAdapterResolution kinetic = resolveKineticMount();
        if (kinetic.isStructuralSelection()) {
            KineticMountAdapter adapter = kinetic.adapter();
            return kinetic.hasAdapter() && adapter != null
                    && adapter.isValid() && adapter.isAssembled();
        }
        if (resolveControlledCbcMounts().stream()
                .anyMatch(CannonMountContext::hasAssembledCannon)) {
            return true;
        }
        Mount mount = supportsPhysBearingMounts() ? resolveMount() : null;
        return mount != null && mount.kind == MountKind.PHYS
                && mount.phys != null && mount.phys.isRunning();
    }

    @Override
    public boolean isTargetLimitConstrained() {
        return targetLimitConstrained;
    }

    @Override
    public boolean setMovementLimits(double minDegrees, double maxDegrees) {
        var validated = ControllerMovementLimits.validated(
                CannonAxis.PITCH, minDegrees, maxDegrees);
        if (validated.isEmpty()) {
            return false;
        }
        ControllerMovementLimits limits = validated.get();
        minAngleDeg = limits.minDegrees();
        maxAngleDeg = limits.maxDegrees();
        applyTargetRequest(requestedTargetAngle, isRunning, true);
        return true;
    }

    public void setMinAngleDeg(double v) {
        setMovementLimits(v, maxAngleDeg);
    }

    public void setMaxAngleDeg(double v) {
        setMovementLimits(minAngleDeg, v);
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

        double storedMin = compound.contains("MinAngleDeg", Tag.TAG_DOUBLE)
                ? compound.getDouble("MinAngleDeg") : -90.0;
        double storedMax = compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE)
                ? compound.getDouble("MaxAngleDeg") : 90.0;
        ControllerMovementLimits limits = ControllerMovementLimits.validated(
                CannonAxis.PITCH, storedMin, storedMax)
                .orElse(ControllerMovementLimits.defaults(CannonAxis.PITCH));
        minAngleDeg = limits.minDegrees();
        maxAngleDeg = limits.maxDegrees();
        targetingTag = compound.contains("Targeting", Tag.TAG_COMPOUND) ? compound.getCompound("Targeting").copy() : defaultTargetingTag();
        requestedTargetAngle = compound.contains("RequestedTargetAngle", Tag.TAG_DOUBLE)
                ? compound.getDouble("RequestedTargetAngle") : targetAngle;
        double constrainedTarget = limits.clampControllerTarget(
                requestedTargetAngle, 0.0);
        targetLimitConstrained = !limits.allowsControllerTarget(
                requestedTargetAngle, 0.0);
        targetAngle = constrainedTarget;

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
        compound.putInt("MovementLimitsVersion", MOVEMENT_LIMITS_VERSION);
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.putDouble("RequestedTargetAngle", requestedTargetAngle);
        compound.putBoolean("TargetLimitConstrained", targetLimitConstrained);
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
        applyTargetRequest(targetAngle, isRunning, false);
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
            if (level instanceof ServerLevel serverLevel) {
                WeaponNetworkRuntime.get(serverLevel)
                        .unregisterContactController(worldPosition);
            }
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
