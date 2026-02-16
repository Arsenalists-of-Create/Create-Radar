package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import net.minecraft.core.Direction;


import javax.annotation.Nullable;
import java.util.List;

public class AutoYawControllerBlockEntity extends KineticBlockEntity{

    private static final double TOLERANCE_DEG = 0.15;

    private double targetAngle; // degrees [0,360)

    private double prevTargetAngle = 0.0;
    private boolean hasPrevTarget = false;

    private double lastCbcYawWritten = 0.0;
    private boolean hasLastCbcYawWritten = false;

    private boolean isRunning;

    @Nullable private Mount cachedMount = null;
    @Nullable private MountKind cachedMountKind = null;
    private boolean mountDirty = true;
    private BlockPos cachedAdjacentPos = BlockPos.ZERO;
    private final double MIN_MOVE_PER_TICK = 0.02;
    private static final double MAX_MOVE_PER_TICK = RadarConfig.server().controllerPhysbearingMaxSpeed.get();
    private static final double SNAP_DISTANCE = 37.0;
    private static final double DEADBAND_DEG = .5;
    private BlockPos lastKnownPos = BlockPos.ZERO;
    private double yawZeroOffsetDeg = 0.0;
    private boolean hasYawZeroOffset = false;
    private double lastYawZeroOffsetDeg = 0.0;
    private MountKind currentmount;



    public AutoYawControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    // ===== Tick =====
    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide())
            return;
        Mount mount = resolveMount();
        if (mount == null) return;

        if (mount.kind == MountKind.CBC) {
            if (Mods.CREATEBIGCANNONS.isLoaded()) {
                rotateCBC(mount.cbc);
                currentmount = MountKind.CBC;
            }
            return;
        }
        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            if(level.getGameTime() %20 == 5) {
                maybeUpdateYawZeroFromCannonInitialOrientation();
            }
            currentmount = MountKind.PHYS;
            rotatePhysBearing(mount.phys);
        }
        if (!level.isClientSide && level.getGameTime() % 40 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                if (lastKnownPos.equals(worldPosition))
                    return;

                ResourceKey<Level> dim = serverLevel.dimension();
                WeaponNetworkData data = WeaponNetworkData.get(serverLevel);

                boolean updated = data.updateWeaponEndpointPosition(
                        dim,
                        lastKnownPos,
                        worldPosition
                );

                // only commit the new position if the network accepted it
                if (updated) {
                    lastKnownPos = worldPosition;
                    setChanged();
                }
            }
        }

    }

    // ===== Public API =====
    public void setTargetAngle(float targetAngle) {
        this.targetAngle = clampYawToLimits(targetAngle);
        this.isRunning = true;
        notifyUpdate();
        setChanged();
    }


    public double getTargetAngle() {
        return targetAngle;
    }

    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide())
            return;

        if (targetPos == null) {
            isRunning = false;

            notifyUpdate();
            setChanged();
            return;
        }
        BlockPos abovepos = worldPosition.above();
        Vec3 mountpos = Vec3.ZERO;
        if(level.getBlockEntity(abovepos) instanceof CannonMountBlockEntity mountBlock) {
            if (PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
                List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mountBlock, targetPos, (ServerLevel) level);
                if (angles == null || angles.isEmpty() || angles.get(0).isEmpty())
                    return;

                // yaw
                this.targetAngle = clampYawToLimits(angles.get(0).get(1));


                isRunning = true;
                notifyUpdate();
                setChanged();
                return;
            }
        }

        isRunning = true;

        Vec3 cannonCenter = isUpsideDown()
                ? worldPosition.below(3).getCenter()
                : worldPosition.above(3).getCenter();
        // i'm computing yaw in ship-space when we're on a VS2 ship
        double angle = computeYawToTargetDeg(cannonCenter, targetPos);
        double newAngle = wrap360(angle) + 180.0;

        if (currentmount == MountKind.PHYS && hasYawZeroOffset) {
            newAngle = wrap360(newAngle - yawZeroOffsetDeg);
        }

        this.targetAngle = clampYawToLimits(newAngle);
        notifyUpdate();
        setChanged();
    }

    /** Works for either mount type */
    public boolean atTargetYaw(boolean lag) {
        if (level == null) return false;

        Mount mount = resolveMount();
        if (mount == null) return false;

        // i increase tolerance slightly if we're not lag-compensating
        double effectiveTolerance = TOLERANCE_DEG;
        if (!lag) {
            effectiveTolerance += 0.15;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            PitchOrientedContraptionEntity contraption = mount.cbc.getContraption();
            if (contraption == null) return false;

            double desired = hasPrevTarget
                    ? wrap360(clampYawToLimits(prevTargetAngle))
                    : wrap360(clampYawToLimits(targetAngle));

            // i prefer what we actually commanded (contraption.yaw can lag behind)
            double current = hasLastCbcYawWritten
                    ? wrap360(lastCbcYawWritten)
                    : wrap360(contraption.yaw);

            return Math.abs(shortestDelta(current, desired)) < effectiveTolerance;
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            Double actualRad = mount.phys.getActualAngle();
            if (actualRad == null) return false;

            double currentDeg = wrap360(Math.toDegrees(actualRad));
            double desiredDeg = wrap360(360.0 - targetAngle);

            return Math.abs(shortestDelta(currentDeg, desiredDeg))
                    < Math.max(effectiveTolerance, DEADBAND_DEG);
        }

        return false;
    }

    // ===== Behavior: CBC =====
    private void rotateCBC(CannonMountBlockEntity mount) {
        if (!isRunning) return;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) return;

        double currentYaw = wrap360(contraption.yaw);
        double desiredYaw = wrap360(clampYawToLimits(targetAngle));


        double yawDiff = shortestDelta(currentYaw, desiredYaw);
        if (Math.abs(yawDiff) <= TOLERANCE_DEG) {
            mount.setYaw((float) desiredYaw);

            lastCbcYawWritten = wrap360(desiredYaw);
            hasLastCbcYawWritten = true;

            mount.notifyUpdate();

            // isRunning = false;
            return;
        }

        double speedFactor = Math.abs(getSpeed()) / 32.0;
        if (speedFactor <= 0) return;

        double nextYaw;
        if (Math.abs(yawDiff) > speedFactor) {
            nextYaw = wrap360(currentYaw + Math.signum(yawDiff) * speedFactor);
        } else {
            nextYaw = desiredYaw;
        }

        mount.setYaw((float) nextYaw);

        lastCbcYawWritten = wrap360(nextYaw);
        hasLastCbcYawWritten = true;

        mount.notifyUpdate();
    }

    private void rotatePhysBearing(PhysBearingBlockEntity mount) {
        ScrollOptionBehaviour<ContraptionController.LockedMode> mode = mount.getMovementMode();
        if (mode != null && mode.getValue() != ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal()) {
            mode.setValue(ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal());
        }

        if (!isRunning) return;

        double rpm = Math.abs(getSpeed());
        if (rpm <= 0.0) return;

        Double actualRad = mount.getActualAngle();
        if (actualRad == null) return;

        // i convert physbearing degrees -> controller degrees (your mapping)
        double currentPhysDeg = wrap360(Math.toDegrees(actualRad));
        double currentCtlDeg  = wrap360(360.0 - currentPhysDeg);

        // i keep desired in controller-space and clamp to my limits
        double desiredCtlDeg = clampYawToLimits(targetAngle);

        // i treat min==max as FULL RANGE (same behavior as your clampYawToLimits)
        double min = wrap360(minAngleDeg);
        double max = wrap360(maxAngleDeg);
        double allowedLen = wrap360(max - min); // (max-min+360)%360

        boolean fullRange = allowedLen < 1e-6;

        // deadband in controller-space
        double diffCtl = shortestDelta(currentCtlDeg, desiredCtlDeg);
        double distCtl = Math.abs(diffCtl);
        if (distCtl <= Math.max(TOLERANCE_DEG, DEADBAND_DEG)) {
            double desiredPhysDeg = wrap360(360.0 - desiredCtlDeg);
            mount.setAngle((float) desiredPhysDeg);
            mount.notifyUpdate();
            return;
        }

        // max degrees i can move this tick
        double stepDeg = getStep(SNAP_DISTANCE, distCtl);

        double nextCtlDeg;

        if (fullRange) {
            double move = Math.min(stepDeg, distCtl);
            nextCtlDeg = wrap360(currentCtlDeg + Math.signum(diffCtl) * move);
        } else {
            double currentParam = angleToAllowedParamOrNearest(currentCtlDeg, min, allowedLen);
            double desiredParam = angleToAllowedParamOrNearest(desiredCtlDeg, min, allowedLen);

            double deltaParam = desiredParam - currentParam;
            double move = Math.signum(deltaParam) * Math.min(Math.abs(deltaParam), stepDeg);

            double nextParam = currentParam + move;
            nextCtlDeg = allowedParamToAngle(nextParam, min, allowedLen);
        }

        // optional: very fast snap
        if (rpm >= 256.0) {
            nextCtlDeg = desiredCtlDeg;
        }

        // i convert back controller degrees -> physbearing degrees
        double nextPhysDeg = wrap360(360.0 - nextCtlDeg);
        mount.setAngle((float) nextPhysDeg);
        mount.notifyUpdate();
    }
    private void maybeUpdateYawZeroFromCannonInitialOrientation() {
        if (level == null) return;
        BlockPos cannonPos =WeaponNetworkData.get((ServerLevel) level).getMountForController(level.dimension(),worldPosition);
        if(cannonPos == null)return;
        CannonMountBlockEntity cannonMount;
        if(level.getBlockEntity(cannonPos) instanceof CannonMountBlockEntity mount) cannonMount =mount;
        else return;

        PitchOrientedContraptionEntity ce = cannonMount.getContraption();
        if (ce == null) return;

        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannonContraption))
            return;

        Direction initial = cannonContraption.initialOrientation();
        if (initial == null || !initial.getAxis().isHorizontal())
            return;

        double newOffset = controllerYawForCardinalDirection(initial);

        if (!hasYawZeroOffset) {
            yawZeroOffsetDeg = wrap360(newOffset);
            lastYawZeroOffsetDeg = yawZeroOffsetDeg;
            hasYawZeroOffset = true;
            setChanged();
            return;
        }

        double oldOffset = lastYawZeroOffsetDeg;
        double delta = shortestDelta(oldOffset, newOffset); // signed shortest path

        if (Math.abs(delta) < 1e-6) return;

        yawZeroOffsetDeg = wrap360(newOffset);
        lastYawZeroOffsetDeg = yawZeroOffsetDeg;

        targetAngle = wrap360(targetAngle - delta);
        minAngleDeg = wrap360(minAngleDeg - delta);
        maxAngleDeg = wrap360(maxAngleDeg - delta);

        normalizeLimits();
        targetAngle = clampYawToLimits(targetAngle);

        notifyUpdate();
        setChanged();
    }
    private double minAngleDeg = 0.0;
    private double maxAngleDeg = 360.0;

    public double getMinAngleDeg() { return minAngleDeg; }
    public double getMaxAngleDeg() { return maxAngleDeg; }

    public void setMinAngleDeg(double v) {
        minAngleDeg = wrap360(wrap180(v));
        normalizeLimits();
        targetAngle = clampYawToLimits(targetAngle);
        notifyUpdate();
        setChanged();
    }

    public void setMaxAngleDeg(double v) {
        maxAngleDeg = wrap360(wrap180(v));
        normalizeLimits();
        targetAngle = clampYawToLimits(targetAngle);
        notifyUpdate();
        setChanged();
    }
    private static double controllerYawForCardinalDirection(Direction d) {
        return switch (d) {
            case SOUTH -> 0.0;
            case WEST  -> 90.0;
            case NORTH -> 180.0;
            case EAST  -> 270.0;
            default    -> 0.0;
        };
    }

    private static double angleToAllowedParamOrNearest(double angleDeg, double minDeg, double allowedLen) {
        double a = wrap360(angleDeg);
        double min = wrap360(minDeg);

        double d = wrap360(a - min); // [0..360)

        if (d <= allowedLen) return d;

        double distToMaxAlongCircle = d - allowedLen;
        double distToMinAlongCircle = 360.0 - d;

        return (distToMaxAlongCircle <= distToMinAlongCircle) ? allowedLen : 0.0;
    }

    // i convert a param back into an angle on the allowed arc
    private static double allowedParamToAngle(double param, double minDeg, double allowedLen) {
        double min = wrap360(minDeg);
        double p = Math.max(0.0, Math.min(allowedLen, param));
        return wrap360(min + p);
    }
    private void normalizeLimits() {
        minAngleDeg = wrap360(minAngleDeg);
        maxAngleDeg = wrap360(maxAngleDeg);
    }



    private double getStep(double range, double dist) {
        double rpm = Math.abs(getSpeed());
        double r = Math.min(256.0, Math.max(0.0, rpm));
        double gamma = 1.6;
        double x = r / 256.0;
        double effectiveRpm = 1.0 + (r - 1.0) * Math.pow(x, gamma);

        double degPerTick = effectiveRpm * 0.3;
        double radPerTick = degPerTick * (Math.PI / 180.0);

        double maxStep = range * radPerTick;
        maxStep = Math.max(MIN_MOVE_PER_TICK, Math.min(MAX_MOVE_PER_TICK, maxStep));

        return Math.min(dist, maxStep);
    }

    // ===== VS2 ship-space yaw helper =====
    private double computeYawToTargetDeg(Vec3 cannonCenterWorld, Vec3 targetWorld) {
        Ship ship = getShipIfPresent();

        Vec3 cannonCenter = cannonCenterWorld;
        Vec3 target = targetWorld;

        // i'm converting both points into ship-space so ship rotation is automatically accounted for
        if (Mods.VALKYRIENSKIES.isLoaded() && ship != null) {
            cannonCenter = toShipSpace(ship, cannonCenterWorld);
            target = toShipSpace(ship, targetWorld);
        }

        double dx = target.x - cannonCenter.x;
        double dz = target.z - cannonCenter.z;

        return Math.toDegrees(Math.atan2(dz, dx)) + 90.0;
    }

    private Vec3 toShipSpace(Ship ship, Vec3 worldPos) {
        Vector3d tmp = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
        ship.getTransform().getWorldToShip().transformPosition(tmp);
        return new Vec3(tmp.x, tmp.y, tmp.z);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        // i load limits (defaults if missing)
        if (compound.contains("MinAngleDeg", Tag.TAG_DOUBLE)) {
            minAngleDeg = compound.getDouble("MinAngleDeg");
        }
        if (compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE)) {
            maxAngleDeg = compound.getDouble("MaxAngleDeg");
        }

        // i load target + clamp it to the limits
        targetAngle = clampYawToLimits(wrap360(compound.getDouble("TargetAngle")));

        isRunning = compound.getBoolean("IsRunning");

        if (compound.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(compound.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        // i save limits
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);

        // i save target (clamped)
        compound.putDouble("TargetAngle", wrap360(clampYawToLimits(targetAngle)));

        compound.putBoolean("IsRunning", isRunning);
        compound.putLong("LastKnownPos", lastKnownPos.asLong());
    }


    @Override
    protected void copySequenceContextFrom(KineticBlockEntity sourceBE) {
        // i'm keeping this empty like the original controller
    }



    // ===== Mount resolution =====
    private enum MountKind { CBC, PHYS, RADAR }

    private class Mount {
        final MountKind kind;
        final CannonMountBlockEntity cbc;
        final PhysBearingBlockEntity phys;

        private Mount(CannonMountBlockEntity cbc) {
            this.kind = MountKind.CBC;
            this.cbc = cbc;
            this.phys = null;
        }

        private Mount(PhysBearingBlockEntity phys) {
            this.kind = MountKind.PHYS;
            this.cbc = null;
            this.phys = phys;
        }
    }
    public boolean isUpsideDown() {
        if (level == null) return false;
        BlockState state = getBlockState();
        if (!state.hasProperty(DirectionalKineticBlock.FACING)) return false;
        return state.getValue(DirectionalKineticBlock.FACING) == Direction.UP;
    }
    public void markMountDirtyExternal() {
        // i expose this so the block can invalidate cache when my own state changes
        markMountDirty();
    }
    @Nullable
    public Mount resolveMount() {
        if (level == null) return null;
        if (mountDirty) refreshMountCache();
        return cachedMount;
    }

    private void markMountDirty() {
        mountDirty = true;
    }
    private void refreshMountCache() {
        if (level == null) return;

        BlockPos adjacentPos = isUpsideDown() ? worldPosition.below() : worldPosition.above();
        cachedAdjacentPos = adjacentPos;

        BlockEntity adjacent = level.getBlockEntity(adjacentPos);

        Mount newMount = null;
        MountKind newKind = null;

        if (Mods.CREATEBIGCANNONS.isLoaded() && adjacent instanceof CannonMountBlockEntity cbc) {
            newMount = new Mount(cbc);
            newKind = MountKind.CBC;
        } else if (Mods.VS_CLOCKWORK.isLoaded() && adjacent instanceof PhysBearingBlockEntity phys) {
            newMount = new Mount(phys);
            newKind = MountKind.PHYS;
        }

        // i update cached values
        cachedMount = newMount;
        cachedMountKind = newKind;
        mountDirty = false;

        // i keep your currentmount in sync so setTarget() keeps behaving the same
        currentmount = (newKind != null) ? newKind : currentmount;

        // optional: if mount goes away, stop running so it doesn't fight phantom state
        if (newMount == null) {
            isRunning = false;
            hasYawZeroOffset = false;
        }

        setChanged();
        notifyUpdate();
    }
    public void onRelevantNeighborChanged(BlockPos fromPos) {
        // i only care if the neighbor that changed is the one i mount to
        BlockPos adjacentPos = isUpsideDown() ? worldPosition.below() : worldPosition.above();
        if (!fromPos.equals(adjacentPos)) return;

        markMountDirty();
    }
    @Nullable
    private Ship getShipIfPresent() {
        if (level == null) return null;

        if (!(Mods.VALKYRIENSKIES.isLoaded()))
            return null;

        return VSGameUtilsKt.getShipManagingPos(level, worldPosition);
    }

    private static double wrap360(double deg) {
        deg %= 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    // i keep angles in the nice user space [-180, 180)
    private static double wrap180(double deg) {
        deg = wrap360(deg);
        if (deg >= 180.0) deg -= 360.0;
        return deg;
    }

    private static boolean isAngleInWrappedRange(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);

        if (min <= max) return angle >= min && angle <= max;
        return angle >= min || angle <= max;
    }


    private static double wrappedDistance(double a, double b) {
        double d = Math.abs(wrap360(a) - wrap360(b));
        return Math.min(d, 360.0 - d);
    }


    private double clampYawToLimits(double angle) {
        double min  = wrap360(minAngleDeg);
        double max  = wrap360(maxAngleDeg);
        angle = wrap360(angle);

        if (Math.abs(wrappedDistance(min, max)) < 1e-6) {
            return angle;
        }

        if (isAngleInWrappedRange(angle, min, max))
            return angle;

        double dToMin = wrappedDistance(angle, min);
        double dToMax = wrappedDistance(angle, max);
        return (dToMin <= dToMax) ? min : max;
    }

    public boolean canPossiblyAimAt(Vec3 originWorld, Vec3 targetWorld) {
        if (originWorld == null || targetWorld == null) return false;

        Vec3 d = targetWorld.subtract(originWorld);
        if (d.lengthSqr() < 1.0e-6) return true;

        double yawDeg = Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0;
        yawDeg = wrap360(yawDeg);

        double min = wrap360(minAngleDeg);
        double max = wrap360(maxAngleDeg);

        if (min == max) return true;

        return isAngleInWrappedRange(yawDeg, min, max);
    }

    private static double shortestDelta(double from, double to) {
        return ((to - from + 540.0) % 360.0) - 180.0;
    }
}
