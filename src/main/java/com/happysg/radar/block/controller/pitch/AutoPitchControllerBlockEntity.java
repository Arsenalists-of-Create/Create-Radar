package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.block.controller.kinetic.ControllerInputShaft;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.kinetic.KineticControllerState;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticMountRegistry;
import com.happysg.radar.compat.vs2.PhysicsHandler;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.CbcShaftDrive;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class AutoPitchControllerBlockEntity extends GeneratingKineticBlockEntity implements ControllerLimitAccess, ControllerInputShaft {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double CBC_TOLERANCE = 0.1;
    private static final double PHYS_TOLERANCE_DEG = 0.1;
    private static final double DEADBAND_DEG = 0.25;
    private static final int MOVEMENT_LIMITS_VERSION = 1;

    private double minAngleDeg = -90.0;
    private double maxAngleDeg = 90.0;

    private double targetAngle = 0.0;
    private double requestedTargetAngle = 0.0;
    private boolean targetLimitConstrained;
    public boolean isRunning = false;

    private boolean artillery = false;
    private boolean binoMode = false;
    private CompoundTag targetingTag = defaultTargetingTag();

    @Nullable
    public RadarTrack track;

    @Nullable
    private Vec3 lastTargetPos = null;

    public WeaponFiringControl firingControl;
    public AutoYawControllerBlockEntity autoyaw;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;
    private long mountResolutionTick = Long.MIN_VALUE;
    @Nullable
    private BlockPos cachedMountEndpointPos;

    private final KineticControllerState kineticControllerState = new KineticControllerState(CannonAxis.PITCH);
    private final CbcShaftDrive cbcShaftDrive = new CbcShaftDrive(CannonAxis.PITCH);
    private float generatedSpeed;
    private boolean isolatedGeneratorInitialized;

    private final CannonMountPitch cannonHandler;
    private final PhysBearingPitch physHandler;
    private final ApiMountPitch apiHandler;

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountPitch(this);
        this.physHandler = new PhysBearingPitch(this);
        this.apiHandler = new ApiMountPitch(this);
    }

    @Override
    public void tick() {
        initializeIsolatedGenerator();
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            cbcShaftDrive.clear();
            kineticControllerState.tick(this, structural, isRunning, targetAngle, 0.05,
                    getAvailableInputSpeed(), this::commandGeneratedSpeed);
            flushKineticStateSync();
            return;
        }
        if (kineticControllerState.isStructuralMode()) {
            kineticControllerState.release();
            commandGeneratedSpeed(0.0);
            flushKineticStateSync();
        }
        if (level instanceof ServerLevel serverLevel) {
            if (WeaponNetworkRuntime.getWeaponGroupViewFromEndpoint(serverLevel, worldPosition) == null) {
                cbcShaftDrive.reset(this::commandGeneratedSpeed);
                return;
            }
        }

        if (firingControl == null) {
            getFiringControl();
        }

        Mount mount = resolveMount();
        if (mount == null) {
            isRunning = false;
            cbcShaftDrive.reset(this::commandGeneratedSpeed);
            return;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            cbcShaftDrive.tick(
                    this,
                    CannonMountContext.of(mount.cbc),
                    isRunning,
                    targetAngle,
                    getAvailableInputSpeed(),
                    getBlockState().getValue(HorizontalDirectionalBlock.FACING),
                    null,
                    this::commandGeneratedSpeed
            );
            return;
        }

        cbcShaftDrive.reset(this::commandGeneratedSpeed);
        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            physHandler.tick(mount.phys);
        } else if (mount.kind == MountKind.API && mount.api != null) {
            apiHandler.tick(mount.api);
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

        if (view.yawPos() != null && level.getBlockEntity(view.yawPos()) instanceof AutoYawControllerBlockEntity aYCBE) {
            autoyaw = aYCBE;
        }

        BlockPos mountPos = view.mountPos();
        if (mountPos == null) {
            return;
        }

        BlockEntity be = level.getBlockEntity(mountPos);
        if (be instanceof CannonMountBlockEntity mount) {
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

        return WeaponNetworkRuntime.getWeaponGroupViewFromEndpoint(sl, worldPosition);
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
        kineticControllerState.endContinuousTracking();
        applyTargetRequest(angle, true, true);
    }

    public double getTargetAngle() {
        return targetAngle;
    }

    public double getRequestedTargetAngle() {
        return requestedTargetAngle;
    }

    private void applyTargetRequest(double requestedAngle, boolean running, boolean resetPhysicalTracking) {
        if (!Double.isFinite(requestedAngle)) {
            return;
        }
        requestedTargetAngle = requestedAngle;
        ControllerMovementLimits effective = getMovementLimits().intersection(getSupportedMovementLimits()).orElse(null);
        if (effective == null) {
            targetLimitConstrained = true;
            isRunning = false;
            kineticControllerState.onTargetChanged(false, targetAngle, 0.05);
            if (resetPhysicalTracking) {
                physHandler.reset();
            }
            notifyUpdate();
            setChanged();
            return;
        }
        targetLimitConstrained = !effective.allowsControllerTarget(requestedAngle, 0.0);
        targetAngle = effective.clampControllerTarget(requestedAngle, 0.0);
        isRunning = running;
        kineticControllerState.onTargetChanged(running, targetAngle, 0.05);
        if (resetPhysicalTracking) {
            physHandler.reset();
        }
        notifyUpdate();
        setChanged();
    }

    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (targetPos == null) {
            isRunning = false;
            kineticControllerState.endContinuousTracking();
            kineticControllerState.onTargetChanged(false, targetAngle, 0.05);
            lastTargetPos = null;
            physHandler.reset();

            notifyUpdate();
            setChanged();
            return;
        }

        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            if (!structural.hasAdapter()) {
                failClosedRadarAim();
                return;
            }
            Vec3 origin = PhysicsHandler.getWorldVec(level,
                    worldPosition.relative(structural.adapter().relativeDirection()).getCenter());
            if (!setRadarAimDirection(targetPos.subtract(origin))) {
                failClosedRadarAim();
            }
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
        } else if (mount.kind == MountKind.API && mount.api != null) {
            apiHandler.setTarget(mount.api, targetPos);
        }
    }

    public boolean atTargetPitch(boolean lag) {
        if (level == null || targetLimitConstrained) {
            return false;
        }

        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            return kineticControllerState.isAlignedForFiring(structural, worldPosition, isRunning,
                    targetAngle, CBC_TOLERANCE + (lag ? 0.0 : 0.15));
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
        if (mount.kind == MountKind.API && mount.api != null) {
            return apiHandler.atTargetPitch(mount.api, lag, 0.0, Double.POSITIVE_INFINITY);
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

    public void setSafeZones(List<AABB> safeZones) {
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
        BlockPos mountPos = getMountPos();
        if (mountPos == null) {
            return;
        }

        if (fromPos.equals(mountPos)) {
            mountDirty = true;
            invalidateKineticActuator();
        }
    }

    public void markMountDirtyExternal() {
        mountDirty = true;
        invalidateKineticActuator();
    }

    @Nullable
    Mount resolveMount() {
        if (level == null) {
            return null;
        }

        BlockPos mountPos = getMountPos();
        if (mountDirty || mountResolutionTick != level.getGameTime()
                || !java.util.Objects.equals(cachedMountEndpointPos, mountPos)
                || (cachedMount != null && cachedMount.kind == MountKind.API
                && (cachedMount.api == null || !cachedMount.api.isValid()))) {
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

        if (mountPos != null && level.hasChunkAt(mountPos)) {
            BlockEntity be = level.getBlockEntity(mountPos);

            if (Mods.CREATEBIGCANNONS.isLoaded() && be instanceof CannonMountBlockEntity cbc) {
                newMount = Mount.cbc(cbc);
            } else if (Mods.VS_CLOCKWORK.isLoaded() && be instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            } else {
                RadarMountAdapter api = RadarMountRegistry.find(level, mountPos);
                if (api != null && api.supportsPitch()) {
                    newMount = Mount.api(api, mountPos);
                }
            }
        }

        boolean mountChanged = !sameMount(cachedMount, newMount);
        cachedMount = newMount;
        cachedMountEndpointPos = mountPos == null ? null : mountPos.immutable();
        mountResolutionTick = level.getGameTime();
        mountDirty = false;

        if (newMount == null) {
            isRunning = false;
            lastTargetPos = null;
            physHandler.reset();
        }

        if (mountChanged) {
            setChanged();
            notifyUpdate();
        }
    }

    private static boolean sameMount(@Nullable Mount first, @Nullable Mount second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.kind != second.kind) {
            return false;
        }
        return switch (first.kind) {
            case CBC -> first.cbc == second.cbc;
            case PHYS -> first.phys == second.phys;
            case API -> first.apiPos != null && first.apiPos.equals(second.apiPos);
        };
    }

    @Nullable
    private BlockPos getMountPos() {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }

        return WeaponNetworkRuntime.getMountForController(sl, worldPosition);
    }

    public CompoundTag getTargetingTag() {
        return targetingTag;
    }

    public void setTargetingTag(CompoundTag targetingTag) {
        this.targetingTag = targetingTag == null ? defaultTargetingTag() : targetingTag.copy();
        setChanged();
        notifyUpdate();
    }

    private static CompoundTag defaultTargetingTag() {
        CompoundTag root = new CompoundTag();
        root.put("targeting", TargetingConfig.DEFAULT.toTag());
        return root;
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
        ControllerMovementLimits configured = new ControllerMovementLimits(CannonAxis.PITCH, minAngleDeg, maxAngleDeg);
        return configured.intersection(getSupportedMovementLimits()).orElse(configured);
    }

    @Override
    public ControllerMovementLimits getSupportedMovementLimits() {
        ControllerMovementLimits defaults = ControllerMovementLimits.defaults(CannonAxis.PITCH);
        if (resolveKineticMount().isStructuralSelection()) {
            return defaults;
        }
        Mount mount = resolveMount();
        if (mount == null || mount.kind != MountKind.CBC || mount.cbc == null) {
            return defaults;
        }
        CannonMountContext context = CannonMountContext.of(mount.cbc);
        CannonMountContext.PitchLimits pitchLimits = context == null ? null : context.pitchLimits();
        if (pitchLimits == null) {
            return defaults;
        }
        return ControllerMovementLimits.validated(CannonAxis.PITCH,
                Math.max(defaults.minDegrees(), pitchLimits.minDegrees()),
                Math.min(defaults.maxDegrees(), pitchLimits.maxDegrees())).orElse(defaults);
    }

    @Override
    public boolean hasAssembledControlledMount() {
        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            KineticMountAdapter adapter = structural.adapter();
            return structural.hasAdapter() && adapter.isValid() && adapter.isAssembled();
        }
        Mount mount = resolveMount();
        if (mount == null) {
            return false;
        }
        return switch (mount.kind) {
            case CBC -> {
                CannonMountContext context = CannonMountContext.of(mount.cbc);
                yield context != null && context.hasAssembledCannon();
            }
            case PHYS -> mount.phys != null && mount.phys.isRunning();
            case API -> mount.api != null && mount.api.isValid() && mount.api.isAssembled();
        };
    }

    @Override
    public boolean isTargetLimitConstrained() {
        return targetLimitConstrained;
    }

    @Override
    public boolean setMovementLimits(double minDegrees, double maxDegrees) {
        var validated = ControllerMovementLimits.validated(CannonAxis.PITCH, minDegrees, maxDegrees);
        if (validated.isEmpty()) {
            return false;
        }
        ControllerMovementLimits limits = validated.get();
        minAngleDeg = limits.minDegrees();
        maxAngleDeg = limits.maxDegrees();
        applyTargetRequest(requestedTargetAngle, isRunning, true);
        return true;
    }

    public void setMinAngleDeg(double value) {
        setMovementLimits(value, maxAngleDeg);
    }

    public void setMaxAngleDeg(double value) {
        setMovementLimits(minAngleDeg, value);
    }

    public boolean snapping() {
        double rpm = Math.abs(getAvailableInputSpeed());
        return rpm == 256.0;
    }



    public KineticMountAdapterResolution resolveKineticMount() {
        return KineticMountRegistry.resolve(this, getBlockState().getValue(HorizontalDirectionalBlock.FACING).getAxis());
    }

    private boolean isSelectedKineticMountEndpoint(KineticBlockEntity candidate) {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        return resolution.hasAdapter() && adapter != null && adapter.isValid()
                && adapter.isKineticEndpoint(candidate);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom,
                                     BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        if (isSelectedKineticMountEndpoint(target)) {
            return -1.0f;
        }
        return super.propagateRotationTo(target, stateFrom, stateTo, diff,
                connectedViaAxes, connectedViaCogs);
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return isSelectedKineticMountEndpoint(other)
                || super.isCustomConnection(other, state, otherState);
    }

    public void invalidateKineticActuator() {
        kineticControllerState.invalidate();
        commandGeneratedSpeed(0.0);
        flushKineticStateSync();
    }

    public void releaseKineticActuator() {
        kineticControllerState.release();
        commandGeneratedSpeed(0.0);
        flushKineticStateSync();
    }

    private void flushKineticStateSync() {
        if (kineticControllerState.consumeSyncRequested()) {
            setChanged();
            notifyUpdate();
        }
    }

    public boolean hasStructuralKineticSelectionForTargeting() {
        return resolveKineticMount().isStructuralSelection();
    }

    @Nullable
    public KineticAimFrame getStructuralAimFrame() {
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        return resolution.hasAdapter() && adapter.isValid()
                && adapter.isAssembled() && adapter.isLocked()
                && adapter.frameIdentity() != null ? adapter.aimFrame() : null;
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
        return adapter.effectiveDegreesPerTick(KineticControllerState.matchingDirectCbcDriveRpm(
                adapter, getAvailableInputSpeed()));
    }

    public boolean setRadarAimDirection(@Nullable Vec3 worldAimDirection) {
        return setRadarAimDirection(worldAimDirection, Double.POSITIVE_INFINITY);
    }

    public boolean setRadarAimDirection(@Nullable Vec3 worldAimDirection,
                                        double maximumFiringToleranceDegrees) {
        if (level == null || level.isClientSide() || worldAimDirection == null
                || worldAimDirection.lengthSqr() < 1.0e-12) {
            return false;
        }
        KineticMountAdapterResolution resolution = resolveKineticMount();
        KineticMountAdapter adapter = resolution.adapter();
        if (!resolution.hasAdapter() || adapter == null || !adapter.isValid()
                || !adapter.isAssembled() || !adapter.isLocked()
                || adapter.frameIdentity() == null || adapter.aimFrame() == null) {
            invalidateKineticActuator();
            return false;
        }
        double target = adapter.controllerTargetForWorldDirection(worldAimDirection);
        if (!Double.isFinite(target)) {
            failClosedRadarAim();
            return false;
        }
        kineticControllerState.beginContinuousTracking(maximumFiringToleranceDegrees);
        applyTargetRequest(target, true, true);
        return !targetLimitConstrained;
    }

    public void endRadarTracking() {
        kineticControllerState.endContinuousTracking();
    }

    public void failClosedRadarAim() {
        kineticControllerState.endContinuousTracking();
        isRunning = false;
        kineticControllerState.onTargetChanged(false, targetAngle, 0.05);
        commandGeneratedSpeed(0.0);
        flushKineticStateSync();
        notifyUpdate();
        setChanged();
    }

    public double getKineticDesiredBearingTarget() {
        return kineticControllerState.getDesiredBearingTarget();
    }

    public double getKineticBearingSetpoint() {
        return kineticControllerState.getBearingSetpointDegrees();
    }

    public double getKineticPhysicalBearing() {
        return kineticControllerState.getPhysicalBearingDegrees();
    }

    public double getKineticRemainingDegrees() {
        return kineticControllerState.getRemainingDegrees();
    }

    public double getKineticSetpointCompensation() {
        return kineticControllerState.getSetpointCompensationDegrees();
    }

    public String getKineticLifecycle() {
        return kineticControllerState.getLifecycleName();
    }

    @Nullable
    public String getKineticBlockedReason() {
        return kineticControllerState.getBlockedReason();
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
        if (next != 0.0f && Math.abs(next - generatedSpeed) < 1.0e-5f) {
            return;
        }
        generatedSpeed = next;
        updateGeneratedRotation();
        setChanged();
    }

    @Override
    public float getGeneratedSpeed() {
        return generatedSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return 2048.0f;
    }

    @Override
    public double getAvailableInputSpeed() {
        return KineticPowerSource.adjacentShaftRpm(this, getInputShaftDirection());
    }

    @Override
    public Direction getInputShaftDirection() {
        BlockState state = getBlockState();
        return state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        targetAngle = clientPacket ? compound.getDouble("TargetAngle") : 0.0;
        isRunning = clientPacket && compound.getBoolean("IsRunning");

        double storedMin = compound.contains("MinAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MinAngleDeg") : -90.0;
        double storedMax = compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MaxAngleDeg") : 90.0;
        ControllerMovementLimits limits = ControllerMovementLimits.validated(CannonAxis.PITCH, storedMin, storedMax)
                .orElse(ControllerMovementLimits.defaults(CannonAxis.PITCH));
        minAngleDeg = limits.minDegrees();
        maxAngleDeg = limits.maxDegrees();
        targetingTag = compound.contains("Targeting", Tag.TAG_COMPOUND)
                ? compound.getCompound("Targeting").copy() : defaultTargetingTag();
        requestedTargetAngle = clientPacket && compound.contains("RequestedTargetAngle", Tag.TAG_DOUBLE)
                ? compound.getDouble("RequestedTargetAngle") : targetAngle;
        targetLimitConstrained = clientPacket && !limits.allowsControllerTarget(requestedTargetAngle, 0.0);
        targetAngle = clientPacket ? limits.clampControllerTarget(requestedTargetAngle, 0.0) : 0.0;

        lastTargetPos = null;
        physHandler.read(compound);
        kineticControllerState.read(compound, clientPacket);
        generatedSpeed = clientPacket ? compound.getFloat("GeneratedSpeed") : 0.0f;
        if (!clientPacket) {
            isolatedGeneratorInitialized = false;
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        compound.putDouble("TargetAngle", targetAngle);
        compound.putBoolean("IsRunning", isRunning);
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
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide()) {
            cbcShaftDrive.clear();
            kineticControllerState.release();
            generatedSpeed = 0.0f;
            isolatedGeneratorInitialized = false;
        }
        super.onChunkUnloaded();
    }

    public static Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    void setInternalTargetAngle(double targetAngle) {
        applyTargetRequest(targetAngle, isRunning, false);
    }

    void setRunning(boolean running) {
        this.isRunning = running;
        kineticControllerState.onTargetChanged(running, targetAngle, 0.05);
    }

    boolean isRunningController() {
        return isRunning;
    }

    public boolean isArtillery() {
        return artillery;
    }

    void setLastTargetPos(@Nullable Vec3 pos) {
        this.lastTargetPos = pos;
    }

    @Nullable
    Vec3 getLastTargetPos() {
        return lastTargetPos;
    }

    public static double getCbcTolerance() {
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

    enum MountKind {
        CBC,
        PHYS,
        API
    }

    static class Mount {
        final MountKind kind;
        final CannonMountBlockEntity cbc;
        final PhysBearingBlockEntity phys;
        final RadarMountAdapter api;
        final BlockPos apiPos;

        private Mount(MountKind kind, @Nullable CannonMountBlockEntity cbc,
                      @Nullable PhysBearingBlockEntity phys, @Nullable RadarMountAdapter api,
                      @Nullable BlockPos apiPos) {
            this.kind = kind;
            this.cbc = cbc;
            this.phys = phys;
            this.api = api;
            this.apiPos = apiPos;
        }

        static Mount cbc(CannonMountBlockEntity cbc) {
            return new Mount(MountKind.CBC, cbc, null, null, null);
        }

        static Mount phys(PhysBearingBlockEntity phys) {
            return new Mount(MountKind.PHYS, null, phys, null, null);
        }

        static Mount api(RadarMountAdapter api, BlockPos pos) {
            return new Mount(MountKind.API, null, null, api, pos.immutable());
        }
    }
}
