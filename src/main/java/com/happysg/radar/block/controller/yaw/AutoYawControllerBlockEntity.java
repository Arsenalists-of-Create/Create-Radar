package com.happysg.radar.block.controller.yaw;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

public class AutoYawControllerBlockEntity extends KineticBlockEntity {
    private double internalTargetAngle;
    private boolean running;
    private double lastCbcYawWritten;
    private boolean hasLastCbcYawWritten;
    private double minAngleDeg = 0;
    private double maxAngleDeg = 360;

    public AutoYawControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setInternalTargetAngle(double angle) { this.internalTargetAngle = angle; }
    public void setTargetAngle(float angle) { this.internalTargetAngle = angle; }
    public double getTargetAngle() { return internalTargetAngle; }
    public void setRunning(boolean running) { this.running = running; }
    public boolean isRunningController() { return running; }
    public boolean isUpsideDown() { return false; } 
    
    public double computeYawToTargetDeg(Vec3 origin, Vec3 target) {
        Vec3 diff = target.subtract(origin);
        return Math.toDegrees(Math.atan2(diff.x, diff.z));
    }

    public static double wrap360(double angle) { return (angle % 360 + 360) % 360; }
    public static double getToleranceDeg() { return 0.5; }
    public static double getDeadbandDeg() { return 0.1; }
    public static double shortestDelta(double current, double target) {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }

    public boolean hasLastCbcYawWritten() { return hasLastCbcYawWritten; }
    public double getLastCbcYawWritten() { return lastCbcYawWritten; }
    public void recordCbcYawWritten(double yaw) {
        this.lastCbcYawWritten = yaw;
        this.hasLastCbcYawWritten = true;
    }

    public double getMinAngleDeg() { return minAngleDeg; }
    public double getMaxAngleDeg() { return maxAngleDeg; }
    public void setMinAngleDeg(double min) { this.minAngleDeg = min; }
    public void setMaxAngleDeg(double max) { this.maxAngleDeg = max; }
    public void setInternalMinAngleDeg(double min) { this.minAngleDeg = min; }
    public void setInternalMaxAngleDeg(double max) { this.maxAngleDeg = max; }
    
    @Override
    public void tick() {
        super.tick();
    }

    public boolean canPossiblyAimAt(Vec3 origin, Vec3 target) { return true; }
    public void onRelevantNeighborChanged(BlockPos pos) {}
    
    public boolean atTargetYaw(boolean lag) {
        // For CBC, the AutoPitchController usually handles the mount.
        // If this is a standalone yaw controller, we check its internal target.
        // For now, return true to not block firing if this is used as a pass-through.
        return true; 
    }
}
