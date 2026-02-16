package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutoPitchControllerBlockEntity extends KineticBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double CBC_TOLERANCE = 0.1;

    // PhysBearing tolerance in degrees
    private static final double PHYS_TOLERANCE_DEG = 0.1;
    private static final double DEADBAND_DEG = 0.25;
    // CBC (normal mount) pitch limits: [-90, 90] by default
    private double minAngleDeg = -90.0;
    private double maxAngleDeg =  90.0;
    @Nullable private Vec3 lastTargetPos = null;

    @Nullable private Mount cachedMount = null;
    @Nullable private MountKind cachedMountKind = null;
    private boolean mountDirty = true;
    private BlockPos cachedMountPos = BlockPos.ZERO;

    public double getMinAngleDeg() { return minAngleDeg; }
    public double getMaxAngleDeg() { return maxAngleDeg; }

    // State
    private double targetAngle;
    public boolean isRunning;

    // artillery selection (CBC)
    private boolean artillery = false;

    public RadarTrack track;

    private BlockPos lastKnownPos = BlockPos.ZERO;
    public WeaponFiringControl firingControl;
    public AutoYawControllerBlockEntity autoyaw;
    private static final double SNAP_DISTANCE = 37.0;
    private static final double MIN_MOVE_PER_TICK = 0.02;
    private static final double MAX_MOVE_PER_TICK = 2.0;

    private static final double MIN_DEG_PER_TICK = 0.30; // ~1 RPM feel
    private static final double MAX_DEG_PER_TICK = 18.0; // 256 RPM feel
    private static final double CURVE_GAMMA = 1.65;

    private double lastCommandedDeg = Double.NaN;

    @Nullable
    private Vec3 desiredTarget = null;   // latest commanded target (ship-space if on ship)
    private  boolean binoMode;
    @Nullable
    private Vec3 smoothedTarget = null;
    private int mountRecheckCooldown = 0;
    // cached mount
    private PhysBearingBlockEntity currentMount;

    public void getFiringControl() {
        if (firingControl != null) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        var view = getWeaponGroup();
        if (view == null) return;

        if (view.yawPos() != null && level.getBlockEntity(view.yawPos()) instanceof AutoYawControllerBlockEntity aYCBE) {
            autoyaw = aYCBE;
        }

        BlockPos mountPos = view.mountPos();
        if (mountPos == null) return;

        BlockEntity be = level.getBlockEntity(mountPos);
        if (be instanceof CannonMountBlockEntity mount) {
            firingControl = new WeaponFiringControl(this, mount, autoyaw);
            LOGGER.debug("made new Weapon Config!");
        }
    }

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide())
            return;
        if(level instanceof ServerLevel serverLevel){
            if ( WeaponNetworkData.get(serverLevel).getWeaponGroupViewFromEndpoint(serverLevel.dimension(), worldPosition) == null ) return;
        }
        if (firingControl == null){
            getFiringControl();
        }

        if (cachedMount == null)refreshMountCache();
        if (cachedMount == null) {
            isRunning = false;
            return;
        }


        if (cachedMount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            rotateCBC(cachedMount.cbc);
        } else if (cachedMount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            rotatePhysBearing(cachedMount.phys);
        }
    }

    /** Horizontal controller: mount is in front of the controller. */
    @Nullable
    public BlockPos isFacingCannonMount(Level level, BlockPos pos, BlockState state) {
        if (level == null || state == null) return null;
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return null;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        return pos.relative(facing);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if(this.firingControl != null) {
            firingControl.clearBinoTarget();
        }
        if (level == null || level.isClientSide)
            return;
        setChanged();
    }

    public void setTargetAngle(float angle) {
        this.targetAngle = angle;
        this.isRunning = true;

        // phys smoothing reset
        this.desiredTarget = null;
        this.smoothedTarget = null;
        this.lastCommandedDeg = Double.NaN;

        notifyUpdate();
        setChanged();

    }
    @Nullable
    private WeaponNetworkData.WeaponGroupView getWeaponGroup() {
        if (level == null || level.isClientSide) return null;
        if (!(level instanceof ServerLevel sl)) return null;

        WeaponNetworkData data = WeaponNetworkData.get(sl);
        return data.getWeaponGroupViewFromEndpoint(sl.dimension(), worldPosition);
    }

    public static Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public void setAndAcquireTrack(@Nullable RadarTrack tTrack, TargetingConfig config) {
        if (level == null || level.isClientSide || binoMode) return; // binoculars have priority

        if (firingControl == null) getFiringControl();


        LOGGER.debug("PITCH setAndAcquireTrack track={} firingControl={}", tTrack == null ? "null" : tTrack.getId(), firingControl != null);

        if (tTrack == null) {
            track = null;
            if (firingControl != null) firingControl.resetTarget();
            return;
        }
        if (tTrack != track) {
            track = tTrack;
        }
        if (firingControl == null) return;
        if (!(level instanceof ServerLevel sl)) return;



        var view = getWeaponGroup();
        if (view == null) {
            LOGGER.debug("PITCH {} getWeaponGroup() returned null - cannot aim/fire", worldPosition);
            return;
        }
        LOGGER.debug("ptfc");
        firingControl.setTarget(track.getPosition(), config, tTrack, view);
    }

    public double getMaxEngagementRangeBlocks() {
        if (level == null || level.isClientSide) return 0;
        if (!(level instanceof ServerLevel sl)) return 0;

        Mount m = resolveMount();
        if (m == null) return 0;

        if (m.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            PitchOrientedContraptionEntity ce = m.cbc.getContraption();
            if (ce == null) return 0;
            if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannon)) return 0;

            double r = CannonUtil.getMaxProjectileRangeBlocks(cannon, sl);
            LOGGER.debug("RANGE DBG endpoint={} cannon={} range={} blocks", worldPosition, cannon.getClass().getSimpleName(), r);
            return r;
        }
        return 0;
    }

    @Nullable
    public Vec3 getRayStart() {
        if (firingControl == null) getFiringControl();
        return firingControl != null ? firingControl.getCannonRayStart() : null;
    }

    public void setAndAcquirePos(@Nullable BlockPos binoTargetPos, TargetingConfig config, boolean reset) {
        if (level == null || level.isClientSide) return;

        if (reset || binoTargetPos == null) {
            this.binoMode = false;

            if (firingControl != null) {
                firingControl.clearBinoTarget();
            }
            return;
        }
        if (firingControl == null) getFiringControl();
        if (firingControl == null) return;
        if (!(level instanceof ServerLevel sl)) return;

        var view = getWeaponGroup();
        if (view == null) return;

        // i only enable binoMode once i know i can actually apply it
        this.binoMode = true;

        firingControl.setBinoTarget(binoTargetPos, config, view, reset);
    }




    public double getTargetAngle() {
        return targetAngle;
    }
    public void setTarget(@Nullable Vec3 targetPos) {
        if (level == null || level.isClientSide())
            return;

        if (targetPos == null) {
            isRunning = false;

            desiredTarget = null;
            smoothedTarget = null;
            lastCommandedDeg = Double.NaN;

            notifyUpdate();
            setChanged();
            return;
        }
        Mount mount = resolveMount();
        if (mount == null)
            return;

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            setTargetCBC(mount.cbc, targetPos);
            return;
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {

            Ship ship = getShipIfPresent();
            Vec3 desired = (ship != null) ? toShipSpace(ship, targetPos) : targetPos;

            desiredTarget = desired;
            if (smoothedTarget == null)
                smoothedTarget = desired;

            isRunning = true;
            lastCommandedDeg = Double.NaN;

            notifyUpdate();
            setChanged();
        }
    }

    /** CBC path: checks contraption.pitch vs targetAngle (both in CBC units). */
    public boolean atTargetPitch(boolean lag) {
        if (level == null)
            return false;

        Mount mount = resolveMount();
        if (mount == null)
            return false;

        // i increase tolerance slightly if we're not lag-compensating
        double cbcTol = CBC_TOLERANCE;
        double physTol = PHYS_TOLERANCE_DEG;
        if (!lag) {
            cbcTol += 0.15;
            physTol += 0.15;
        }

        if (mount.kind == MountKind.CBC && Mods.CREATEBIGCANNONS.isLoaded()) {
            PitchOrientedContraptionEntity contraption = mount.cbc.getContraption();
            if (contraption == null)
                return false;

            if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption))
                return false;

            double currentPitch = contraption.pitch;
            int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
            currentPitch = currentPitch * -invert;

            return Math.abs(currentPitch - targetAngle) < cbcTol;
        }

        if (mount.kind == MountKind.PHYS && Mods.VS_CLOCKWORK.isLoaded()) {
            Double actualRad = mount.phys.getActualAngle();
            if (actualRad == null)
                return false;

            double currentDeg = wrap360(Math.toDegrees(actualRad));
            double desiredDeg = wrap360(targetAngle);

            return Math.abs(shortestDelta(currentDeg, desiredDeg)) < Math.max(physTol, DEADBAND_DEG);
        }

        return false;
    }

    public void setTrack(RadarTrack track) {
        this.track = track;
    }
    private double clampToLimitsCBC(double deg) {
        return Math.max(minAngleDeg, Math.min(maxAngleDeg, deg));
    }
    private static boolean isAngleInWrappedRange(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);

        if (min <= max) {
            // normal case (ex: 30..120)
            return angle >= min && angle <= max;
        }

        // wrap case (ex: 270..90) == [-90..90]
        return angle >= min || angle <= max;
    }

    private static double clampAngleToWrappedRange(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);

        if (isAngleInWrappedRange(angle, min, max))
            return angle;

        // i clamp to the nearest edge of the allowed range
        double dToMin = wrappedDistance(angle, min);
        double dToMax = wrappedDistance(angle, max);
        return (dToMin <= dToMax) ? min : max;
    }

    // smallest absolute distance between two angles on a circle
    private static double wrappedDistance(double a, double b) {
        double d = Math.abs(wrap360(a) - wrap360(b));
        return Math.min(d, 360.0 - d);
    }
    private double clampToLimitsPhys(double deg) {
            return clampAngleToWrappedRange(deg, minAngleDeg, maxAngleDeg);
    }

    // i clamp based on what i'm actually attached to
    private double clampToLimits(double deg) {
        Mount m = resolveMount();
        if (m != null && m.kind == MountKind.PHYS) {
            return clampToLimitsPhys(deg);
        }
        return clampToLimitsCBC(deg);
    }


    public void setSafeZones(List<AABB> safeZones) {
        if (firingControl == null)
            return;
        firingControl.setSafeZones(safeZones);
    }

    // CBC behavior (aligned to yaw's "snap + stop" flow)

    private void rotateCBC(CannonMountBlockEntity mount) {
        if (!isRunning) {
            LOGGER.debug("PITCH.rotateCBC aborted: isRunning=false");
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return;

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption))
            return;

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double diff = targetAngle - currentPitch;


        double nearDeadbandDeg = CBC_TOLERANCE; // default
        if (firingControl != null) {
            Vec3 muzzle = firingControl.getCannonRayStart();
            Vec3 target = lastTargetPos;
            if (target != null) {
                double dist = muzzle.distanceTo(target);
                if (dist <= 10.0) {
                    nearDeadbandDeg = 6.0; // tune this "dramatic" threshold however you want
                }
            }
        }

        LOGGER.debug(
                "PITCH.rotateCBC current={} target={} diff={} speed={} deadband={}",
                currentPitch,
                targetAngle,
                diff,
                getSpeed(),
                nearDeadbandDeg
        );

        // if the change isn't big enough (especially when close), i hold position
        if (Math.abs(diff) <= nearDeadbandDeg) {
            double clamped = clampToLimitsCBC(targetAngle);
            mount.setPitch((float) clamped);
            mount.notifyUpdate();

            // i stop running once we're within the chosen deadband
            isRunning = false;
            return;
        }

        double speedFactor = Math.abs(getSpeed()) / 32.0;
        if (speedFactor <= 0.0)
            return;

        double next;
        if (Math.abs(diff) > speedFactor) {
            next = currentPitch + Math.signum(diff) * speedFactor;
        } else {
            next = targetAngle;
        }
        LOGGER.debug("ping" +mount);
        mount.setPitch((float) next);
        mount.notifyUpdate();
    }
    private void setTargetCBC(CannonMountBlockEntity mount, Vec3 targetPos) {
        if (level == null || !(level instanceof ServerLevel serverLevel))
            return;


        if (PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, serverLevel);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()){
                LOGGER.warn("ping-3"+angles);
                return;
            }

            // pitch
            this.targetAngle = clampToLimitsCBC(angles.get(0).get(0));

//            // yaw (if you have firingControl)
//            if (firingControl != null) {
//                firingControl.yawController.setTargetAngle(angles.get(0).get(1).floatValue());
//            }
            LOGGER.warn("ping");
            isRunning = true;
            notifyUpdate();
            setChanged();
            return;
        }

        // Use mount directly to determine origin
        Vec3 origin = getRayStart();
        List<Double> angles = CannonTargeting.calculatePitch(mount, origin, targetPos, serverLevel);
        LOGGER.debug(
                "PITCH.solve origin={} target={} mountPos={}",
                origin,
                targetPos,
                mount.getBlockPos()
        );
        lastTargetPos = targetPos;
        if (angles == null || angles.isEmpty()) {
            LOGGER.debug("PITCH.solve FAILED: no pitch roots");
            isRunning = false;
            return;
        }

        List<Double> usableAngles = new ArrayList<>();
        for (double angle : angles) {
            if (mount.getContraption() == null)
                break;
            if (angle < mount.getContraption().maximumElevation() && angle > -mount.getContraption().maximumDepression()) {
                usableAngles.add(angle);
            }
        }

        if (artillery && usableAngles.size() == 2) {
            targetAngle = clampToLimitsCBC(angles.get(1));
        } else if (!usableAngles.isEmpty()) {
            targetAngle = clampToLimitsCBC(usableAngles.get(0));
        }

        LOGGER.debug("PITCH.solve targetAngle={}", targetAngle);

        isRunning = true;
        notifyUpdate();
        setChanged();
    }


    // PhysBearing behavior (aligned to yaw: follow-angle + unwrap)
    private void rotatePhysBearing(PhysBearingBlockEntity mount) {
        // Ensure follow-angle mode
        ScrollOptionBehaviour<ContraptionController.LockedMode> mode = mount.getMovementMode();
        if (mode != null && mode.getValue() != ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal()) {
            mode.setValue(ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal());
        }

        double rpmAbs = Math.abs(getSpeed());
        if (rpmAbs <= 0.0)
            return;

        if (!isRunning)
            return;

        // If we have a desired target, keep smoothing towards it and update targetAngle (degrees)
        updateSmoothedTargetAndAngle(rpmAbs, mount);

        Double actualRad = mount.getActualAngle();
        if (actualRad == null)
            return;

        double currentDeg = wrap360(Math.toDegrees(actualRad));
        double desiredDeg = wrap360(targetAngle);

        // "snap at max rpm"
        if (rpmAbs >= 256.0) {
            lastCommandedDeg = desiredDeg;
            mount.setAngle((float) desiredDeg);
            mount.notifyUpdate();
            return;
        }

        // latch the last-commanded value
        if (Double.isNaN(lastCommandedDeg)) {
            lastCommandedDeg = currentDeg;
        }

        // unwrap target near last command to avoid long spins
        double desiredContinuous = unwrapNear(lastCommandedDeg, desiredDeg);

        // deadband: stop running when close enough
        if (Math.abs(shortestDelta(currentDeg, desiredDeg)) <= Math.max(PHYS_TOLERANCE_DEG, DEADBAND_DEG)) {
            mount.setAngle((float) desiredDeg);
            mount.notifyUpdate();
            isRunning = false;
            lastCommandedDeg = desiredContinuous;
            return;
        }

        lastCommandedDeg = desiredContinuous;
        mount.setAngle((float) wrap360(desiredContinuous));
        mount.notifyUpdate();
    }

    private void updateSmoothedTargetAndAngle(double rpmAbs, PhysBearingBlockEntity mount) {
        if (desiredTarget == null) return;

        Direction facing = getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING);

        Vec3 pivot = mount.getBlockPos().getCenter();

        if (smoothedTarget == null) {
            smoothedTarget = desiredTarget;
        } else {
            Vec3 delta = desiredTarget.subtract(smoothedTarget);
            double dist = delta.length();

            if (dist > SNAP_DISTANCE) {
                smoothedTarget = desiredTarget;
            } else if (dist > 1e-6) {
                double radius = diskRadius(pivot, smoothedTarget, facing);
                double step = stepTowardTarget(radius, dist, rpmAbs);
                smoothedTarget = smoothedTarget.add(delta.scale(step / dist));
            }
        }
        double newAngle = rollAroundFacingDeg(pivot, smoothedTarget, facing);
        if (Math.abs(shortestDelta(targetAngle, newAngle)) < DEADBAND_DEG) return;
        targetAngle = clampToLimitsPhys(approachWrapped(targetAngle, newAngle));
    }
    private enum MountKind { CBC, PHYS }

    private static class Mount {
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
    private void refreshMountCache() {
        if (level == null) return;

        BlockPos mountPos = getMountPos();
        cachedMountPos = (mountPos != null) ? mountPos : BlockPos.ZERO;

        Mount newMount = null;
        MountKind newKind = null;

        if (mountPos != null) {
            BlockEntity be = level.getBlockEntity(mountPos);

            if (Mods.CREATEBIGCANNONS.isLoaded() && be instanceof CannonMountBlockEntity cbc) {
                newMount = new Mount(cbc);
                newKind = MountKind.CBC;
            } else if (Mods.VS_CLOCKWORK.isLoaded() && be instanceof PhysBearingBlockEntity phys) {
                newMount = new Mount(phys);
                newKind = MountKind.PHYS;
            }
        }

        cachedMount = newMount;
        cachedMountKind = newKind;
        mountDirty = false;

        // optional: if mount disappears, stop motion so it doesn't fight ghost state
        if (newMount == null) {
            isRunning = false;
            desiredTarget = null;
            smoothedTarget = null;
            lastCommandedDeg = Double.NaN;
        }

        setChanged();
        notifyUpdate();
    }

    public void onRelevantNeighborChanged(BlockPos fromPos) {
        // i only care if the block that changed is the one i'm attached to
        BlockPos mountPos = getMountPos();
        if (mountPos == null) return;

        if (fromPos.equals(mountPos)) {
            mountDirty = true;
        }
    }

    public void markMountDirtyExternal() {
        // i expose this so my block can invalidate when my own facing changes
        mountDirty = true;
    }

    @Nullable
    private Mount resolveMount() {
        if (level == null) return null;
        if (mountDirty) refreshMountCache();
        return cachedMount;
    }

    @Nullable
    private BlockPos getMountPos() {
        if (level == null) return null;
        return isFacingCannonMount(level, worldPosition, getBlockState()); // your existing helper
    }
    public void setMinAngleDeg(double v) {
        Mount m = resolveMount();

        if (m != null && m.kind == MountKind.PHYS) {
            minAngleDeg = wrap360(v);
        } else {
            minAngleDeg = v;
            if (minAngleDeg > maxAngleDeg) {
                double tmp = minAngleDeg;
                minAngleDeg = maxAngleDeg;
                maxAngleDeg = tmp;
            }
        }

        targetAngle = clampToLimits(targetAngle);
        notifyUpdate();
        setChanged();
    }

    public void setMaxAngleDeg(double v) {
        Mount m = resolveMount();

        if (m != null && m.kind == MountKind.PHYS) {
            maxAngleDeg = wrap360(v);
        } else {
            maxAngleDeg = v;
            if (minAngleDeg > maxAngleDeg) {
                double tmp = minAngleDeg;
                minAngleDeg = maxAngleDeg;
                maxAngleDeg = tmp;
            }
        }

        targetAngle = clampToLimits(targetAngle);
        notifyUpdate();
        setChanged();
    }

    // NBT
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        targetAngle = compound.getDouble("TargetAngle");
        isRunning = compound.getBoolean("IsRunning");
        if (compound.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(compound.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }
        // i load limits (defaults if missing)
        minAngleDeg = compound.contains("MinAngleDeg") ? compound.getDouble("MinAngleDeg") : -90.0;
        maxAngleDeg =compound.contains("MaxAngleDeg") ?compound.getDouble("MaxAngleDeg") :  90.0;
        if (minAngleDeg > maxAngleDeg) {
            double tmp = minAngleDeg;
            minAngleDeg = maxAngleDeg;
            maxAngleDeg = tmp;
        }
        minAngleDeg = compound.contains("MinAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MinAngleDeg") : -90.0;
        maxAngleDeg = compound.contains("MaxAngleDeg", Tag.TAG_DOUBLE) ? compound.getDouble("MaxAngleDeg") :  90.0;
        if (minAngleDeg > maxAngleDeg) {
            double tmp = minAngleDeg;
            minAngleDeg = maxAngleDeg;
            maxAngleDeg = tmp;
        }


        // smoothing state not persisted by design
        desiredTarget = null;
        smoothedTarget = null;
        lastCommandedDeg = Double.NaN;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putLong("LastKnownPos", lastKnownPos.asLong());
        compound.putDouble("TargetAngle", targetAngle);
        compound.putBoolean("IsRunning", isRunning);
        // i save limits
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
    }

    @Override
    protected void copySequenceContextFrom(KineticBlockEntity sourceBE) {

    }


    private static Vec3 forwardHoriz(Direction facing) {
        Vec3 f = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        if (f.lengthSqr() < 1e-8)
            return new Vec3(0, 0, 1);
        return f.normalize();
    }

    private static Vec3 rightHoriz(Direction facing) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 fwd = forwardHoriz(facing);
        Vec3 r = up.cross(fwd);
        double ls = r.lengthSqr();
        return ls < 1e-8 ? new Vec3(1, 0, 0) : r.scale(1.0 / Math.sqrt(ls));
    }

    /** Returns degrees wrapped to [0,360). */
    private static double rollAroundFacingDeg(Vec3 pivot, Vec3 target, Direction facing) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 fwd = forwardHoriz(facing);
        Vec3 right = rightHoriz(facing);

        Vec3 v = target.subtract(pivot);

        // remove along-axis component so it only reacts to disk plane
        Vec3 vDisk = v.subtract(fwd.scale(v.dot(fwd)));

        double r = vDisk.dot(right);
        double u = vDisk.dot(up);

        if (Math.abs(r) < 1e-10 && Math.abs(u) < 1e-10)
            return 0.0;

        return wrap360(Math.toDegrees(Math.atan2(u, r)));
    }

    private static double diskRadius(Vec3 pivot, Vec3 target, Direction facing) {
        Vec3 fwd = forwardHoriz(facing);
        Vec3 v = target.subtract(pivot);
        Vec3 vDisk = v.subtract(fwd.scale(v.dot(fwd)));
        return Math.sqrt(vDisk.lengthSqr());
    }

    private static double stepTowardTarget(double radius, double distToSmoothed, double rpmAbs) {
        double degPerTick = degPerTickFromRpm(rpmAbs);
        double radPerTick = degPerTick * (Math.PI / 180.0);

        double maxStep = radius * radPerTick;
        maxStep = Math.max(MIN_MOVE_PER_TICK, Math.min(MAX_MOVE_PER_TICK, maxStep));

        return Math.min(distToSmoothed, maxStep);
    }

    private static double degPerTickFromRpm(double rpmAbs) {
        double r = Math.max(0.0, Math.min(256.0, rpmAbs));

        double t;
        if (r <= 1.0) {
            t = 0.0;
        } else {
            t = (r - 1.0) / 255.0;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        double shaped = Math.pow(t, CURVE_GAMMA);
        return MIN_DEG_PER_TICK + (MAX_DEG_PER_TICK - MIN_DEG_PER_TICK) * shaped;
    }

    private static double approachWrapped(double currentWrapped, double newWrapped) {
        return wrap360(currentWrapped + shortestDelta(currentWrapped, newWrapped));
    }

    private static double wrap360(double a) {
        a %= 360.0;
        if (a < 0) a += 360.0;
        return a;
    }
    private static double wrap180(double deg) {
        deg = wrap360(deg);
        if (deg >= 180.0) deg -= 360.0;
        return deg;
    }
    private static double shortestDelta(double from, double to) {
        return ((to - from + 540.0) % 360.0) - 180.0;
    }

    private static double unwrapNear(double lastContinuous, double newWrapped) {
        double lastWrapped = wrap360(lastContinuous);
        return lastContinuous + shortestDelta(lastWrapped, newWrapped);
    }


    // VS helpers (PhysBearing target space)
    @Nullable
    private Ship getShipIfPresent() {
        if (level == null) return null;

        if (!Mods.VALKYRIENSKIES.isLoaded())
            return null;

        return VSGameUtilsKt.getShipManagingPos(level, worldPosition);
    }

    private static Vec3 toShipSpace(Ship ship, Vec3 worldPos) {
        Matrix4dc worldToShip = ship.getTransform().getWorldToShip();
        Vector3d v = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
        worldToShip.transformPosition(v);
        return new Vec3(v.x, v.y, v.z);
    }

    public boolean canEngageTrack(@Nullable RadarTrack track, boolean requireLos) {
        if (track == null) return false;
        if (!(level instanceof ServerLevel sl)) return false;

        getFiringControl();
        if (firingControl == null) return false;

        Mount mount = resolveMount();
        if (mount == null) return false;

        if (mount.kind == MountKind.CBC) {
            if (mount.cbc == null) return false;
            if (mount.cbc.getContraption() == null) return false;
            if (!(mount.cbc.getContraption().getContraption() instanceof AbstractMountedCannonContraption)) return false;
        }

        Vec3 p = track.position();
        if (p == null) return false;

        double max = getMaxEngagementRangeBlocks();
        if (max > 0.0) {
            Vec3 start = firingControl.getCannonRayStart();
            if (Mods.VALKYRIENSKIES.isLoaded() && PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
                start = PhysicsHandler.getWorldVec(level, start);
            }
            if (start.distanceToSqr(p) > (max * max)) return false;
        }

        if (mount.kind == MountKind.CBC && mount.cbc != null) {
            if (Mods.VALKYRIENSKIES.isLoaded() && PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
                List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount.cbc, p, sl);
                if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) return false;
            } else {
                Vec3 origin = getRayStart();
                List<Double> pitches = CannonTargeting.calculatePitch(mount.cbc, origin, p, sl);
                if (pitches == null || pitches.isEmpty()) return false;
            }
        }

        return firingControl.hasLineOfSightTo(track, requireLos);
    }
}
