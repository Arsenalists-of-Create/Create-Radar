package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import org.slf4j.Logger;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class AutoPitchControllerBlockEntity extends KineticBlockEntity {
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

    private final CannonMountPitch cannonHandler;
    private final PhysBearingPitch physHandler;

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountPitch(this);
        this.physHandler = new PhysBearingPitch(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        if (firingControl == null) {
            getFiringControl();
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

        physHandler.reset();

        notifyUpdate();
        setChanged();
    }

    public double getTargetAngle() {
        return targetAngle;
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
        lastTargetPos = null;
        physHandler.reset();

        notifyUpdate();
        setChanged();
    }

    public boolean atTargetPitch(boolean lag) {
        if (level == null) {
            return false;
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

            if (Mods.CREATEBIGCANNONS.isLoaded() && be instanceof CannonMountBlockEntity cbc) {
                newMount = Mount.cbc(cbc);
            } else if (Mods.VS_CLOCKWORK.isLoaded() && be instanceof PhysBearingBlockEntity phys) {
                newMount = Mount.phys(phys);
            }
        }

        cachedMount = newMount;
        mountDirty = false;

        if (newMount == null) {
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
        double rpm = Math.abs(getSpeed());
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
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries,clientPacket);

        compound.putDouble("TargetAngle", targetAngle);
        compound.putBoolean("IsRunning", isRunning);
        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.put("Targeting", targetingTag.copy());

        physHandler.write(compound);
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
    }

    void setRunning(boolean running) {
        this.isRunning = running;
    }

    boolean isRunningController() {
        return isRunning;
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
