package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkData;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;

import javax.annotation.Nullable;

public class AutoYawControllerBlockEntity extends KineticBlockEntity {

    private static final double TOLERANCE_DEG = 0.15;
    private static final double DEADBAND_DEG = 0.5;

    private double targetAngle = 0.0;
    private boolean isRunning = false;

    private double lastCbcYawWritten = 0.0;
    private boolean hasLastCbcYawWritten = false;

    private double minAngleDeg = 0.0;
    private double maxAngleDeg = 360.0;

    private BlockPos lastKnownPos = BlockPos.ZERO;

    @Nullable
    private Mount cachedMount = null;

    private boolean mountDirty = true;

    private final CannonMountYaw cannonHandler;
    private final PhysBearingYaw physHandler;

    public AutoYawControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cannonHandler = new CannonMountYaw(this);
        this.physHandler = new PhysBearingYaw(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide()) {
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

        if (level.getGameTime() % 40 == 0 && level instanceof ServerLevel serverLevel) {
            if (!lastKnownPos.equals(worldPosition)) {
                ResourceKey<Level> dim = serverLevel.dimension();
                WeaponNetworkData data = WeaponNetworkData.get(serverLevel);

                boolean updated = data.updateWeaponEndpointPosition(dim, lastKnownPos, worldPosition);
                if (updated) {
                    lastKnownPos = worldPosition;
                    setChanged();
                }
            }
        }
    }

    public void setTargetAngle(float targetAngle) {
        this.targetAngle = targetAngle;
        this.isRunning = true;
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
            isRunning = false;
            notifyUpdate();
            setChanged();
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

    public boolean atTargetYaw(boolean lag) {
        if (level == null) {
            return false;
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

        if (newMount == null) {
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

        double yawDeg = Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0;
        yawDeg = wrap360(yawDeg);

        return true;
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

        if (compound.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(compound.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }

        hasLastCbcYawWritten = false;
        physHandler.read(compound);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putDouble("MinAngleDeg", minAngleDeg);
        compound.putDouble("MaxAngleDeg", maxAngleDeg);
        compound.putDouble("TargetAngle", wrap360(targetAngle));
        compound.putBoolean("IsRunning", isRunning);
        compound.putLong("LastKnownPos", lastKnownPos.asLong());

        physHandler.write(compound);
    }

    @Override
    protected void copySequenceContextFrom(KineticBlockEntity sourceBE) {
        // i'm keeping this empty like before
    }

    public void setInternalTargetAngle(double targetAngle) {
        this.targetAngle = targetAngle;
    }

    void setRunning(boolean running) {
        this.isRunning = running;
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

    void setInternalMinAngleDeg(double v) {
        this.minAngleDeg = v;
    }

    void setInternalMaxAngleDeg(double v) {
        this.maxAngleDeg = v;
    }

    static double getToleranceDeg() {
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
