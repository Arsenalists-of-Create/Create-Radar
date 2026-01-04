package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.controller.firing.FiringControlBlockEntity;
import com.happysg.radar.block.datalink.screens.TargetingConfig;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import java.util.ArrayList;
import java.util.List;

public class AutoPitchControllerBlockEntity extends GeneratingKineticBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Cannon pitch tolerance (radians) ---
    private static final double TOLERANCE = 0.1;

    // --- Active servo tunables ---
    private static final double KP = 6.0;                      // proportional gain
    private static final double MAX_OMEGA = 6.0;               // rad/s clamp
    private static final double DEADBAND = Math.toRadians(1.5);// stop within ~1.5 deg
    private static final double TAU = Math.PI * 2.0;

    // Your existing meaning: target cannon pitch (radians)
    private double targetAngle;
    public boolean isRunning;
    private boolean artillery = false;
    private RadarTrack track;

    // --- NEW: active servo internal angle tracker (radians) ---
    // This is the shaft angle the servo is driving toward, not the cannon pitch.
    // We’ll treat it as "same angle space" as targetAngle for your use-case.
    private double currentAngleRad = 0.0;

    // --- Generated speed cache (Create wants a stable getter) ---
    private float generatedSpeed = 0f;

    // --- Keep your radar-driven "kinetic control" knobs if you still want them ---
    // Here they act as a multiplier on the servo’s computed speed (optional).
    private float speedMultiplier = 1.0f;
    private boolean invertOutput = false;

    private int signalAge = 0;
    private static final int SIGNAL_TIMEOUT_TICKS = 40; // 2s @ 20tps

    //abstract class for firing control to avoid cluttering pitch logic
    public FiringControlBlockEntity firingControl;
    public CannonMountBlockEntity mountBlock;

    public AutoPitchControllerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (Mods.CREATEBIGCANNONS.isLoaded()) {
            LOGGER.debug("  → CBC is loaded");
            BlockPos cannonMountPos = getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
            if (level != null && level.getBlockEntity(cannonMountPos) instanceof CannonMountBlockEntity mount) {
                LOGGER.debug("  → Level not null and cannon pos good");
                firingControl = new FiringControlBlockEntity(this, mount);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide())
            return;

        // --- Optional: decay radar “assist” multiplier if radar stops updating ---
        if (track != null) {
            if (++signalAge > SIGNAL_TIMEOUT_TICKS) {
                track = null;
                speedMultiplier = 1.0f;
                invertOutput = false;
                signalAge = 0;
                requestKineticUpdate();
                LOGGER.debug("  → radar kinetic signal timed out; resetting passthrough");
            }
        }

        // --- Active servo logic: drive shaft toward targetAngle ---
        // If you only want it active while "isRunning", gate it:
        if (!isRunning) {
            setGeneratedSpeedAndUpdate(0f);
            return;
        }

        // Track current shaft angle from actual network speed (RPM -> rad/s)
        double rpm = getSpeed();
        double omega = rpm * TAU / 60.0;      // rad/s
        double dt = 1.0 / 20.0;               // 20tps
        currentAngleRad = wrap0ToTau(currentAngleRad + omega * dt);

        // Shortest signed error [-pi, +pi]
        double error = wrapToPi(targetAngle - currentAngleRad);

        // P controller -> commanded rad/s
        double cmdOmega = clamp(KP * error, -MAX_OMEGA, MAX_OMEGA);

        // Deadband near target
        if (Math.abs(error) < DEADBAND) cmdOmega = 0.0;

        // Apply your optional multiplier + inversion (radar assist)
        double mult = speedMultiplier;
        if (invertOutput) mult = -mult;

        cmdOmega *= mult;

        // rad/s -> RPM for Create generated speed
        float newGenSpeed = (float) (cmdOmega * 60.0 / TAU);

        setGeneratedSpeedAndUpdate(newGenSpeed);

        // If you still want to physically rotate the CBC mount toward targetAngle,
        // you can call tryRotateCannon() here. But note: that rotates the cannon,
        // not the shaft. Decide if you want both coupled.
        // tryRotateCannon();

        if (firingControl != null) {
            firingControl.tick();
        }
    }

    private void setGeneratedSpeedAndUpdate(float newSpeed) {
        if (Math.abs(newSpeed - generatedSpeed) <= 0.01f)
            return;
        generatedSpeed = newSpeed;
        updateGeneratedRotation(); // Create network recompute
        setChanged();
    }

    @Override
    public float getGeneratedSpeed() {
        return generatedSpeed;
    }

    public BlockPos getMount(){
        return getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
    }

    // ----------------------------------------------------------------
    // Your existing CBC pitch rotation logic (unchanged)
    // ----------------------------------------------------------------
    private void tryRotateCannon() {
        if (level == null || level.isClientSide())
            return;

        BlockPos cannonMountPos = getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
        if (!(level.getBlockEntity(cannonMountPos) instanceof CannonMountBlockEntity mount))
            return;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return;

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption))
            return;

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double pitchDifference = targetAngle - currentPitch;
        double speedFactor = Math.abs(getSpeed()) / 32.0; // your old step logic

        double newPitch;
        if (Math.abs(pitchDifference) > TOLERANCE) {
            if (Math.abs(pitchDifference) > speedFactor) {
                newPitch = currentPitch + Math.signum(pitchDifference) * speedFactor;
            } else {
                newPitch = targetAngle;
            }
        } else {
            newPitch = targetAngle;
        }

        mount.setPitch((float) newPitch);
        mount.notifyUpdate();
    }

    public boolean atTargetPitch() {
        BlockPos turretPos = getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
        if (level == null || !(level.getBlockEntity(turretPos) instanceof CannonMountBlockEntity mount))
            return false;
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null)
            return false;
        int invert = -contraption.getInitialOrientation().getStepZ() + contraption.getInitialOrientation().getStepX();
        return Math.abs(contraption.pitch * invert - targetAngle) < TOLERANCE;
    }

    public void setTargetAngle(float targetAngle) {
        this.targetAngle = targetAngle;
        setChanged();
    }

    public double getTargetAngle() {
        return targetAngle;
    }

    // ----------------------------------------------------------------
    // NBT
    // ----------------------------------------------------------------
    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        targetAngle = compound.getDouble("TargetAngle");
        isRunning = compound.getBoolean("IsRunning");

        currentAngleRad = compound.contains("ServoAngle") ? compound.getDouble("ServoAngle") : 0.0;
        generatedSpeed = compound.contains("GenSpeed") ? compound.getFloat("GenSpeed") : 0f;

        if (compound.contains("SpeedMult"))
            speedMultiplier = compound.getFloat("SpeedMult");
        invertOutput = compound.getBoolean("InvertOut");
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        compound.putDouble("TargetAngle", targetAngle);
        compound.putBoolean("IsRunning", isRunning);

        compound.putDouble("ServoAngle", currentAngleRad);
        compound.putFloat("GenSpeed", generatedSpeed);

        compound.putFloat("SpeedMult", speedMultiplier);
        compound.putBoolean("InvertOut", invertOutput);
    }

    // ----------------------------------------------------------------
    // Your setTarget() and radar helpers (mostly unchanged)
    // ----------------------------------------------------------------
    public void setTarget(Vec3 targetPos) {
        if (level == null || level.isClientSide()) return;

        if (targetPos == null) {
            isRunning = false;
            setChanged();
            return;
        }

        if (level.getBlockEntity(getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING))) instanceof CannonMountBlockEntity mount) {

            if (PhysicsHandler.isBlockInShipyard(level, this.getBlockPos())) {
                List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, (ServerLevel) level);
                if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) return;

                this.targetAngle = angles.get(0).get(0);
                if (firingControl == null) return;

                this.firingControl.cannonMount.setYaw(angles.get(0).get(1).floatValue());
                isRunning = true;
                setChanged();
            } else {
                List<Double> angles = CannonTargeting.calculatePitch(mount, targetPos, (ServerLevel) level);
                if (angles == null || angles.isEmpty()) {
                    isRunning = false;
                    setChanged();
                    return;
                }

                List<Double> usableAngles = new ArrayList<>();
                for (double angle : angles) {
                    if (mount.getContraption() == null) break;
                    if (angle < mount.getContraption().maximumElevation() && angle > -mount.getContraption().maximumDepression()) {
                        usableAngles.add(angle);
                    }
                }

                if (artillery && usableAngles.size() == 2) {
                    targetAngle = angles.get(1);
                } else if (!usableAngles.isEmpty()) {
                    targetAngle = usableAngles.get(0);
                }

                isRunning = true;
                setChanged();
            }
        }
    }

    private void requestKineticUpdate() {
        updateGeneratedRotation();
        setChanged();
    }

    public void setTrack(RadarTrack track){
        this.track = track;
        this.signalAge = 0;
        applyTrackKineticControl(track);
    }

    private void applyTrackKineticControl(RadarTrack t) {
        if (t == null || t.getPosition() == null) {
            speedMultiplier = 1.0f;
            invertOutput = false;
            requestKineticUpdate();
            return;
        }

        double dist = distanceToTrack(t);
        double bearing = bearingToTrackDeg(t);

        float mult = (float) Mth.clamp(64.0 / Math.max(dist, 1.0), 0.25, 8.0);
        invertOutput = (bearing > 90.0 && bearing < 270.0);

        speedMultiplier = mult;

        requestKineticUpdate();
    }

    private double distanceToTrack(RadarTrack t) {
        if (t == null || t.getPosition() == null) return Double.POSITIVE_INFINITY;
        Vec3 myPos = Vec3.atCenterOf(this.worldPosition);
        return myPos.distanceTo(t.getPosition());
    }

    private double bearingToTrackDeg(RadarTrack t) {
        if (t == null || t.getPosition() == null) return 0;

        Vec3 myPos = Vec3.atCenterOf(this.worldPosition);
        Vec3 toTarget = t.getPosition().subtract(myPos);

        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        if (flat.lengthSqr() < 1e-9) return 0;

        flat = flat.normalize();

        var facing = getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING);
        Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ()).normalize();

        double crossY = forward.x * flat.z - forward.z * flat.x;
        double dot = forward.x * flat.x + forward.z * flat.z;

        double angleRad = Math.atan2(crossY, dot);
        double deg = Math.toDegrees(angleRad);
        if (deg < 0) deg += 360.0;

        return deg;
    }

    public void setFiringTarget(Vec3 targetPos, TargetingConfig targetingConfig ) {
        if (firingControl == null) {
            BlockPos mountPos = getBlockPos().relative(getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING));
            if (level != null && level.getBlockEntity(mountPos) instanceof CannonMountBlockEntity mount) {
                firingControl = new FiringControlBlockEntity(this, mount);
            }
        }

        if (firingControl == null) return;

        firingControl.setTarget(targetPos, targetingConfig, track);
    }

    public void setSafeZones(List<AABB> safeZones) {
        if (firingControl == null) return;
        firingControl.setSafeZones(safeZones);
    }

    public void setKineticOutput(float multiplier, boolean invert) {
        speedMultiplier = multiplier;
        invertOutput = invert;
        signalAge = 0;
        requestKineticUpdate();
        setChanged();
    }

    // ----------------------------------------------------------------
    // Math helpers
    // ----------------------------------------------------------------
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double wrap0ToTau(double a) {
        a = a % TAU;
        if (a < 0) a += TAU;
        return a;
    }

    private static double wrapToPi(double a) {
        a = (a + Math.PI) % TAU;
        if (a < 0) a += TAU;
        return a - Math.PI;
    }
}
