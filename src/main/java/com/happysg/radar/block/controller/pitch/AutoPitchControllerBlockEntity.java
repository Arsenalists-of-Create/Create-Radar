package com.happysg.radar.block.controller.pitch;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.happysg.radar.compat.Mods;
import net.minecraft.core.Direction;
import javax.annotation.Nullable;
import java.util.List;

public class AutoPitchControllerBlockEntity extends KineticBlockEntity {
    private double internalTargetAngle;
    public boolean isRunning;
    private double minAngleDeg = -90;
    private double maxAngleDeg = 90;
    private Vec3 lastTargetPos = Vec3.ZERO;
    public FiringControl firingControl = new FiringControl(this);
    public AutoYawControllerBlockEntity autoyaw;
    
    private final CannonMountPitch cannonMountHelper = new CannonMountPitch(this);
    private final PhysBearingPitch physBearingHelper = new PhysBearingPitch(this);
    
    public com.happysg.radar.block.behavior.networks.WeaponFiringControl wfc;
    private Vec3 lastMuzzlePos = null;
    private List<com.happysg.radar.block.behavior.networks.config.SafeZone> safeZones = new java.util.ArrayList<>();

    public AutoPitchControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setInternalTargetAngle(double angle) { this.internalTargetAngle = angle; }
    public double getTargetAngle() { return internalTargetAngle; }
    public void setRunning(boolean running) { this.isRunning = running; }
    public boolean isRunningController() { return isRunning; }
    public void setTargetAngle(float angle) { this.internalTargetAngle = angle; }

    public double computePitchToTargetDeg(Vec3 origin, Vec3 target) {
        Vec3 diff = target.subtract(origin);
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return Math.toDegrees(Math.atan2(diff.y, horizontalDist));
    }

    public static double wrap360(double angle) { return (angle % 360 + 360) % 360; }
    public static double getToleranceDeg() { return 0.5; }
    public static double getDeadbandDeg() { return 0.1; }
    public static double getCbcTolerance() { return (double) com.happysg.radar.config.RadarConfig.server().autoFireTolerance.get(); }
    public static double getPhysToleranceDeg() { return 0.5; }
    public static double shortestDelta(double current, double target) {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }
    public static double unwrapNear(double last, double current) { return current; }

    public double getMinAngleDeg() { return minAngleDeg; }
    public double getMaxAngleDeg() { return maxAngleDeg; }
    public void setMinAngleDeg(double min) { this.minAngleDeg = min; }
    public void setMaxAngleDeg(double max) { this.maxAngleDeg = max; }
    public void recordCbcPitchWritten(double pitch) {}
    
    public double getMaxEngagementRangeBlocks() { return 256; }
    public Vec3 getRayStart() { return Vec3.atCenterOf(getBlockPos()); }
    public Vec3 getLastTargetPos() { return lastTargetPos; }
    public void setLastTargetPos(Vec3 pos) { this.lastTargetPos = pos; }
    public boolean isArtillery() { return false; }
    public FiringControl getFiringControl() { return firingControl; }
    public boolean canEngageTrack(Object track, boolean requireLos) {
        if (!(track instanceof com.happysg.radar.block.radar.track.RadarTrack rt)) return false;
        if (wfc == null) return true;
        return wfc.hasLineOfSightTo(rt, requireLos);
    }
    public void setAndAcquireTrack(@Nullable Object track, Object cfg) {
        if (track instanceof com.happysg.radar.block.radar.track.RadarTrack rt) {
             setRunning(true);
             if (wfc != null) {
                 wfc.setTarget(rt.position(), (com.happysg.radar.block.behavior.networks.config.TargetingConfig) cfg, rt, wfc.view);
             }
        } else {
             setRunning(false);
             if (wfc != null) wfc.setTarget(null, null, null, null);
        }
    }

    public void setAndAcquirePos(BlockPos pos, @Nullable java.util.UUID subLevelId, Object cfg, boolean reset) {
        setLastTargetPos(pos.getCenter());
        setRunning(true);
        if (wfc != null) {
            wfc.setBinoTarget(pos, subLevelId, (com.happysg.radar.block.behavior.networks.config.TargetingConfig) cfg, wfc.view, reset);
        }
    }

    public void setSafeZones(List<com.happysg.radar.block.behavior.networks.config.SafeZone> zones) {
        this.safeZones = zones;
        if (wfc != null) wfc.setSafeZones(zones);
    }
    public void setIgnoreList(java.util.Set<String> ignoreList) {
        if (wfc != null) wfc.setIgnoreList(ignoreList);
    }
    public void markMountDirtyExternal() {}
    public void onRelevantNeighborChanged(BlockPos pos) {}

    public boolean atTargetPitch(boolean lag) {
        var mount = getAttachedMount();
        if (mount instanceof rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity cm) {
            return cannonMountHelper.atTargetPitch(cm, lag);
        }
        if (Mods.VALKYRIENSKIES.isLoaded() && mount instanceof org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity pb) {
            return physBearingHelper.atTargetPitch(pb, lag);
        }
        return true;
    }

    public boolean atTargetYaw(double targetYaw, boolean lag) {
        var mount = getAttachedMount();
        if (mount instanceof rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity cm) {
            return cannonMountHelper.atTargetYaw(cm, targetYaw, lag);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        

        var mount = getAttachedMount();
        if (mount instanceof rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity cm) {
            com.happysg.radar.block.behavior.networks.WeaponNetworkData weaponData = com.happysg.radar.block.behavior.networks.WeaponNetworkData.get((ServerLevel) level);
            var group = weaponData.getOrCreateGroup(level.dimension(), cm.getBlockPos());
            weaponData.tryMergeIntoGroup(group, autoyaw != null ? autoyaw.getBlockPos() : null, getBlockPos(), null);
            
            if (wfc == null || wfc.cannonMount != cm) {
                wfc = new com.happysg.radar.block.behavior.networks.WeaponFiringControl(this, cm, autoyaw);
                wfc.setSafeZones(safeZones);
            }
            wfc.refreshControllers();
            wfc.tick();
            
            cannonMountHelper.tick(cm);
            lastMuzzlePos = wfc.getCannonRayStart(); 
        } else {
            if (level.getGameTime() % 60 == 0) {
                 com.happysg.radar.CreateRadar.getLogger().warn("AUTO CONTROLLER {}: No mount found at orientation {}", getBlockPos(), getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
            }
        }
    }
    
    @Override
    protected void write(net.minecraft.nbt.CompoundTag compound, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(compound, provider, clientPacket);
        compound.putDouble("TargetAngle", internalTargetAngle);
        compound.putBoolean("Running", isRunning);
        compound.putDouble("MinAngle", minAngleDeg);
        compound.putDouble("MaxAngle", maxAngleDeg);
        if (lastTargetPos != null) {
            compound.putDouble("TargetX", lastTargetPos.x);
            compound.putDouble("TargetY", lastTargetPos.y);
            compound.putDouble("TargetZ", lastTargetPos.z);
        }
        if (!safeZones.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (com.happysg.radar.block.behavior.networks.config.SafeZone sz : safeZones) {
                list.add(sz.toTag());
            }
            compound.put("SafeZones", list);
        }
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag compound, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(compound, provider, clientPacket);
        internalTargetAngle = compound.getDouble("TargetAngle");
        isRunning = compound.getBoolean("Running");
        minAngleDeg = compound.getDouble("MinAngle");
        maxAngleDeg = compound.getDouble("MaxAngle");
        if (compound.contains("TargetX")) {
            lastTargetPos = new Vec3(compound.getDouble("TargetX"), compound.getDouble("TargetY"), compound.getDouble("TargetZ"));
        }
        if (compound.contains("SafeZones", net.minecraft.nbt.Tag.TAG_LIST)) {
            safeZones.clear();
            net.minecraft.nbt.ListTag list = compound.getList("SafeZones", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                safeZones.add(com.happysg.radar.block.behavior.networks.config.SafeZone.fromTag(list.getCompound(i)));
            }
            if (wfc != null) wfc.setSafeZones(safeZones);
        }
    }

    private net.minecraft.world.level.block.entity.BlockEntity getAttachedMount() {
        Direction facing = getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING);
        BlockPos mountPos = getBlockPos().relative(facing);
        return level.getBlockEntity(mountPos);
    }

    public static class FiringControl {
        private final AutoPitchControllerBlockEntity parent;
        public FiringControl(AutoPitchControllerBlockEntity parent) { this.parent = parent; }
        
        public Vec3 getCannonRayStart() { 
            return parent.lastMuzzlePos != null ? parent.lastMuzzlePos : parent.getRayStart(); 
        }
        public boolean hasLineOfSightTo(Object track, boolean requireLos) { return true; }
        public void resetTarget() { parent.setRunning(false); }
    }
}
