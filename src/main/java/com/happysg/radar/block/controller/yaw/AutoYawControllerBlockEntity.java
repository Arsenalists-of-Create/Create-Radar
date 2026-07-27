package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.DebugSwivelFollow;
import com.happysg.radar.block.controller.kinetic.DebugSwivelSweep;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticControllerState;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticMountFrame;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;

import javax.annotation.Nullable;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class AutoYawControllerBlockEntity extends GeneratingKineticBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double TOLERANCE_DEG = 0.15;
    private static final double DEADBAND_DEG = 0.5;

    private double targetAngle = 0.0;
    private boolean isRunning = false;

    private double lastCbcYawWritten = 0.0;
    private boolean hasLastCbcYawWritten = false;

    private double minAngleDeg = 0.0;
    private double maxAngleDeg = 360.0;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;

    private final KineticControllerState kineticControllerState =
            new KineticControllerState(CannonAxis.YAW);
    private final DebugSwivelSweep debugSwivelSweep = new DebugSwivelSweep();
    private final DebugSwivelFollow debugSwivelFollow = new DebugSwivelFollow();

    private float generatedSpeed;
    private boolean isolatedGeneratorInitialized;

    private final CannonMountYaw cannonHandler;
    private final PhysBearingYaw physHandler;

    public AutoYawControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountYaw(this);
        this.physHandler = new PhysBearingYaw(this);
    }

    @Override
    public void tick() {
        initializeIsolatedGenerator();
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        debugSwivelSweep.enforce(targetAngle, this::applyDebugSwivelCommand);
        tickDebugSwivelFollow();
        boolean kineticSelected = tickKineticActuator();
        tickDebugSwivelSweep(kineticSelected);
        if (kineticSelected) {
            return;
        }

        Mount mount = resolveMount();
        if (mount != null) {
            if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
                cannonHandler.tick(mount.cbc);
            } else if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
                if (level.getGameTime() % 20 == 5) {
                    physHandler.maybeUpdateYawZeroFromCannonInitialOrientation();
                }
                physHandler.tick(mount.phys);
            }
        }
    }

    public void setTargetAngle(float targetAngle) {
        kineticControllerState.endContinuousTracking();
        this.targetAngle = targetAngle;
        this.isRunning = true;
        kineticControllerState.onTargetChanged(true, targetAngle, DEADBAND_DEG);
        notifyUpdate();
        setChanged();
    }

    public double getTargetAngle() {
        return targetAngle;
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
        Vec3 playerTarget = player.getEyePosition();
        if (!canPossiblyAimAt(origin, playerTarget)) {
            return DebugSwivelFollow.ToggleResult.failed("player_outside_yaw_sector");
        }
        double target = DebugSwivelFollow.yawTargetDegrees(origin, playerTarget);
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
        targetAngle = degrees;
        isRunning = running;
        kineticControllerState.onTargetChanged(running, degrees, DEADBAND_DEG);
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
        Vec3 playerTarget = player.getEyePosition();
        if (!canPossiblyAimAt(origin, playerTarget)) {
            stopDebugSwivelFollow("player_outside_yaw_sector", true);
            return;
        }
        double target = DebugSwivelFollow.yawTargetDegrees(origin, playerTarget);
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
            LOGGER.warn("Cancelled yaw Swivel player follow controller={} reason={}",
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
            LOGGER.warn("Cancelled yaw Swivel sweep controller={} reason={}", worldPosition, outcome);
        }
    }

    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (targetPos == null) {
            returnToInitialOrientation();
            return;
        }

        KineticMountAdapterResolution kineticResolution = resolveKineticMount();
        if (kineticResolution.isStructuralSelection()) {
            Vec3 origin = PhysicsHandler.getWorldVec(this);
            setStructuralAimDirection(targetPos.subtract(origin), false);
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
        returnToInitialOrientation();
    }

    public void returnToInitialOrientation() {
        kineticControllerState.endContinuousTracking();
        targetAngle = getInitialOrientationTargetAngle();
        isRunning = true;
        kineticControllerState.onTargetChanged(true, targetAngle, DEADBAND_DEG);
        notifyUpdate();
        setChanged();
    }

    private double getInitialOrientationTargetAngle() {
        Mount mount = resolveMount();
        if (mount == null) {
            return 0.0;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()
                && mount.cbc.getContraption() != null
                && mount.cbc.getContraption().getContraption() instanceof AbstractMountedCannonContraption cannon) {
            Direction initial = cannon.initialOrientation();
            if (initial != null && initial.getAxis().isHorizontal()) {
                return controllerYawForCardinalDirection(initial);
            }
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.maybeUpdateYawZeroFromCannonInitialOrientation();
        }

        return 0.0;
    }

    public boolean atTargetYaw(boolean lag) {
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
            return cannonHandler.atTargetYaw(mount.cbc, lag);
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            return physHandler.atTargetYaw(mount.phys, lag);
        }

        return false;
    }

    public boolean isUpsideDown() {
        if (level == null) {
            return false;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(DirectionalKineticBlock.FACING)) {
            return false;
        }

        return state.getValue(DirectionalKineticBlock.FACING) == Direction.UP;
    }

    public void markMountDirtyExternal() {
        mountDirty = true;
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

    @Nullable
    public Mount resolveMount() {
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
            BlockEntity adjacent = level.getBlockEntity(mountPos);

            if (Mods.CREATEBIGCANNONS.isLoaded() && adjacent instanceof CannonMountBlockEntity cbc) {
                newMount = Mount.cbc(cbc);
            } else if (Mods.VS_CLOCKWORK.isLoaded() && adjacent instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            }
        }

        cachedMount = newMount;
        mountDirty = false;

        if (newMount == null && !hasStructuralKineticSelection()) {
            isRunning = false;
            hasLastCbcYawWritten = false;
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

        BlockPos preferred = isUpsideDown() ? worldPosition.below() : worldPosition.above();
        BlockPos opposite = isUpsideDown() ? worldPosition.above() : worldPosition.below();

        if (level instanceof ServerLevel serverLevel) {
            BlockPos linkedMount = WeaponNetworkRuntime.get(serverLevel).getMountForController(worldPosition);
            if (linkedMount != null) {
                // A network link is authoritative even when its mount does not support direct
                // yaw. The kinetic actuator may still drive a swivel, while returning this
                // position prevents fallback rotation of an unrelated adjacent cannon mount.
                return linkedMount;
            }
        }

        if (isControllableMount(preferred)) {
            return preferred;
        }
        if (isControllableMount(opposite)) {
            return opposite;
        }

        return preferred;
    }

    private boolean isControllableMount(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return (Mods.CREATEBIGCANNONS.isLoaded() && be instanceof CannonMountBlockEntity)
                || (Mods.VS_CLOCKWORK.isLoaded() && be instanceof PhysBearingBlockEntity);
    }

    /** Input power is sampled, never connected to this isolated generator. */
    public double getAvailableInputSpeed() {
        return KineticPowerSource.strongestAdjacentShaftRpm(this, Direction.Axis.Y);
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

    /**
     * Applies a radar solver's world launch direction as an absolute Swivel
     * command. This is intentionally separate from setTargetAngle so CC and
     * ordinary mount commands retain their finite-command watchdog.
     */
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
        if (!Double.isFinite(target)
                || !KineticAngleMath.isInInclusiveWrappedInterval(
                        target, minAngleDeg, maxAngleDeg)) {
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
        targetAngle = target;
        isRunning = true;
        kineticControllerState.onTargetChanged(true, target, DEADBAND_DEG);
        notifyUpdate();
        setChanged();
        return true;
    }

    private KineticMountAdapterResolution resolveKineticMount() {
        return Mods.SIMULATED.isLoaded()
                ? SimulatedSwivelMountAdapter.resolve(this, Direction.Axis.Y)
                : KineticMountAdapterResolution.absent("simulated_not_loaded");
    }

    private boolean isPerpendicularNeighbor(BlockPos pos) {
        int dx = pos.getX() - worldPosition.getX();
        int dy = pos.getY() - worldPosition.getY();
        int dz = pos.getZ() - worldPosition.getZ();
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1 && dy == 0;
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
        minAngleDeg = wrap360(wrap180(v));
        notifyUpdate();
        setChanged();
    }

    public void setMaxAngleDeg(double v) {
        maxAngleDeg = wrap360(wrap180(v));
        notifyUpdate();
        setChanged();
    }

    public boolean canPossiblyAimAt(Vec3 originWorld, Vec3 targetWorld) {
        if (originWorld == null || targetWorld == null) {
            return false;
        }

        Vec3 d = targetWorld.subtract(originWorld);
        if (d.lengthSqr() < 1.0e-6) {
            return true;
        }

        double yawDeg;
        if (hasStructuralKineticSelection()) {
            KineticAimFrame aimFrame = getStructuralAimFrame();
            if (aimFrame == null) {
                return false;
            }
            yawDeg = aimFrame.controllerTargetDegrees(CannonAxis.YAW, d);
        } else {
            yawDeg = wrap360(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        }

        return Double.isFinite(yawDeg)
                && KineticAngleMath.isInInclusiveWrappedInterval(
                        yawDeg, minAngleDeg, maxAngleDeg);
    }

    public double computeYawToTargetDeg(Vec3 cannonCenterWorld, Vec3 targetWorld) {
        SubLevelAccess subLevel = getSublevelIfPresent();

        Vec3 cannonCenter = cannonCenterWorld;
        Vec3 target = targetWorld;

        if (Mods.SABLE.isLoaded() && subLevel != null) {
            cannonCenter = toShipSpace(subLevel, cannonCenterWorld);
            target = toShipSpace(subLevel, targetWorld);
        }

        double dx = target.x - cannonCenter.x;
        double dz = target.z - cannonCenter.z;

        return Math.toDegrees(Math.atan2(dz, dx)) + 90.0;
    }

    @Nullable
    private SubLevelAccess getSublevelIfPresent() {
        if (level == null) {
            return null;
        }

        if (!Mods.SABLE.isLoaded()) {
            return null;
        }

        return SableCompanion.INSTANCE.getContaining(level, worldPosition);
    }

    private Vec3 toShipSpace(SubLevelAccess subLevel, Vec3 worldPos) {
        Vector3d tmp = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
        subLevel.logicalPose().transformPositionInverse(tmp);
        return new Vec3(tmp.x, tmp.y, tmp.z);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        if (compound.contains("MinAngleDeg", Tag.TAG_DOUBLE)) {
            minAngleDeg = compound.getDouble("MinAngleDeg");
        }
        if (compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE)) {
            maxAngleDeg = compound.getDouble("MaxAngleDeg");
        }

        targetAngle = wrap360(compound.getDouble("TargetAngle"));
        isRunning = compound.getBoolean("IsRunning");

        hasLastCbcYawWritten = false;
        physHandler.read(compound);
        kineticControllerState.read(compound, wasMoved, clientPacket);
        generatedSpeed = compound.getFloat("GeneratedSpeed");
        isolatedGeneratorInitialized = false;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        double storedTarget = clientPacket ? targetAngle
                : debugSwivelFollow.persistentTarget(targetAngle);
        boolean storedRunning = clientPacket ? isRunning
                : debugSwivelFollow.persistentRunning(isRunning);
        compound.putDouble("TargetAngle", wrap360(storedTarget));
        compound.putBoolean("IsRunning", storedRunning);

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

    public void setInternalTargetAngle(double targetAngle) {
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

    void recordCbcYawWritten(double yawDeg) {
        this.lastCbcYawWritten = wrap360(yawDeg);
        this.hasLastCbcYawWritten = true;
    }

    boolean hasLastCbcYawWritten() {
        return hasLastCbcYawWritten;
    }

    double getLastCbcYawWritten() {
        return lastCbcYawWritten;
    }

    void setInternalMinAngleDeg(double v) {
        this.minAngleDeg = v;
    }

    void setInternalMaxAngleDeg(double v) {
        this.maxAngleDeg = v;
    }

    public static double getToleranceDeg() {
        return TOLERANCE_DEG;
    }

    static double getDeadbandDeg() {
        return DEADBAND_DEG;
    }

    static double wrap360(double deg) {
        deg %= 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    static double wrap180(double deg) {
        deg = wrap360(deg);
        if (deg >= 180.0) deg -= 360.0;
        return deg;
    }

    static double shortestDelta(double from, double to) {
        return ((to - from + 540.0) % 360.0) - 180.0;
    }

    private static double controllerYawForCardinalDirection(Direction d) {
        return switch (d) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
    }

    enum MountKind {
        CBC,
        PHYS
    }

    static class Mount {
        final MountKind kind;
        final CannonMountBlockEntity cbc;
        final PhysBearingBlockEntity phys;

        private Mount(MountKind kind, @Nullable CannonMountBlockEntity cbc, @Nullable PhysBearingBlockEntity phys) {
            this.kind = kind;
            this.cbc = cbc;
            this.phys = phys;
        }

        static Mount cbc(CannonMountBlockEntity cbc) {
            return new Mount(MountKind.CBC, cbc, null);
        }

        static Mount phys(PhysBearingBlockEntity phys) {
            return new Mount(MountKind.PHYS, null, phys);
        }
    }
}
