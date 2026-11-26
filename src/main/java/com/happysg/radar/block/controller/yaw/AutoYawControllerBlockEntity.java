package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;

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
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;

import javax.annotation.Nullable;

public class AutoYawControllerBlockEntity extends GeneratingKineticBlockEntity implements ControllerLimitAccess, ControllerInputShaft {

    private static final double TOLERANCE_DEG = 0.15;
    private static final double DEADBAND_DEG = 0.5;
    private static final int MOVEMENT_LIMITS_VERSION = 1;

    private double targetAngle = 0.0;
    private boolean isRunning = false;

    private double lastCbcYawWritten = 0.0;
    private boolean hasLastCbcYawWritten = false;

    private double minAngleDeg = -180.0;
    private double maxAngleDeg = 180.0;
    private double requestedTargetAngle = 0.0;
    private boolean targetLimitConstrained;
    private double lastLimitNeutralDeg = Double.NaN;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;
    private long mountResolutionTick = Long.MIN_VALUE;
    @Nullable
    private BlockPos cachedMountEndpointPos;

    private final KineticControllerState kineticControllerState = new KineticControllerState(CannonAxis.YAW);
    private final CbcShaftDrive cbcShaftDrive = new CbcShaftDrive(CannonAxis.YAW);
    private float generatedSpeed;
    private boolean isolatedGeneratorInitialized;

    private final CannonMountYaw cannonHandler;
    private final PhysBearingYaw physHandler;
    private final ApiMountYaw apiHandler;

    public AutoYawControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountYaw(this);
        this.physHandler = new PhysBearingYaw(this);
        this.apiHandler = new ApiMountYaw(this);
    }

    @Override
    public void tick() {
        initializeIsolatedGenerator();
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        refreshLimitNeutral();
        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            cbcShaftDrive.clear();
            kineticControllerState.tick(this, structural, isRunning, targetAngle, 0.05,
                    getAvailableInputSpeed(), this::legalYawDelta, this::commandGeneratedSpeed);
            flushKineticStateSync();
            return;
        }
        if (kineticControllerState.isStructuralMode()) {
            kineticControllerState.release();
            commandGeneratedSpeed(0.0);
            flushKineticStateSync();
        }
        Mount mount = resolveMount();
        if (mount == null || mount.kind != MountKind.CBC) {
            cbcShaftDrive.reset(this::commandGeneratedSpeed);
        }
        if (mount != null) {
            if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
                cbcShaftDrive.tick(
                        this,
                        CannonMountContext.of(mount.cbc),
                        isRunning,
                        targetAngle,
                        getAvailableInputSpeed(),
                        getBlockState().getValue(DirectionalKineticBlock.FACING).getOpposite(),
                        this::legalYawDelta,
                        this::commandGeneratedSpeed
                );
            } else if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
                if (mount.phys.isRemoved() || !mount.phys.isRunning()) {
                    if (isRunning) {
                        failClosedRadarAim();
                    }
                    return;
                }
                if (level.getGameTime() % 20 == 5) {
                    physHandler.maybeUpdateYawZeroFromCannonInitialOrientation();
                }
                physHandler.tick(mount.phys);
            } else if (mount.kind == MountKind.API && mount.api != null) {
                apiHandler.tick(mount.api);
            }
        }

    }

    public void setTargetAngle(float targetAngle) {
        kineticControllerState.endContinuousTracking();
        applyTargetRequest(targetAngle, true, true);
    }

    public double getTargetAngle() {
        return targetAngle;
    }

    public double getRequestedTargetAngle() {
        return requestedTargetAngle;
    }

    private void applyTargetRequest(double requestedAngle, boolean running, boolean notify) {
        if (!Double.isFinite(requestedAngle)) {
            return;
        }
        requestedTargetAngle = wrap360(requestedAngle);
        double neutral = getLimitNeutralAngleDeg();
        ControllerMovementLimits limits = getMovementLimits();
        double applied = limits.clampControllerTarget(requestedTargetAngle, neutral);
        targetLimitConstrained = !limits.allowsControllerTarget(requestedTargetAngle, neutral);
        targetAngle = applied;
        isRunning = running;
        kineticControllerState.onTargetChanged(running, applied, 0.05);
        if (notify) {
            notifyUpdate();
            setChanged();
        }
    }

    public double getLimitNeutralAngleDeg() {
        KineticMountAdapterResolution kinetic = resolveKineticMount();
        if (kinetic.hasAdapter() && kinetic.adapter().frameIdentity() != null) {
            return kinetic.adapter().frameIdentity().controllerNeutralDegrees();
        }
        Mount mount = resolveMount();
        if (mount != null && mount.kind == MountKind.CBC && mount.cbc != null) {
            CannonMountContext context = CannonMountContext.of(mount.cbc);
            Direction initial = context == null ? null : context.initialOrientation();
            if (initial != null && initial.getAxis().isHorizontal()) {
                return controllerYawForCardinalDirection(initial);
            }
        }
        return 0.0;
    }

    public double legalYawDelta(double currentControllerDegrees, double targetControllerDegrees) {
        return getMovementLimits().legalDelta(currentControllerDegrees,
                targetControllerDegrees, getLimitNeutralAngleDeg());
    }

    private void refreshLimitNeutral() {
        double neutral = getLimitNeutralAngleDeg();
        if (!Double.isFinite(neutral)) {
            return;
        }
        if (Double.isFinite(lastLimitNeutralDeg)
                && Math.abs(shortestDelta(lastLimitNeutralDeg, neutral)) <= 1.0e-7) {
            return;
        }
        lastLimitNeutralDeg = neutral;
        double oldTarget = targetAngle;
        boolean oldConstrained = targetLimitConstrained;
        applyTargetRequest(requestedTargetAngle, isRunning, false);
        if (Math.abs(shortestDelta(oldTarget, targetAngle)) > 1.0e-7
                || oldConstrained != targetLimitConstrained) {
            notifyUpdate();
            setChanged();
        }
    }

    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (targetPos == null) {
            isRunning = false;
            kineticControllerState.endContinuousTracking();
            kineticControllerState.onTargetChanged(false, targetAngle, 0.05);
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

    public boolean atTargetYaw(boolean lag) {
        if (level == null || targetLimitConstrained) {
            return false;
        }

        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            return kineticControllerState.isAlignedForFiring(structural, worldPosition, isRunning,
                    targetAngle, TOLERANCE_DEG + (lag ? 0.0 : 0.15));
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
        if (mount.kind == MountKind.API && mount.api != null) {
            return apiHandler.atTargetYaw(mount.api, lag, 0.0, Double.POSITIVE_INFINITY);
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
        invalidateKineticActuator();
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

    @Nullable
    public Mount resolveMount() {
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
            BlockEntity adjacent = level.getBlockEntity(mountPos);

            if (Mods.CREATEBIGCANNONS.isLoaded() && adjacent instanceof CannonMountBlockEntity cbc) {
                newMount = Mount.cbc(cbc);
            } else if (Mods.VS_CLOCKWORK.isLoaded() && adjacent instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            } else {
                RadarMountAdapter api = RadarMountRegistry.find(level, mountPos);
                if (api != null && api.supportsYaw()) {
                    newMount = Mount.api(api, mountPos);
                }
            }
        }

        Mount oldMount = cachedMount;
        boolean mountChanged = !sameMount(oldMount, newMount);
        cachedMount = newMount;
        cachedMountEndpointPos = mountPos == null ? null : mountPos.immutable();
        mountResolutionTick = level.getGameTime();
        mountDirty = false;

        if (newMount == null || (oldMount != null && mountChanged)) {
            isRunning = false;
            hasLastCbcYawWritten = false;
            physHandler.reset();
            cbcShaftDrive.reset(this::commandGeneratedSpeed);
            kineticControllerState.endContinuousTracking();
            kineticControllerState.onTargetChanged(false, targetAngle, 0.05);
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

    public double getMinAngleDeg() {
        return minAngleDeg;
    }

    public double getMaxAngleDeg() {
        return maxAngleDeg;
    }

    @Override
    public CannonAxis getControlledAxis() {
        return CannonAxis.YAW;
    }

    @Override
    public ControllerMovementLimits getMovementLimits() {
        return new ControllerMovementLimits(CannonAxis.YAW, minAngleDeg, maxAngleDeg);
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
        var validated = ControllerMovementLimits.validated(CannonAxis.YAW, minDegrees, maxDegrees);
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

    public boolean canPossiblyAimAt(Vec3 originWorld, Vec3 targetWorld) {
        if (originWorld == null || targetWorld == null) {
            return false;
        }
        Vec3 difference = targetWorld.subtract(originWorld);
        if (difference.lengthSqr() < 1.0e-6) {
            return true;
        }
        KineticMountAdapterResolution structural = resolveKineticMount();
        if (structural.isStructuralSelection()) {
            KineticAimFrame frame = getStructuralAimFrame();
            if (frame == null) {
                return false;
            }
            double yaw = frame.controllerTargetDegrees(CannonAxis.YAW, difference);
            return Double.isFinite(yaw) && getMovementLimits().allowsControllerTarget(
                    yaw, getLimitNeutralAngleDeg());
        }
        double yaw = wrap360(computeYawToTargetDeg(originWorld, targetWorld) + 180.0);
        Mount mount = resolveMount();
        if (mount != null && mount.kind == MountKind.PHYS) {
            yaw = physHandler.toRelativeControllerAngle(yaw);
        }
        return Double.isFinite(yaw)
                && getMovementLimits().allowsControllerTarget(yaw, getLimitNeutralAngleDeg());
    }

    public double computeYawToTargetDeg(Vec3 cannonCenterWorld, Vec3 targetWorld) {
        Ship ship = getShipIfPresent();

        Vec3 cannonCenter = cannonCenterWorld;
        Vec3 target = targetWorld;

        if (Mods.VALKYRIENSKIES.isLoaded() && ship != null) {
            cannonCenter = toShipSpace(ship, cannonCenterWorld);
            target = toShipSpace(ship, targetWorld);
        }

        double dx = target.x - cannonCenter.x;
        double dz = target.z - cannonCenter.z;

        return Math.toDegrees(Math.atan2(dz, dx)) + 90.0;
    }

    @Nullable
    private Ship getShipIfPresent() {
        if (level == null) {
            return null;
        }

        if (!Mods.VALKYRIENSKIES.isLoaded()) {
            return null;
        }

        return VSGameUtilsKt.getShipManagingPos(level, worldPosition);
    }

    private Vec3 toShipSpace(Ship ship, Vec3 worldPos) {
        Vector3d tmp = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
        ship.getTransform().getWorldToShip().transformPosition(tmp);
        return new Vec3(tmp.x, tmp.y, tmp.z);
    }



    public KineticMountAdapterResolution resolveKineticMount() {
        return KineticMountRegistry.resolve(this, Direction.Axis.Y);
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
        return state.getValue(DirectionalKineticBlock.FACING);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        ControllerMovementLimits limits = ControllerMovementLimits.defaults(CannonAxis.YAW);
        if (compound.getInt("MovementLimitsVersion") >= MOVEMENT_LIMITS_VERSION
                && compound.contains("MinAngleDeg", Tag.TAG_DOUBLE)
                && compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE)) {
            limits = ControllerMovementLimits.validated(CannonAxis.YAW,
                    compound.getDouble("MinAngleDeg"),
                    compound.getDouble("MaxAngleDeg")).orElse(limits);
        }
        minAngleDeg = limits.minDegrees();
        maxAngleDeg = limits.maxDegrees();

        if (clientPacket) {
            targetAngle = wrap360(compound.getDouble("TargetAngle"));
            requestedTargetAngle = compound.contains("RequestedTargetAngle", Tag.TAG_DOUBLE)
                    ? wrap360(compound.getDouble("RequestedTargetAngle")) : targetAngle;
            targetLimitConstrained = compound.getBoolean("TargetLimitConstrained");
            isRunning = compound.getBoolean("IsRunning");
        } else {
            targetAngle = 0.0;
            requestedTargetAngle = 0.0;
            targetLimitConstrained = false;
            isRunning = false;
        }
        lastLimitNeutralDeg = Double.NaN;

        hasLastCbcYawWritten = false;
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

        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.putInt("MovementLimitsVersion", MOVEMENT_LIMITS_VERSION);
        compound.putDouble("TargetAngle", wrap360(targetAngle));
        compound.putDouble("RequestedTargetAngle", requestedTargetAngle);
        compound.putBoolean("TargetLimitConstrained", targetLimitConstrained);
        compound.putBoolean("IsRunning", isRunning);

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

    public void setInternalTargetAngle(double targetAngle) {
        applyTargetRequest(targetAngle, isRunning, false);
    }

    void setRunning(boolean running) {
        this.isRunning = running;
        kineticControllerState.onTargetChanged(running, targetAngle, 0.05);
    }

    boolean isRunningController() {
        return isRunning;
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

    private static double controllerYawForCardinalDirection(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
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
