package com.happysg.radar.block.controller.kinetic;

import com.happysg.radar.compat.cbc.DirectCbcMountMotion;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Fail-closed closed-loop control for the isolated generator used by a kinetic
 * cannon controller. Endpoint attachment is deliberately absent here: Create
 * propagation owns the adapter-selected Swivel endpoint.
 */
public final class KineticControllerState {
    private static final String NBT_KEY = "KineticController";
    private static final double SPEED_EPSILON = 1.0e-5;
    private static final double STOP_RPM = 0.05;
    private static final int REVERSAL_HOLD_TICKS = 5;
    private static final int REVERSAL_MIN_HOLD_TICKS = 2;
    private static final double REVERSAL_RELEASE_SPEED_DEG_PER_TICK = 0.20;
    private static final int VERIFY_TIMEOUT_TICKS = 40;
    private static final int SETTLE_TICKS = 3;
    private static final double MAX_PHYSICAL_SETPOINT_LEAD_DEGREES = 8.0;
    private static final double PROFILE_RPM_PER_DEGREE_PER_TICK = 12.0;
    private static final double PHYSICAL_BRAKE_LOOKAHEAD_TICKS = 2.0;
    private static final double PHYSICAL_VELOCITY_SAMPLE_WEIGHT = 0.75;
    private static final double TARGET_VELOCITY_SAMPLE_WEIGHT = 0.5;
    private static final double MOVING_TARGET_RATE_THRESHOLD = 0.15;
    private static final double SWIVEL_TRACKING_ERROR_GAIN = 0.75;
    private static final double SWIVEL_TRACKING_RATE_CHANGE = 1.0;
    private static final double MAX_TARGET_SAMPLE_STEP_DEGREES = 5.0;
    private static final double MAX_PHYSICAL_SAMPLE_STEP_DEGREES = 90.0;

    private final CannonAxis axis;
    private final DirectCbcMountMotion.State swivelMotion = new DirectCbcMountMotion.State();
    private long swivelMotionTick;
    private double lastSampledPhysicalAngle = Double.NaN;
    private double physicalAngularVelocityDegPerTick;
    private double lastSampledDesiredBearingTarget = Double.NaN;
    private double targetAngularVelocityDegPerTick;

    @Nullable private KineticMountAdapter activeAdapter;
    @Nullable private KineticMountFrame frame;

    private KineticControllerLifecycle lifecycle = KineticControllerLifecycle.CLOSED;
    private boolean structuralMode;
    private boolean lockedLastTick;
    private boolean hardBlocked;
    private int verificationTicks;
    private int reversalTicks;

    private double observedTarget = Double.NaN;
    private boolean observedRunning;
    private long commandRevision;
    private double desiredBearingTarget = Double.NaN;
    private double remainingDegrees = Double.NaN;
    private double bearingSetpointDegrees = Double.NaN;
    private double physicalBearingDegrees = Double.NaN;
    private double setpointCompensationDegrees = Double.NaN;
    @Nullable private String blockedReason;

    private double commandedGeneratorRpm;
    private int driveSign;
    private int candidateDriveSign;
    private int driveSignVotes;
    private double lastBearingAngle = Double.NaN;

    private int settleTicks;
    private double lastPhysicalAngle = Double.NaN;
    private long readyRevision = -1;
    private double readyControllerTarget = Double.NaN;
    private boolean atDestination;

    private boolean watchdogActive;
    private double watchdogInitialError;
    private double watchdogDestinationTravel;
    private double watchdogBearingTravel;
    private double watchdogPhysicalTravel;
    private double watchdogLastBearing = Double.NaN;
    private double watchdogLastPhysical = Double.NaN;
    private double watchdogLastDestination = Double.NaN;
    private double watchdogMaxStep;
    private boolean continuousTracking;
    private boolean physicalVelocitySampleValid;
    private double trackingToleranceDegrees = Double.POSITIVE_INFINITY;
    private double stopRpm = STOP_RPM;

    private boolean syncRequested;
    private ControllerAngleDelta controllerAngleDelta = ControllerAngleDelta.SHORTEST;

    public KineticControllerState(CannonAxis axis) {
        this.axis = axis;
    }

    public boolean tick(KineticBlockEntity controller,
                        KineticMountAdapterResolution resolution,
                        boolean running,
                        double controllerTargetDegrees,
                        double toleranceDegrees,
                        double availableInputRpm,
                        DoubleConsumer commandGenerator) {
        return tick(controller, resolution, running, controllerTargetDegrees,
                toleranceDegrees, availableInputRpm,
                ControllerAngleDelta.SHORTEST, commandGenerator);
    }

    public boolean tick(KineticBlockEntity controller,
                        KineticMountAdapterResolution resolution,
                        boolean running,
                        double controllerTargetDegrees,
                        double toleranceDegrees,
                        double availableInputRpm,
                        ControllerAngleDelta angleDelta,
                        DoubleConsumer commandGenerator) {
        return tick(controller.getBlockPos(), resolution, running, controllerTargetDegrees,
                toleranceDegrees, availableInputRpm, !controller.hasSource(),
                angleDelta, commandGenerator);
    }

    boolean tick(BlockPos controllerPos,
                 KineticMountAdapterResolution resolution,
                 boolean running,
                 double controllerTargetDegrees,
                 double toleranceDegrees,
                 double availableInputRpm,
                 boolean generatorIsolated,
                 DoubleConsumer commandGenerator) {
        return tick(controllerPos, resolution, running, controllerTargetDegrees,
                toleranceDegrees, availableInputRpm, generatorIsolated,
                ControllerAngleDelta.SHORTEST, commandGenerator);
    }

    boolean tick(BlockPos controllerPos,
                 KineticMountAdapterResolution resolution,
                 boolean running,
                 double controllerTargetDegrees,
                 double toleranceDegrees,
                 double availableInputRpm,
                 boolean generatorIsolated,
                 ControllerAngleDelta angleDelta,
                 DoubleConsumer commandGenerator) {
        controllerAngleDelta = angleDelta == null ? ControllerAngleDelta.SHORTEST : angleDelta;
        double tolerance = effectiveTolerance(toleranceDegrees);
        stopRpm = Math.max(SPEED_EPSILON, Math.min(STOP_RPM, tolerance * 0.5));
        onTargetChanged(running, controllerTargetDegrees, tolerance);

        if (resolution == null || resolution.selection() == KineticMountAdapterResolution.Selection.ABSENT) {
            stopGenerator(commandGenerator);
            setStructuralMode(false);
            activeAdapter = null;
            clearFrame();
            resetEndpointSession(false);
            setLifecycle(KineticControllerLifecycle.CLOSED);
            return false;
        }

        setStructuralMode(true);
        if (resolution.selection() == KineticMountAdapterResolution.Selection.UNAVAILABLE) {
            stopGenerator(commandGenerator);
            settleTicks = 0;
            setAtDestination(false);
            blockedReason = resolution.reason();
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }
        if (resolution.selection() == KineticMountAdapterResolution.Selection.AMBIGUOUS
                || !resolution.hasAdapter() || resolution.adapter() == null) {
            stopGenerator(commandGenerator);
            activeAdapter = null;
            clearFrame();
            resetEndpointSession(false);
            blockedReason = resolution.reason();
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }

        KineticMountAdapter resolved = resolution.adapter();
        if (resolved.axis() != axis) {
            stopGenerator(commandGenerator);
            activeAdapter = null;
            clearFrame();
            resetEndpointSession(false);
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }
        selectAdapter(resolved, commandGenerator);
        KineticMountAdapter adapter = activeAdapter;
        if (adapter == null || !adapter.isValid()) {
            failClosed(commandGenerator, false, "adapter_invalid");
            return true;
        }
        if (!generatorIsolated) {
            failClosed(commandGenerator, true, "generator_not_isolated");
            return true;
        }
        if (adapter.isEndpointFree() && adapter.hasSequenceContext()
                && !adapter.discardStaleSequenceContextIfFree()) {
            failClosed(commandGenerator, true, "stale_sequence_context");
            return true;
        }

        if (!running || !Double.isFinite(controllerTargetDegrees)) {
            stopGenerator(commandGenerator);
            resetEndpointSession(false);
            hardBlocked = false;
            setLifecycle(KineticControllerLifecycle.CLOSED);
            return true;
        }
        if (!adapter.isAssembled()) {
            stopGenerator(commandGenerator);
            lockedLastTick = false;
            settleTicks = 0;
            setAtDestination(false);
            setLifecycle(KineticControllerLifecycle.WAITING_FOR_ASSEMBLY);
            return true;
        }
        if (!adapter.isLocked()) {
            stopGenerator(commandGenerator);
            lockedLastTick = false;
            settleTicks = 0;
            setAtDestination(false);
            setLifecycle(KineticControllerLifecycle.WAITING_FOR_LOCK);
            return true;
        }
        if (!lockedLastTick) {
            lockedLastTick = true;
            stopGenerator(commandGenerator);
            setAtDestination(false);
            setLifecycle(KineticControllerLifecycle.WAITING_FOR_LOCK);
            return true;
        }

        KineticMountFrame liveFrame = adapter.frameIdentity();
        if (liveFrame == null) {
            failClosed(commandGenerator, false, "frame_unavailable");
            return true;
        }

        if (!liveFrame.equals(frame)) {
            boolean replacingFrame = frame != null;
            stopGenerator(commandGenerator);
            frame = liveFrame;
            hardBlocked = false;
            resetEndpointSession(false);
            lockedLastTick = true;
            driveSign = -adapter.getPositiveRotationSign();
            syncRequested = true;
            if (replacingFrame) {
                setLifecycle(KineticControllerLifecycle.ARMING);
                return true;
            }
        }

        double bearingTarget = adapter.getTargetAngleDegrees();
        double physicalAngle = adapter.getPhysicalAngleDegrees();

        if (!Double.isFinite(bearingTarget) || !Double.isFinite(physicalAngle)) {
            failClosed(commandGenerator, false, "physical_feedback_unavailable");
            return true;
        }

        samplePhysicalAngularVelocity(physicalAngle);
        desiredBearingTarget = adapter.bearingTargetForControllerAngle(observedTarget);

        if (!Double.isFinite(desiredBearingTarget)) {
            failClosed(commandGenerator, false, "pitch_swivel_target_unreachable");
            return true;
        }
        sampleTrackingTargetVelocity(desiredBearingTarget);
        bearingSetpointDegrees = bearingTarget;
        physicalBearingDegrees = physicalAngle;
        remainingDegrees = plannedBearingDelta(physicalAngle, desiredBearingTarget);
        double setpointRemaining = plannedBearingDelta(bearingTarget, desiredBearingTarget);

        setpointCompensationDegrees = 0.0;
        observeDriveSign(bearingTarget, tolerance);
        lastBearingAngle = bearingTarget;

        if (hardBlocked) {
            stopGenerator(commandGenerator);
            setAtDestination(false);
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }

        if (watchdogActive && !watchdogAllows(bearingTarget, physicalAngle, desiredBearingTarget, tolerance)) {
            failClosed(commandGenerator, true, blockedReason == null ? "watchdog_travel_bound_exceeded" : blockedReason);
            return true;
        }

        swivelMotionTick++;

        if (Math.abs(remainingDegrees) <= tolerance && Math.abs(setpointRemaining) <= tolerance) {
            settleAtDestination(adapter, tolerance, commandGenerator);
            return true;
        }

        settleTicks = 0;
        lastPhysicalAngle = Double.NaN;
        setAtDestination(false);

        double maximumRpm = matchingDirectCbcDriveRpm(adapter, availableInputRpm);
        if (!Double.isFinite(maximumRpm) || maximumRpm <= SPEED_EPSILON) {
            stopGenerator(commandGenerator);
            watchdogActive = false;
            setLifecycle(KineticControllerLifecycle.CLOSED);
            return true;
        }
        if (driveSign == 0) {
            driveSign = -adapter.getPositiveRotationSign();
        }
        if (driveSign == 0) {
            failClosed(commandGenerator, false, "drive_direction_unavailable");
            return true;
        }

        if (adapter.hasSequenceContext() && adapter.isDrivenBy(controllerPos)) {
            failClosed(commandGenerator, true, "sequence_context_present");
            return true;
        }
        if (!adapter.isEndpointFree() && !adapter.isDrivenBy(controllerPos)) {
            failClosed(commandGenerator, true, "endpoint_owned_by_other_source");
            return true;
        }


        double maximumStep = adapter.effectiveDegreesPerTick(maximumRpm);
        if (!Double.isFinite(maximumStep) || maximumStep <= SPEED_EPSILON) {
            failClosed(commandGenerator, false, "swivel_speed_conversion_invalid");
            return true;
        }

        long motionTick = swivelMotionTick;
        double profileRpm = maximumStep * PROFILE_RPM_PER_DEGREE_PER_TICK;

        if (continuousTracking && physicalVelocitySampleValid) {
            swivelMotion.recordAppliedStep(clamp(physicalAngularVelocityDegPerTick, -maximumStep, maximumStep));
        }

        double plannedStep = continuousTracking
                ? swivelMotion.nextTrackingStep(remainingDegrees, profileRpm, desiredBearingTarget, motionTick, axis == CannonAxis.YAW)
                : swivelMotion.nextStep(setpointRemaining, profileRpm);

        if (continuousTracking && physicalVelocitySampleValid) {
            double targetRate = targetAngularVelocityDegPerTick;
            double measuredRate = clamp(physicalAngularVelocityDegPerTick, -maximumStep, maximumStep);


            if (Math.abs(targetRate) > MOVING_TARGET_RATE_THRESHOLD && measuredRate * targetRate > 0.0) {
                double requestedTrackingRate = clamp(targetRate + SWIVEL_TRACKING_ERROR_GAIN * remainingDegrees, -maximumStep, maximumStep);
                if (requestedTrackingRate * targetRate > 0.0) {
                    double minimumRate = Math.max(-maximumStep, measuredRate - SWIVEL_TRACKING_RATE_CHANGE);
                    double maximumRate = Math.min(maximumStep, measuredRate + SWIVEL_TRACKING_RATE_CHANGE);
                    double followingStep = clamp(requestedTrackingRate, minimumRate, maximumRate);

                    if (followingStep * targetRate > 0.0) {
                        plannedStep = followingStep;
                        swivelMotion.recordAppliedStep(plannedStep);
                    }
                }
            }
        }

        double physicalSetpointLead = plannedBearingDelta(physicalAngle, bearingTarget);
        boolean leadLimited = false;

        if (Math.abs(plannedStep) > SPEED_EPSILON) {
            double direction = Math.signum(plannedStep);
            double physicalRemaining = Math.max(0.0, remainingDegrees * direction);
            double targetRate = continuousTracking ? targetAngularVelocityDegPerTick : 0.0;
            double closingVelocity = Math.max(0.0, (physicalAngularVelocityDegPerTick - targetRate) * direction);

            double lookaheadTicks = continuousTracking ? 1.0 : PHYSICAL_BRAKE_LOOKAHEAD_TICKS;
            double projectedRemaining = physicalRemaining - closingVelocity * lookaheadTicks;
            boolean targetAdvancing = continuousTracking && targetRate * direction > MOVING_TARGET_RATE_THRESHOLD;
            double maximumLead = MAX_PHYSICAL_SETPOINT_LEAD_DEGREES;


            if (closingVelocity > SPEED_EPSILON && !targetAdvancing) {
                maximumLead = Math.min(maximumLead, Math.max(0.0, projectedRemaining));
            }

            double existingForwardLead = physicalSetpointLead * direction;
            boolean predictedToPassTarget = closingVelocity > SPEED_EPSILON && projectedRemaining <= 0.0;
            double availableLead = predictedToPassTarget ? 0.0 : Math.max(0.0, maximumLead - existingForwardLead);

            if (Math.abs(plannedStep) > availableLead) {
                plannedStep = Math.copySign(availableLead, plannedStep);
                leadLimited = true;

                if (continuousTracking) {
                    swivelMotion.recordAppliedStep(plannedStep);
                } else {
                    swivelMotion.reset();
                }
            }
        }


        double rawRpm = clamp(plannedStep / maximumStep * maximumRpm, -maximumRpm, maximumRpm) * driveSign;

        if (!watchdogActive) {
            beginWatchdog(adapter, bearingTarget, physicalAngle, desiredBearingTarget, maximumRpm);
        }

        if (Math.abs(rawRpm) <= stopRpm) {

            if (continuousTracking || leadLimited) {
                swivelMotion.recordAppliedStep(0.0);
                holdGenerator(commandGenerator);
            } else {
                stopGenerator(commandGenerator);
            }

            if (!adapter.wakePhysicalAssembly()) {
                failClosed(commandGenerator, true, "physical_assembly_could_not_wake");
                return true;
            }

            setLifecycle(KineticControllerLifecycle.SETTLING);
            return true;
        }

        if (reversalTicks > 0) {
            int completedHoldTicks = REVERSAL_HOLD_TICKS - reversalTicks;
            boolean physicallySlowed = physicalVelocitySampleValid && Double.isFinite(physicalAngularVelocityDegPerTick) && Math.abs(physicalAngularVelocityDegPerTick) <= REVERSAL_RELEASE_SPEED_DEG_PER_TICK;
            boolean canReleaseEarly = continuousTracking && completedHoldTicks >= REVERSAL_MIN_HOLD_TICKS && physicallySlowed;

            if (canReleaseEarly) {
                reversalTicks = 0;
            } else {
                reversalTicks--;
                holdForReversal(commandGenerator);
                return true;
            }
        }

        if (Math.abs(commandedGeneratorRpm) > stopRpm && Math.signum(commandedGeneratorRpm) != Math.signum(rawRpm)) {
            reversalTicks = REVERSAL_HOLD_TICKS;
            holdForReversal(commandGenerator);
            return true;
        }

        commandGenerator(commandGenerator, rawRpm);

        if (adapter.hasSequenceContext()) {
            failClosed(commandGenerator, true, "sequence_context_present");
            return true;
        }
        if (!adapter.isDrivenBy(controllerPos)) {
            verificationTicks++;
            setLifecycle(KineticControllerLifecycle.VERIFYING_ENDPOINT);
            if (!adapter.isEndpointFree() || verificationTicks >= VERIFY_TIMEOUT_TICKS) {
                failClosed(commandGenerator, true, "endpoint_drive_verification_failed");
            }
            return true;
        }

        verificationTicks = 0;
        setLifecycle(KineticControllerLifecycle.MOVING);
        return true;
    }

    public static double matchingDirectCbcDriveRpm(KineticMountAdapter adapter, double availableInputRpm) {

        if (adapter == null || !Double.isFinite(availableInputRpm)) {
            return 0.0;
        }

        double permittedRpm = adapter.maximumDriveRpm(availableInputRpm);
        double degreesPerRpm = adapter.effectiveDegreesPerTick(1.0);

        if (!Double.isFinite(permittedRpm) || permittedRpm <= 0.0 || !Double.isFinite(degreesPerRpm) || degreesPerRpm <= SPEED_EPSILON) {
            return 0.0;
        }

        double directSpeedLimit = DirectCbcMountMotion.maxSpeedForRpm(Math.abs(availableInputRpm));
        return Math.min(permittedRpm, directSpeedLimit / degreesPerRpm);
    }

    public void onTargetChanged(boolean running, double targetDegrees, double toleranceDegrees) {
        double tolerance = effectiveTolerance(toleranceDegrees);
        boolean materialTargetChange = !Double.isFinite(observedTarget)
                || !Double.isFinite(targetDegrees)
                || Math.abs(KineticAngleMath.shortestDelta(observedTarget, targetDegrees)) > tolerance;
        boolean runningChange = observedRunning != running;
        // Keep every aim update, including sub-tolerance motion. Tolerance
        // controls settling, not whether a new destination is remembered.
        observedTarget = targetDegrees;
        observedRunning = running;
        if (materialTargetChange || runningChange) {
            commandRevision++;
            settleTicks = 0;
            lastPhysicalAngle = Double.NaN;
            readyRevision = -1;
            setAtDestination(false);
        }
        // A continuously moving solver target is still the same physical motion
        // session. Preserve its cumulative travel budget (and any fail-safe latch)
        // across target revisions so it cannot hide a runaway bearing by granting a
        // fresh watchdog allowance every tick. An explicit stop/start is the operator
        // boundary that begins a new command session.
        if (runningChange) {
            watchdogActive = false;
            hardBlocked = false;
            blockedReason = null;
        }
    }

    /**
     * Selects the progress-based watchdog used by continuously changing debug
     * targets. The cumulative travel bound remains active in this mode.
     */
    public void beginContinuousTracking() {
        beginContinuousTracking(Double.POSITIVE_INFINITY);
    }

    public void beginContinuousTracking(double maximumFiringToleranceDegrees) {
        // Leave headroom inside the firing gate without resetting the motion
        // watchdog when range (and hence required accuracy) changes.
        trackingToleranceDegrees = Double.isFinite(maximumFiringToleranceDegrees)
                ? Math.max(0.0, maximumFiringToleranceDegrees) * 0.5
                : Double.POSITIVE_INFINITY;
        if (continuousTracking) {
            return;
        }
        continuousTracking = true;
        resetTrackingTargetVelocity();
        watchdogActive = false;
        hardBlocked = false;
    }

    /**
     * Ends a moving-target session without discarding the verified mount
     * frame. The next ordinary set-angle command gets a fresh finite watchdog.
     */
    public void endContinuousTracking() {
        trackingToleranceDegrees = Double.POSITIVE_INFINITY;
        if (!continuousTracking) {
            return;
        }
        continuousTracking = false;
        resetTrackingTargetVelocity();
        watchdogActive = false;
        hardBlocked = false;
    }

    boolean isContinuousTracking() {
        return continuousTracking;
    }

    private double effectiveTolerance(double toleranceDegrees) {
        return Math.min(Math.max(0.0, toleranceDegrees),
                continuousTracking ? trackingToleranceDegrees
                        : Double.POSITIVE_INFINITY);
    }

    public boolean isReady(KineticMountAdapterResolution resolution, boolean running,
                           double controllerTargetDegrees, double toleranceDegrees) {
        if (!structuralMode || !atDestination || !running || readyRevision != commandRevision
                || !Double.isFinite(readyControllerTarget)
                || Math.abs(KineticAngleMath.shortestDelta(readyControllerTarget, controllerTargetDegrees))
                > Math.max(0.0, toleranceDegrees)
                || resolution == null || !resolution.hasAdapter() || resolution.adapter() == null
                || activeAdapter == null || !activeAdapter.hasSameEndpoint(resolution.adapter())) {
            return false;
        }
        if (!activeAdapter.isValid() || !activeAdapter.isAssembled() || !activeAdapter.isLocked()
                || !Objects.equals(frame, activeAdapter.frameIdentity())
                || activeAdapter.hasSequenceContext()
                || !activeAdapter.isEndpointSafelyReleased()) {
            return false;
        }
        double physical = activeAdapter.getPhysicalAngleDegrees();
        double desired = activeAdapter.bearingTargetForControllerAngle(controllerTargetDegrees);
        return Double.isFinite(physical) && Double.isFinite(desired)
                && Math.abs(KineticAngleMath.shortestDelta(physical, desired))
                <= Math.max(0.0, toleranceDegrees);
    }

    /**
     * Checks whether this actuator is safe and accurately aligned for firing.
     * Unlike {@link #isReady}, an endpoint driven by this controller may pass:
     * radar tracking continuously revises the destination and may never enter
     * the fully stopped, three-tick settled state.
     */
    public boolean isAlignedForFiring(KineticMountAdapterResolution resolution,
                                      BlockPos controllerPos, boolean running,
                                      double controllerTargetDegrees,
                                      double toleranceDegrees) {
        if (!structuralMode || hardBlocked || !running || !observedRunning
                || !Double.isFinite(controllerTargetDegrees)
                || !Double.isFinite(toleranceDegrees)
                || resolution == null || !resolution.hasAdapter()
                || resolution.adapter() == null || activeAdapter == null
                || resolution.adapter().axis() != axis
                || !activeAdapter.hasSameEndpoint(resolution.adapter())) {
            return false;
        }

        double tolerance = Math.max(0.0, toleranceDegrees);
        if (!Double.isFinite(observedTarget)
                || Math.abs(KineticAngleMath.shortestDelta(
                        observedTarget, controllerTargetDegrees)) > tolerance
                || (lifecycle != KineticControllerLifecycle.MOVING
                && lifecycle != KineticControllerLifecycle.SETTLING)
                || !activeAdapter.isValid()
                || !activeAdapter.isAssembled()
                || !activeAdapter.isLocked()
                || frame == null
                || !Objects.equals(frame, activeAdapter.frameIdentity())
                || activeAdapter.hasSequenceContext()) {
            return false;
        }

        boolean endpointSafe = activeAdapter.isEndpointSafelyReleased()
                || activeAdapter.isDrivenBy(controllerPos);
        if (!endpointSafe) {
            return false;
        }

        double physical = activeAdapter.getPhysicalAngleDegrees();
        double desired = activeAdapter.bearingTargetForControllerAngle(controllerTargetDegrees);
        return Double.isFinite(physical) && Double.isFinite(desired)
                && Math.abs(KineticAngleMath.shortestDelta(physical, desired))
                <= tolerance;
    }

    private void selectAdapter(KineticMountAdapter resolved, DoubleConsumer commandGenerator) {
        if (activeAdapter != null && activeAdapter.hasSameEndpoint(resolved)) {
            return;
        }
        stopGenerator(commandGenerator);
        activeAdapter = resolved;
        resetEndpointSession(false);
    }

    private void settleAtDestination(KineticMountAdapter adapter, double tolerance, DoubleConsumer commandGenerator) {
        if (continuousTracking) {
            swivelMotion.recordAppliedStep(0.0);
            holdGenerator(commandGenerator);
        } else {
            stopGenerator(commandGenerator);
        }
        watchdogActive = false;

        double physical = adapter.getPhysicalAngleDegrees();
        if (!Double.isFinite(physical) || adapter.hasSequenceContext()) {
            settleTicks = 0;
            lastPhysicalAngle = Double.NaN;
            setAtDestination(false);
            blockedReason = !Double.isFinite(physical)
                    ? "physical_feedback_unavailable" : "sequence_context_present";
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return;
        }
        double error = Math.abs(KineticAngleMath.shortestDelta(physical, desiredBearingTarget));
        double motion = Double.isFinite(lastPhysicalAngle)
                ? Math.abs(KineticAngleMath.shortestDelta(lastPhysicalAngle, physical))
                : Double.POSITIVE_INFINITY;
        lastPhysicalAngle = physical;
        if ((error > tolerance || motion > tolerance) && !adapter.wakePhysicalAssembly()) {
            settleTicks = 0;
            setAtDestination(false);
            blockedReason = "physical_assembly_could_not_wake";
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return;
        }
        if (error <= tolerance && motion <= tolerance) {
            settleTicks = Math.min(SETTLE_TICKS, settleTicks + 1);
        } else {
            settleTicks = 0;
        }
        setLifecycle(KineticControllerLifecycle.SETTLING);
        if (settleTicks >= SETTLE_TICKS) {
            readyRevision = commandRevision;
            readyControllerTarget = observedTarget;
            setAtDestination(true);
        } else {
            setAtDestination(false);
        }
    }

    private void observeDriveSign(double bearingAngle, double tolerance) {
        if (!Double.isFinite(lastBearingAngle) || Math.abs(commandedGeneratorRpm) <= stopRpm) {
            return;
        }
        double movement = KineticAngleMath.shortestDelta(lastBearingAngle, bearingAngle);
        if (Math.abs(movement) <= Math.max(0.05, tolerance * 0.25)) {
            return;
        }
        int observedSign = (int) Math.signum(movement * commandedGeneratorRpm);
        if (observedSign == 0 || observedSign == driveSign) {
            candidateDriveSign = driveSign;
            driveSignVotes = 0;
            return;
        }
        if (candidateDriveSign != observedSign) {
            candidateDriveSign = observedSign;
            driveSignVotes = 1;
        } else {
            driveSignVotes++;
        }
        if (driveSignVotes >= 3) {
            driveSign = observedSign;
            driveSignVotes = 0;
            swivelMotion.reset();
            reversalTicks = REVERSAL_HOLD_TICKS;
            syncRequested = true;
        }
    }

    private void beginWatchdog(KineticMountAdapter adapter, double bearingAngle,
                               double physicalAngle, double destination,
                               double maximumRpm) {
        watchdogActive = true;
        watchdogInitialError = Math.abs(plannedBearingDelta(physicalAngle, destination));
        watchdogDestinationTravel = 0.0;
        watchdogBearingTravel = 0.0;
        watchdogPhysicalTravel = 0.0;
        watchdogLastBearing = bearingAngle;
        watchdogLastPhysical = physicalAngle;
        watchdogLastDestination = destination;
        watchdogMaxStep = adapter.effectiveDegreesPerTick(maximumRpm);
    }

    private boolean watchdogAllows(double bearingAngle, double physicalAngle, double destination, double tolerance) {
        if (!watchdogActive) {
            return false;
        }

        double bearingStep = KineticAngleMath.shortestDelta(watchdogLastBearing, bearingAngle);
        double destinationStep = plannedBearingDelta(watchdogLastDestination, destination);
        double physicalStep = KineticAngleMath.shortestDelta(watchdogLastPhysical, physicalAngle);

        watchdogBearingTravel += Math.abs(bearingStep);
        watchdogDestinationTravel += Math.abs(destinationStep);
        watchdogPhysicalTravel += Math.abs(physicalStep);

        watchdogLastBearing = bearingAngle;
        watchdogLastPhysical = physicalAngle;
        watchdogLastDestination = destination;

        double allowance = watchdogInitialError + watchdogDestinationTravel + 2.0 * Math.max(watchdogMaxStep, tolerance) + 2.0 * MAX_PHYSICAL_SETPOINT_LEAD_DEGREES;

        if (watchdogBearingTravel > allowance + 1.0e-6 || watchdogPhysicalTravel > allowance + 1.0e-6) {
            blockedReason = "watchdog_travel_bound_exceeded";
            return false;
        }

        return true;
    }

    private double plannedBearingDelta(double currentBearingDegrees, double destinationBearingDegrees) {
        if (activeAdapter != null && activeAdapter.hasDynamicPitchMapping()) {
            return KineticAngleMath.shortestDelta(currentBearingDegrees, destinationBearingDegrees);
        }

        if (frame == null || !Double.isFinite(currentBearingDegrees) || !Double.isFinite(destinationBearingDegrees)) {
            return KineticAngleMath.shortestDelta(currentBearingDegrees, destinationBearingDegrees);
        }

        double currentController = frame.controllerTargetFor(currentBearingDegrees);
        double destinationController = frame.controllerTargetFor(destinationBearingDegrees);
        double controllerDelta = controllerAngleDelta.remainingDegrees(currentController, destinationController);
        return frame.conversionSign() * controllerDelta;
    }

    private void samplePhysicalAngularVelocity(double physicalAngle) {
        if (!Double.isFinite(physicalAngle)) {
            resetPhysicalMotionSample();
            return;
        }

        if (!Double.isFinite(lastSampledPhysicalAngle)) {
            physicalAngularVelocityDegPerTick = 0.0;
            physicalVelocitySampleValid = false;
        } else {
            double step = KineticAngleMath.shortestDelta(lastSampledPhysicalAngle, physicalAngle);

            if (!Double.isFinite(step) || Math.abs(step) > MAX_PHYSICAL_SAMPLE_STEP_DEGREES) {
                physicalAngularVelocityDegPerTick = 0.0;
                physicalVelocitySampleValid = false;
            } else {
                physicalAngularVelocityDegPerTick = physicalAngularVelocityDegPerTick * (1.0 - PHYSICAL_VELOCITY_SAMPLE_WEIGHT) + step * PHYSICAL_VELOCITY_SAMPLE_WEIGHT;
                physicalVelocitySampleValid = true;
            }
        }

        lastSampledPhysicalAngle = physicalAngle;
    }

    private void resetPhysicalMotionSample() {
        lastSampledPhysicalAngle = Double.NaN;
        physicalAngularVelocityDegPerTick = 0.0;
        physicalVelocitySampleValid = false;
    }

    private void sampleTrackingTargetVelocity(double targetAngle) {
        if (!continuousTracking || !Double.isFinite(targetAngle)) {
            resetTrackingTargetVelocity();
            return;
        }

        if (!Double.isFinite(lastSampledDesiredBearingTarget)) {
            targetAngularVelocityDegPerTick = 0.0;
        } else {
            double step = KineticAngleMath.shortestDelta(lastSampledDesiredBearingTarget, targetAngle);

            if (!Double.isFinite(step) || Math.abs(step) > MAX_TARGET_SAMPLE_STEP_DEGREES) {
                targetAngularVelocityDegPerTick = 0.0;
            } else {
                targetAngularVelocityDegPerTick = targetAngularVelocityDegPerTick * (1.0 - TARGET_VELOCITY_SAMPLE_WEIGHT) + step * TARGET_VELOCITY_SAMPLE_WEIGHT;
            }
        }

        lastSampledDesiredBearingTarget = targetAngle;
    }

    private void resetTrackingTargetVelocity() {
        lastSampledDesiredBearingTarget = Double.NaN;
        targetAngularVelocityDegPerTick = 0.0;
    }

    private void failClosed(DoubleConsumer commandGenerator,
                            boolean blockUntilCommandChange,
                            String reason) {
        stopGenerator(commandGenerator);
        hardBlocked |= blockUntilCommandChange;
        blockedReason = reason;
        setAtDestination(false);
        setLifecycle(KineticControllerLifecycle.BLOCKED);
    }

    private void commandGenerator(DoubleConsumer command, double rpm) {
        commandedGeneratorRpm = Double.isFinite(rpm) ? rpm : 0.0;
        command.accept(commandedGeneratorRpm);
    }

    private void holdGenerator(DoubleConsumer command) {
        boolean changed = Math.abs(commandedGeneratorRpm) > SPEED_EPSILON;

        commandedGeneratorRpm = 0.0;
        verificationTicks = 0;
        command.accept(0.0);

        if (changed) {
            syncRequested = true;
        }
    }

    private void stopGenerator(DoubleConsumer command) {
        swivelMotion.reset();
        holdGenerator(command);
    }

    private void holdForReversal(DoubleConsumer command) {
        if (continuousTracking) {
            swivelMotion.recordAppliedStep(0.0);
        } else {
            swivelMotion.reset();
        }

        holdGenerator(command);
        setLifecycle(KineticControllerLifecycle.REVERSING);
    }

    private void resetEndpointSession(boolean resetFrame) {
        lockedLastTick = false;
        settleTicks = 0;
        reversalTicks = 0;
        swivelMotion.reset();
        resetPhysicalMotionSample();
        resetTrackingTargetVelocity();
        verificationTicks = 0;
        desiredBearingTarget = Double.NaN;
        remainingDegrees = Double.NaN;
        bearingSetpointDegrees = Double.NaN;
        physicalBearingDegrees = Double.NaN;
        setpointCompensationDegrees = Double.NaN;
        blockedReason = null;
        lastPhysicalAngle = Double.NaN;
        lastBearingAngle = Double.NaN;
        readyRevision = -1;
        readyControllerTarget = Double.NaN;
        watchdogActive = false;
        hardBlocked = false;
        driveSign = 0;
        candidateDriveSign = 0;
        driveSignVotes = 0;
        setAtDestination(false);
        if (resetFrame) {
            clearFrame();
        }
    }

    private void clearFrame() {
        if (frame != null) {
            frame = null;
            syncRequested = true;
        }
    }

    private void setLifecycle(KineticControllerLifecycle next) {
        if (lifecycle != next) {
            lifecycle = next;
            syncRequested = true;
        }
    }

    private void setStructuralMode(boolean structural) {
        if (structuralMode != structural) {
            structuralMode = structural;
            syncRequested = true;
        }
    }

    private void setAtDestination(boolean value) {
        if (atDestination != value) {
            atDestination = value;
            syncRequested = true;
        }
    }

    public boolean isStructuralMode() {
        return structuralMode;
    }

    public boolean isAtDestination() {
        return structuralMode && atDestination;
    }

    public boolean isBlocked() {
        return lifecycle == KineticControllerLifecycle.BLOCKED;
    }

    public double getCommandedGeneratorRpm() {
        return commandedGeneratorRpm;
    }

    public double getDesiredBearingTarget() {
        return desiredBearingTarget;
    }

    public double getBearingSetpointDegrees() {
        return bearingSetpointDegrees;
    }

    public double getPhysicalBearingDegrees() {
        return physicalBearingDegrees;
    }

    public double getSetpointCompensationDegrees() {
        return setpointCompensationDegrees;
    }

    public double getRemainingDegrees() {
        return remainingDegrees;
    }

    @Nullable
    public String getBlockedReason() {
        return isBlocked() ? blockedReason : null;
    }

    public String getLifecycleName() {
        return lifecycle.name();
    }

    /** Stops output but deliberately preserves the watchdog budget across topology churn. */
    public void invalidate() {
        commandedGeneratorRpm = 0.0;
        swivelMotion.reset();
        resetTrackingTargetVelocity();
        verificationTicks = 0;
        settleTicks = 0;
        setAtDestination(false);
        setStructuralMode(false);
        setLifecycle(KineticControllerLifecycle.CLOSED);
    }

    public void release() {
        commandedGeneratorRpm = 0.0;
        swivelMotion.reset();
        activeAdapter = null;
        continuousTracking = false;
        trackingToleranceDegrees = Double.POSITIVE_INFINITY;
        setStructuralMode(false);
        resetEndpointSession(false);
        setLifecycle(KineticControllerLifecycle.CLOSED);
    }

    public void read(CompoundTag parent, boolean clientPacket) {
        read(parent, false, clientPacket);
    }

    public void read(CompoundTag parent, boolean wasMoved, boolean clientPacket) {
        activeAdapter = null;
        continuousTracking = false;
        trackingToleranceDegrees = Double.POSITIVE_INFINITY;
        structuralMode = false;
        lifecycle = KineticControllerLifecycle.CLOSED;
        observedTarget = Double.NaN;
        observedRunning = false;
        commandRevision = 0;
        frame = null;
        commandedGeneratorRpm = 0.0;
        swivelMotion.reset();
        resetEndpointSession(false);

        if (!wasMoved && parent.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = parent.getCompound(NBT_KEY);
            if (tag.getInt("FrameVersion") == KineticMountFrame.CURRENT_VERSION
                    && tag.contains("FrameAssemblyId")
                    && tag.contains("FrameBearingFacing", Tag.TAG_BYTE)
                    && tag.contains("FrameControllerFacing", Tag.TAG_BYTE)) {
                try {
                    Direction bearingFacing = Direction.from3DDataValue(tag.getByte("FrameBearingFacing"));
                    Direction controllerFacing = Direction.from3DDataValue(tag.getByte("FrameControllerFacing"));
                    UUID assemblyId = tag.getUUID("FrameAssemblyId");
                    Direction initial = tag.contains("FrameCannonInitial", Tag.TAG_BYTE)
                            ? Direction.from3DDataValue(tag.getByte("FrameCannonInitial")) : null;
                    frame = new KineticMountFrame(KineticMountFrame.CURRENT_VERSION,
                            bearingFacing, controllerFacing, assemblyId, initial,
                            tag.getInt("FrameConversionSign"), tag.getDouble("FrameNeutral"));
                } catch (RuntimeException ignored) {
                    frame = null;
                }
            }
            if (clientPacket) {
                structuralMode = tag.getBoolean("TransientStructuralMode");
                atDestination = tag.getBoolean("TransientAtDestination");
                if (tag.contains("TransientLifecycle", Tag.TAG_STRING)) {
                    try {
                        lifecycle = KineticControllerLifecycle.valueOf(tag.getString("TransientLifecycle"));
                    } catch (IllegalArgumentException ignored) {
                        lifecycle = KineticControllerLifecycle.CLOSED;
                    }
                }
            }
        }
    }

    public void write(CompoundTag parent, boolean clientPacket) {
        CompoundTag tag = new CompoundTag();
        if (frame != null) {
            tag.putInt("FrameVersion", frame.version());
            tag.putByte("FrameBearingFacing", (byte) frame.bearingFacing().get3DDataValue());
            tag.putByte("FrameControllerFacing", (byte) frame.controllerFacing().get3DDataValue());
            tag.putUUID("FrameAssemblyId", frame.assemblyId());
            if (frame.cannonInitialOrientation() != null) {
                tag.putByte("FrameCannonInitial",
                        (byte) frame.cannonInitialOrientation().get3DDataValue());
            }
            tag.putInt("FrameConversionSign", frame.conversionSign());
            tag.putDouble("FrameNeutral", frame.controllerNeutralDegrees());
        }
        if (clientPacket) {
            tag.putBoolean("TransientStructuralMode", structuralMode);
            tag.putBoolean("TransientOutputConnected",
                    Math.abs(commandedGeneratorRpm) > SPEED_EPSILON);
            tag.putDouble("TransientOutputRpm", commandedGeneratorRpm);
            tag.putBoolean("TransientAtDestination", atDestination);
            tag.putString("TransientLifecycle", lifecycle.name());
        }
        parent.put(NBT_KEY, tag);
    }

    public boolean consumeSyncRequested() {
        boolean requested = syncRequested;
        syncRequested = false;
        return requested;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
