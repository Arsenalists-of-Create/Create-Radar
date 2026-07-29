package com.happysg.radar.block.controller.kinetic;

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
 * Fail-closed closed-loop control for the isolated cog generator used by a
 * kinetic cannon controller. Endpoint attachment is deliberately absent here:
 * ordinary Create propagation owns the Swivel's extra kinetic endpoint.
 */
public final class KineticControllerState {
    private static final String NBT_KEY = "KineticController";
    private static final double SPEED_EPSILON = 1.0e-5;
    private static final double STOP_RPM = 0.05;
    private static final double PROPORTIONAL_RPM_PER_DEGREE = 0.6 / 0.3;
    private static final int REVERSAL_HOLD_TICKS = 5;
    private static final int VERIFY_TIMEOUT_TICKS = 40;
    private static final int SETTLE_TICKS = 3;
    private static final int WATCHDOG_GRACE_TICKS = 20;

    private final CannonAxis axis;

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

    private double commandedGeneratorRpm;
    private double smoothedGeneratorRpm;
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
    private int watchdogTicks;
    private double watchdogLastError = Double.NaN;
    private int watchdogStallTicks;
    private boolean continuousTracking;

    private boolean syncRequested;

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
        return tick(controller.getBlockPos(), resolution, running, controllerTargetDegrees,
                toleranceDegrees, availableInputRpm, !controller.hasSource(), commandGenerator);
    }

    boolean tick(BlockPos controllerPos,
                 KineticMountAdapterResolution resolution,
                 boolean running,
                 double controllerTargetDegrees,
                 double toleranceDegrees,
                 double availableInputRpm,
                 boolean generatorIsolated,
                 DoubleConsumer commandGenerator) {
        double tolerance = Math.max(0.0, toleranceDegrees);
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
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }
        if (resolution.selection() == KineticMountAdapterResolution.Selection.AMBIGUOUS
                || !resolution.hasAdapter() || resolution.adapter() == null) {
            stopGenerator(commandGenerator);
            activeAdapter = null;
            clearFrame();
            resetEndpointSession(false);
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
            failClosed(commandGenerator, false);
            return true;
        }
        if (!generatorIsolated) {
            failClosed(commandGenerator, true);
            return true;
        }
        if (adapter.isEndpointFree() && adapter.hasSequenceContext()
                && !adapter.discardStaleSequenceContextIfFree()) {
            failClosed(commandGenerator, true);
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
            failClosed(commandGenerator, false);
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
            failClosed(commandGenerator, false);
            return true;
        }
        desiredBearingTarget = frame.bearingTargetFor(observedTarget);
        remainingDegrees = KineticAngleMath.shortestDelta(bearingTarget, desiredBearingTarget);

        observeDriveSign(bearingTarget, tolerance);
        lastBearingAngle = bearingTarget;

        if (hardBlocked) {
            stopGenerator(commandGenerator);
            setAtDestination(false);
            setLifecycle(KineticControllerLifecycle.BLOCKED);
            return true;
        }

        if (watchdogActive && !watchdogAllows(bearingTarget, physicalAngle,
                desiredBearingTarget, tolerance)) {
            failClosed(commandGenerator, true);
            return true;
        }

        if (Math.abs(remainingDegrees) <= tolerance) {
            settleAtDestination(adapter, tolerance, commandGenerator);
            return true;
        }

        settleTicks = 0;
        lastPhysicalAngle = Double.NaN;
        setAtDestination(false);

        double maximumRpm = adapter.maximumDriveRpm(availableInputRpm);
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
            failClosed(commandGenerator, false);
            return true;
        }

        if (adapter.hasSequenceContext() && adapter.isDrivenBy(controllerPos)) {
            failClosed(commandGenerator, true);
            return true;
        }
        if (!adapter.isEndpointFree() && !adapter.isDrivenBy(controllerPos)) {
            failClosed(commandGenerator, true);
            return true;
        }

        double rawRpm = clamp(remainingDegrees * PROPORTIONAL_RPM_PER_DEGREE,
                -maximumRpm, maximumRpm) * driveSign;
        if (reversalTicks > 0) {
            reversalTicks--;
            stopGenerator(commandGenerator);
            setLifecycle(KineticControllerLifecycle.REVERSING);
            return true;
        }
        if (Math.abs(commandedGeneratorRpm) > STOP_RPM
                && Math.signum(commandedGeneratorRpm) != Math.signum(rawRpm)) {
            reversalTicks = REVERSAL_HOLD_TICKS;
            stopGenerator(commandGenerator);
            setLifecycle(KineticControllerLifecycle.REVERSING);
            return true;
        }

        double smoothing = Math.abs(remainingDegrees) <= 5.0 ? 0.5 : 0.4;
        smoothedGeneratorRpm += (rawRpm - smoothedGeneratorRpm) * smoothing;
        if (Math.abs(smoothedGeneratorRpm) < STOP_RPM) {
            smoothedGeneratorRpm = 0.0;
        }

        if (!watchdogActive) {
            beginWatchdog(adapter, bearingTarget, physicalAngle, desiredBearingTarget,
                    remainingDegrees, maximumRpm);
        }
        commandGenerator(commandGenerator, smoothedGeneratorRpm);

        if (adapter.hasSequenceContext()) {
            failClosed(commandGenerator, true);
            return true;
        }
        if (!adapter.isDrivenBy(controllerPos)) {
            verificationTicks++;
            setLifecycle(KineticControllerLifecycle.VERIFYING_ENDPOINT);
            if (!adapter.isEndpointFree() || verificationTicks >= VERIFY_TIMEOUT_TICKS) {
                failClosed(commandGenerator, true);
            }
            return true;
        }

        verificationTicks = 0;
        setLifecycle(KineticControllerLifecycle.MOVING);
        return true;
    }

    public void onTargetChanged(boolean running, double targetDegrees, double toleranceDegrees) {
        double tolerance = Math.max(0.0, toleranceDegrees);
        boolean materialTargetChange = !Double.isFinite(observedTarget)
                || !Double.isFinite(targetDegrees)
                || Math.abs(KineticAngleMath.shortestDelta(observedTarget, targetDegrees)) > tolerance;
        boolean runningChange = observedRunning != running;
        if (materialTargetChange) {
            observedTarget = targetDegrees;
        }
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
        }
    }

    /**
     * Selects the progress-based watchdog used by continuously changing debug
     * targets. The cumulative travel bound remains active in this mode.
     */
    public void beginContinuousTracking() {
        if (continuousTracking) {
            return;
        }
        continuousTracking = true;
        watchdogActive = false;
        watchdogStallTicks = 0;
        hardBlocked = false;
    }

    /**
     * Ends a moving-target session without discarding the verified mount
     * frame. The next ordinary set-angle command gets a fresh finite watchdog.
     */
    public void endContinuousTracking() {
        if (!continuousTracking) {
            return;
        }
        continuousTracking = false;
        watchdogActive = false;
        watchdogStallTicks = 0;
        hardBlocked = false;
    }

    boolean isContinuousTracking() {
        return continuousTracking;
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
        double desired = frame == null ? Double.NaN : frame.bearingTargetFor(controllerTargetDegrees);
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
        double desired = frame.bearingTargetFor(controllerTargetDegrees);
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

    private void settleAtDestination(KineticMountAdapter adapter, double tolerance,
                                     DoubleConsumer commandGenerator) {
        stopGenerator(commandGenerator);
        watchdogActive = false;

        double physical = adapter.getPhysicalAngleDegrees();
        if (!Double.isFinite(physical) || adapter.hasSequenceContext()) {
            settleTicks = 0;
            lastPhysicalAngle = Double.NaN;
            setAtDestination(false);
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
        if (!Double.isFinite(lastBearingAngle) || Math.abs(commandedGeneratorRpm) <= STOP_RPM) {
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
            smoothedGeneratorRpm = 0.0;
            reversalTicks = REVERSAL_HOLD_TICKS;
            syncRequested = true;
        }
    }

    private void beginWatchdog(KineticMountAdapter adapter, double bearingAngle,
                               double physicalAngle, double destination,
                               double remaining, double maximumRpm) {
        watchdogActive = true;
        watchdogInitialError = Math.max(Math.abs(remaining),
                Math.abs(KineticAngleMath.shortestDelta(physicalAngle, destination)));
        watchdogDestinationTravel = 0.0;
        watchdogBearingTravel = 0.0;
        watchdogPhysicalTravel = 0.0;
        watchdogLastBearing = bearingAngle;
        watchdogLastPhysical = physicalAngle;
        watchdogLastDestination = destination;
        watchdogMaxStep = adapter.effectiveDegreesPerTick(maximumRpm);
        watchdogTicks = 0;
        watchdogLastError = Math.abs(KineticAngleMath.shortestDelta(
                bearingAngle, destination));
        watchdogStallTicks = 0;
    }

    private boolean watchdogAllows(double bearingAngle, double physicalAngle,
                                   double destination, double tolerance) {
        if (!watchdogActive) {
            return false;
        }
        double bearingStep = KineticAngleMath.shortestDelta(
                watchdogLastBearing, bearingAngle);
        double destinationStep = KineticAngleMath.shortestDelta(
                watchdogLastDestination, destination);
        double physicalStep = KineticAngleMath.shortestDelta(
                watchdogLastPhysical, physicalAngle);
        double priorRemaining = KineticAngleMath.shortestDelta(
                watchdogLastBearing, watchdogLastDestination);
        watchdogBearingTravel += Math.abs(bearingStep);
        watchdogDestinationTravel += Math.abs(destinationStep);
        watchdogPhysicalTravel += Math.abs(physicalStep);
        watchdogLastBearing = bearingAngle;
        watchdogLastPhysical = physicalAngle;
        watchdogLastDestination = destination;
        watchdogTicks++;

        double allowance = watchdogInitialError + watchdogDestinationTravel
                + 2.0 * Math.max(watchdogMaxStep, tolerance);
        boolean withinTravelBound = watchdogBearingTravel <= allowance + 1.0e-6
                && watchdogPhysicalTravel <= allowance + 1.0e-6;
        if (continuousTracking) {
            double progressThreshold = Math.max(1.0e-4, tolerance * 0.05);
            double currentError = Math.abs(KineticAngleMath.shortestDelta(
                    bearingAngle, destination));
            boolean movedTowardPriorTarget = Math.abs(bearingStep) > progressThreshold
                    && Math.signum(bearingStep) == Math.signum(priorRemaining);
            boolean reducedError = Double.isFinite(watchdogLastError)
                    && currentError + progressThreshold < watchdogLastError;
            watchdogStallTicks = movedTowardPriorTarget || reducedError
                    ? 0 : watchdogStallTicks + 1;
            watchdogLastError = currentError;
            int stallLimit = watchdogMaxStep <= SPEED_EPSILON ? WATCHDOG_GRACE_TICKS
                    : (int) Math.ceil(Math.max(currentError, watchdogMaxStep)
                    / watchdogMaxStep) + WATCHDOG_GRACE_TICKS;
            return withinTravelBound && watchdogStallTicks <= stallLimit;
        }

        int duration = watchdogMaxStep <= SPEED_EPSILON ? WATCHDOG_GRACE_TICKS
                : (int) Math.ceil(allowance / watchdogMaxStep) + WATCHDOG_GRACE_TICKS;
        return withinTravelBound && watchdogTicks <= duration;
    }

    private void failClosed(DoubleConsumer commandGenerator, boolean blockUntilCommandChange) {
        stopGenerator(commandGenerator);
        hardBlocked |= blockUntilCommandChange;
        setAtDestination(false);
        setLifecycle(KineticControllerLifecycle.BLOCKED);
    }

    private void commandGenerator(DoubleConsumer command, double rpm) {
        commandedGeneratorRpm = Double.isFinite(rpm) ? rpm : 0.0;
        command.accept(commandedGeneratorRpm);
    }

    private void stopGenerator(DoubleConsumer command) {
        boolean changed = Math.abs(commandedGeneratorRpm) > SPEED_EPSILON
                || Math.abs(smoothedGeneratorRpm) > SPEED_EPSILON;
        commandedGeneratorRpm = 0.0;
        smoothedGeneratorRpm = 0.0;
        verificationTicks = 0;
        command.accept(0.0);
        if (changed) {
            syncRequested = true;
        }
    }

    private void resetEndpointSession(boolean resetFrame) {
        lockedLastTick = false;
        settleTicks = 0;
        reversalTicks = 0;
        verificationTicks = 0;
        desiredBearingTarget = Double.NaN;
        remainingDegrees = Double.NaN;
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

    /** Stops output but deliberately preserves the watchdog budget across topology churn. */
    public void invalidate() {
        commandedGeneratorRpm = 0.0;
        smoothedGeneratorRpm = 0.0;
        verificationTicks = 0;
        settleTicks = 0;
        setAtDestination(false);
        setStructuralMode(false);
        setLifecycle(KineticControllerLifecycle.CLOSED);
    }

    public void release() {
        commandedGeneratorRpm = 0.0;
        smoothedGeneratorRpm = 0.0;
        activeAdapter = null;
        continuousTracking = false;
        setStructuralMode(false);
        resetEndpointSession(false);
        setLifecycle(KineticControllerLifecycle.CLOSED);
    }

    public void read(CompoundTag parent, boolean clientPacket) {
        read(parent, false, clientPacket);
    }

    public void read(CompoundTag parent, boolean wasMoved, boolean clientPacket) {
        activeAdapter = null;
        structuralMode = false;
        lifecycle = KineticControllerLifecycle.CLOSED;
        observedTarget = Double.NaN;
        observedRunning = false;
        commandRevision = 0;
        frame = null;
        commandedGeneratorRpm = 0.0;
        smoothedGeneratorRpm = 0.0;
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
