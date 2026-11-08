package com.happysg.radar.block.controller.kinetic;

import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.block.controller.kinetic.ControllerMotionProfile;
import com.happysg.radar.debug.MotionTraceRecorder;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

public final class CbcShaftDrive {
    private static final double RPM_PER_DEGREE_PER_TICK = 80.0 / 3.0;
    private static final double EPSILON = 1.0e-4;
    private static final int MAX_SOURCE_HOPS = 64;
    private final CannonAxis axis;
    private static final double BRAKE_LOOKAHEAD_TICKS = 1.5;

    private final ControllerMotionProfile.State motionProfile = new ControllerMotionProfile.State();

    private double filteredActualRate;
    private double filteredDesiredRate;

    private KineticBlockEntity endpoint;
    private double speedRatio = Double.NaN;
    private double lastCommandedRpm;
    private double lastActualAngle = Double.NaN;
    private double lastDesiredAngle = Double.NaN;
    private long lastSampleTick = Long.MIN_VALUE;
    private long nextProbeTick = Long.MIN_VALUE;
    private int probeTicks;
    private int reverseHoldTicks;
    private int pendingFineReverseSign;
    private long pendingFineReverseTick = Long.MIN_VALUE;
    private boolean hadDrive;
    private List<ConfirmedDriveNode> confirmedDrivePath = List.of();
    private Level traceLevel;
    private BlockPos traceControllerPos;
    private BlockPos traceMountPos;

    public CbcShaftDrive(CannonAxis axis) {
        this.axis = axis;
    }

    private void clearFineReversal() {
        pendingFineReverseSign = 0;
        pendingFineReverseTick = Long.MIN_VALUE;
    }

    public void clear() {
        endpoint = null;
        speedRatio = Double.NaN;
        lastCommandedRpm = 0;
        lastActualAngle = Double.NaN;
        lastDesiredAngle = Double.NaN;
        lastSampleTick = Long.MIN_VALUE;
        nextProbeTick = Long.MIN_VALUE;
        probeTicks = 0;
        reverseHoldTicks = 0;
        clearFineReversal();
        hadDrive = false;
        confirmedDrivePath = List.of();
        motionProfile.reset();
        filteredActualRate = 0.0;
        filteredDesiredRate = 0.0;
    }

    public void reset(DoubleConsumer command) {
        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.reset",
                    traceLevel,
                    traceControllerPos,
                    "mount", traceMountPos,
                    "axis", axis,
                    "previousRpm", lastCommandedRpm,
                    "previousRatio", speedRatio,
                    "reverseHoldTicks", reverseHoldTicks,
                    "hadDrive", hadDrive
            );
        }

        command.accept(0.0);
        clear();
    }

    public void tick(
            KineticBlockEntity controller,
            CannonMountContext mount,
            boolean running,
            double requestedAngle,
            double inputRpm,
            Direction outputSide,
            ControllerAngleDelta yawPath,
            DoubleConsumer command
    ) {

        traceLevel = controller.getLevel();
        traceControllerPos = controller.getBlockPos();
        traceMountPos = mount == null ? null : mount.getBlockPos();

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.tick",
                    traceLevel,
                    traceControllerPos,
                    "mount", traceMountPos,
                    "axis", axis,
                    "running", running,
                    "requestedAngle", requestedAngle,
                    "inputRpm", inputRpm,
                    "outputSide", outputSide,
                    "yawPath", yawPath,
                    "lastCommandedRpm", lastCommandedRpm,
                    "speedRatio", speedRatio,
                    "reverseHoldTicks", reverseHoldTicks,
                    "probeTicks", probeTicks,
                    "hadDrive", hadDrive,
                    "endpointPresent", endpoint != null
            );
        }

        if (controller.getLevel() == null || !(mount.blockEntity() instanceof CannonMountBlockEntity cbc) || !mount.isCurrent()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.rejected",
                        traceLevel,
                        traceControllerPos,
                        "mount", traceMountPos,
                        "axis", axis,
                        "reason", "INVALID_MOUNT_OR_LEVEL",
                        "levelPresent", controller.getLevel() != null,
                        "mountCurrent", mount.isCurrent()
                );
            }

            reset(command);
            return;
        }

        KineticBlockEntity selected = axis == CannonAxis.YAW ? cbc.getYawInterface() : cbc.getPitchInterface();
        if (endpoint != selected) {
            clear();
            endpoint = selected;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();

        if (!running
                || !Double.isFinite(requestedAngle)
                || !Double.isFinite(inputRpm)
                || Math.abs(inputRpm) < EPSILON
                || !mount.hasAssembledCannon()
                || contraption == null
                || contraption.isStalled()
                || !(contraption.getContraption()
                instanceof AbstractMountedCannonContraption cannon)) {

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.inactive",
                        traceLevel,
                        traceControllerPos,
                        "mount", traceMountPos,
                        "axis", axis,
                        "running", running,
                        "requestedAngle", requestedAngle,
                        "inputRpm", inputRpm,
                        "cannonAssembled", mount.hasAssembledCannon(),
                        "contraptionPresent", contraption != null,
                        "contraptionStalled",
                        contraption != null && contraption.isStalled(),
                        "lastCommandedRpm", lastCommandedRpm
                );
            }

            motionProfile.reset();
            filteredActualRate = 0.0;
            filteredDesiredRate = 0.0;
            reverseHoldTicks = 0;
            clearFineReversal();
            lastSampleTick = Long.MIN_VALUE;
            lastActualAngle = Double.NaN;
            lastDesiredAngle = Double.NaN;

            send(command, 0.0);
            return;
        }

        double actual;
        double desired;
        double error;

        if (axis == CannonAxis.YAW) {
            actual = KineticAngleMath.wrap360(contraption.yaw);
            desired = KineticAngleMath.wrap360(requestedAngle);
            error = (yawPath == null ? ControllerAngleDelta.SHORTEST : yawPath).remainingDegrees(actual, desired);
        } else {
            int pitchSign = -cannon.initialOrientation().getStepX() + cannon.initialOrientation().getStepZ();
            if (Math.abs(pitchSign) != 1) {
                send(command, 0.0);
                return;
            }

            actual = contraption.pitch;
            desired = -pitchSign * requestedAngle;
            error = desired - actual;
        }

        if (!Double.isFinite(actual) || !Double.isFinite(desired) || !Double.isFinite(error)) {
            send(command, 0.0);
            return;
        }

        Level level = controller.getLevel();
        long tick = level.getGameTime();

        boolean consecutive = lastSampleTick == tick - 1;
        double measuredActualRate = consecutive && Double.isFinite(lastActualAngle) ? delta(lastActualAngle, actual) : 0.0;
        double measuredDesiredRate = consecutive && Double.isFinite(lastDesiredAngle) ? delta(lastDesiredAngle, desired) : 0.0;

        lastSampleTick = tick;
        lastActualAngle = actual;
        lastDesiredAngle = desired;

        if (!consecutive || !Double.isFinite(measuredActualRate) || Math.abs(measuredActualRate) > ControllerMotionProfile.MAX_PHYSICAL_SAMPLE_STEP_DEGREES) {
            filteredActualRate = 0.0;
        } else {
            filteredActualRate = ControllerMotionProfile.filteredRate(filteredActualRate, measuredActualRate, ControllerMotionProfile.PHYSICAL_VELOCITY_SAMPLE_WEIGHT);
        }

        if (!consecutive || !Double.isFinite(measuredDesiredRate) || Math.abs(measuredDesiredRate) > ControllerMotionProfile.MAX_TARGET_SAMPLE_STEP_DEGREES) {
            filteredDesiredRate = 0.0;
        } else {
            filteredDesiredRate = ControllerMotionProfile.filteredRate(filteredDesiredRate, measuredDesiredRate, ControllerMotionProfile.TARGET_VELOCITY_SAMPLE_WEIGHT);
        }

        double actualRate = clamp(filteredActualRate, -5.0, 5.0);
        double desiredRate = clamp(filteredDesiredRate, -5.0, 5.0);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.feedback",
                    level,
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "axis", axis,
                    "actualAngle", actual,
                    "desiredAngle", desired,
                    "angleError", error,
                    "consecutiveSample", consecutive,
                    "measuredActualRate", measuredActualRate,
                    "measuredDesiredRate", measuredDesiredRate,
                    "filteredActualRate", filteredActualRate,
                    "filteredDesiredRate", filteredDesiredRate,
                    "actualRate", actualRate,
                    "desiredRate", desiredRate,
                    "lastSampleTick", lastSampleTick,
                    "inputRpm", inputRpm
            );
        }

        if (Math.abs(error) < 0.06 && Math.abs(desiredRate) < 0.03 && Math.abs(actualRate) < 0.06) {
            clearFineReversal();
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.settled",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "error", error,
                        "actualRate", actualRate,
                        "desiredRate", desiredRate,
                        "previousRpm", lastCommandedRpm
                );
            }

            motionProfile.recordAppliedStep(0.0);
            reverseHoldTicks = 0;

            send(command, 0.0);
            return;
        }

        double maximumRpm = Math.min(256.0, Math.abs(inputRpm));
        boolean physicallyDriven = selected != null && sourcedBy(selected, controller);

        if (physicallyDriven && confirmedDrivePath.isEmpty()) {
            rememberConfirmedDrivePath(selected, controller);
        }

        boolean retainedStoppedDrive = !physicallyDriven && hasConfirmedStoppedDrivePath(selected, controller, outputSide);
        boolean driven = physicallyDriven || retainedStoppedDrive;

        if (!physicallyDriven
                && !retainedStoppedDrive
                && confirmedDrivePath.size() >= 2
                && Math.abs(lastCommandedRpm) <= EPSILON
                && MotionTraceRecorder.isEnabled()) {

            boolean nodesPresent = true;
            boolean statesMatch = true;
            boolean connectionsIntact = true;
            boolean nodesStopped = true;
            boolean foreignNodeSource = false;

            for (int i = 0; i < confirmedDrivePath.size(); i++) {
                ConfirmedDriveNode recorded = confirmedDrivePath.get(i);
                KineticBlockEntity node = recorded.entity();

                boolean present = isCurrentDriveNode(level, node);
                nodesPresent &= present;

                if (!present) {
                    continue;
                }

                statesMatch &= recorded.state().getBlock() == node.getBlockState().getBlock();
                nodesStopped &= Math.abs(node.getSpeed()) <= EPSILON && Math.abs(node.getTheoreticalSpeed()) <= EPSILON && !node.isOverStressed();

                if (node != controller) {
                    foreignNodeSource |= node.isSource();
                }

                if (i + 1 < confirmedDrivePath.size()) {
                    KineticBlockEntity upstream = confirmedDrivePath.get(i + 1).entity();
                    connectionsIntact &= RotationPropagator.isConnected(upstream, node);
                    foreignNodeSource |= node.hasSource() && (node.source == null || !node.source.equals(upstream.getBlockPos()));
                }
            }

            MotionTraceRecorder.record("cbc_shaft.retention_diagnostic", level, controller.getBlockPos(), "mount", mount.getBlockPos(), "axis", axis, "nodesPresent", nodesPresent, "statesMatch", statesMatch, "connectionsIntact", connectionsIntact, "nodesStopped", nodesStopped, "foreignNodeSource", foreignNodeSource, "foreignOutputSource", hasForeignOutputSource(controller, outputSide), "controllerHasSource", controller.hasSource(), "controllerTheoreticalSpeed", controller.getTheoreticalSpeed(), "endpointIsSource", selected != null && selected.isSource(), "firstNodeMatches", confirmedDrivePath.getFirst().entity() == selected, "lastNodeMatches", confirmedDrivePath.getLast().entity() == controller, "lastCommandedRpm", lastCommandedRpm, "speedRatio", speedRatio);
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.connection",
                    level,
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "axis", axis,
                    "driven", driven,
                    "physicallyDriven", physicallyDriven,
                    "retainedStoppedDrive", retainedStoppedDrive,
                    "confirmedPathLength", confirmedDrivePath.size(),
                    "endpointPresent", selected != null,
                    "endpointPosition",
                    selected == null ? null : selected.getBlockPos(),
                    "controllerSpeed", controller.getSpeed(),
                    "endpointSpeed",
                    selected == null ? null : selected.getSpeed(),
                    "controllerOverstressed", controller.isOverStressed(),
                    "endpointOverstressed",
                    selected != null && selected.isOverStressed(),
                    "speedRatio", speedRatio,
                    "maximumRpm", maximumRpm,
                    "hadDrive", hadDrive
            );
        }

        if (!driven) {
            clearFineReversal();
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.drive_loss_detail",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "commandedRpm", lastCommandedRpm,
                        "generatedRpm", controller.getGeneratedSpeed(),
                        "controllerSpeed", controller.getSpeed(),
                        "controllerTheoreticalSpeed",
                        controller.getTheoreticalSpeed(),
                        "endpointSpeed",
                        selected == null ? null : selected.getSpeed(),
                        "endpointTheoreticalSpeed",
                        selected == null ? null : selected.getTheoreticalSpeed(),
                        "controllerHasSource", controller.hasSource(),
                        "endpointHasSource",
                        selected != null && selected.hasSource(),
                        "controllerHasNetwork", controller.hasNetwork(),
                        "endpointHasNetwork",
                        selected != null && selected.hasNetwork(),
                        "directConnectionIntact",
                        selected != null && RotationPropagator.isConnected(controller, selected),
                        "controllerRegistered",
                        level.getBlockEntity(controller.getBlockPos()) == controller,
                        "endpointOwnedByMount",
                        selected != null
                                && level.getBlockEntity(selected.getBlockPos()) instanceof CannonMountBlockEntity parent
                                && (parent.getYawInterface() == selected || parent.getPitchInterface() == selected),
                        "cachedPathLength", confirmedDrivePath.size(),
                        "hadDrive", hadDrive,
                        "reverseHoldTicks", reverseHoldTicks,
                        "probeTicks", probeTicks,
                        "speedRatio", speedRatio,
                        "controllerOverstressed", controller.isOverStressed(),
                        "endpointOverstressed",
                        selected != null && selected.isOverStressed(),
                        "foreignOutputSource",
                        hasForeignOutputSource(controller, outputSide),
                        "endpointRemoved",
                        selected != null && selected.isRemoved(),
                        "endpointLevelMatches",
                        selected != null && selected.getLevel() == level,
                        "endpointCurrent",
                        isCurrentDriveNode(level, selected),
                        "controllerCurrent",
                        isCurrentDriveNode(level, controller),
                        "lastCommandedRpm", lastCommandedRpm
                );
            }

            confirmedDrivePath = List.of();
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record("cbc_shaft.drive_missing", level, controller.getBlockPos(), "mount", mount.getBlockPos(), "axis", axis, "error", error, "endpointPresent", selected != null, "endpointHasSource", selected != null && selected.hasSource(), "endpointIsSource", selected != null && selected.isSource(), "probeTicks", probeTicks, "nextProbeTick", nextProbeTick, "previouslyDriven", hadDrive);
            }

            if (reverseHoldTicks > 0) {
                if (MotionTraceRecorder.isEnabled()) {
                    MotionTraceRecorder.record("cbc_shaft.reversal_hold", level, controller.getBlockPos(), "mount", mount.getBlockPos(), "axis", axis, "remainingHoldTicks", reverseHoldTicks, "error", error, "reason", "TEMPORARY_DRIVE_LOSS");
                }

                reverseHoldTicks--;
                motionProfile.recordAppliedStep(0.0);
                send(command, 0.0);
                return;
            }

            motionProfile.reset();
            filteredActualRate = 0.0;
            filteredDesiredRate = 0.0;
            reverseHoldTicks = 0;

            if (hadDrive) {
                nextProbeTick = tick;
                hadDrive = false;
            }

            if ((selected != null && (selected.hasSource() || selected.isSource())) || hasForeignOutputSource(controller, outputSide)) {
                send(command, 0.0);
                return;
            }

            if (tick >= nextProbeTick) {
                probeTicks = 10;
                nextProbeTick = tick + 40;
            }

            if (probeTicks-- > 0) {
                double direction = Math.signum(error) * (axis == CannonAxis.YAW ? -1.0 : 1.0);
                double probeRpm = Math.min(8.0, maximumRpm);

                if (Double.isFinite(speedRatio) && Math.abs(speedRatio) > EPSILON) {
                    double degreesPerRpm = Math.abs(speedRatio) / RPM_PER_DEGREE_PER_TICK;

                    if (degreesPerRpm > EPSILON) {
                        double maxProbeRpm = Math.abs(error) / (2.0 * degreesPerRpm);
                        probeRpm = Math.min(probeRpm, Math.floor(maxProbeRpm * 2.0) / 2.0);
                    }
                }

                send(command, direction * probeRpm);
            } else {
                send(command, 0.0);
            }

            return;
        }

        hadDrive = true;

        if (selected.isOverStressed() || controller.isOverStressed()) {
            send(command, 0.0);
            return;
        }

        double motorRpm = controller.getSpeed();
        double endRpm = selected.getSpeed();

        if (Math.abs(motorRpm) > EPSILON && Math.abs(endRpm) > EPSILON) {
            double observedRatio = endRpm / motorRpm;

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.gearing",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "motorRpm", motorRpm,
                        "endpointRpm", endRpm,
                        "observedRatio", observedRatio,
                        "previousRatio", speedRatio,
                        "ratioAccepted",
                        Double.isFinite(observedRatio) && Math.abs(observedRatio) >= 0.015625 && Math.abs(observedRatio) <= 64.0
                );
            }

            if (Double.isFinite(observedRatio) && Math.abs(observedRatio) >= 0.015625 && Math.abs(observedRatio) <= 64.0) {
                speedRatio = observedRatio;
            }
        }

        if (!Double.isFinite(speedRatio)) {
            send(command, (axis == CannonAxis.YAW ? -1.0 : 1.0) * Math.signum(error) * Math.min(8.0, maximumRpm));
            return;
        }

        double physicalDegreesPerMotorRpm = (axis == CannonAxis.YAW ? -1.0 : 1.0) * speedRatio / RPM_PER_DEGREE_PER_TICK;
        if (!Double.isFinite(physicalDegreesPerMotorRpm) || Math.abs(physicalDegreesPerMotorRpm) <= EPSILON) {
            motionProfile.reset();
            send(command, 0.0);
            return;
        }


        double maximumPhysicalStep = Math.min(ControllerMotionProfile.maxSpeedForRpm(maximumRpm), Math.abs(physicalDegreesPerMotorRpm) * maximumRpm);
        if (!Double.isFinite(maximumPhysicalStep) || maximumPhysicalStep <= EPSILON) {
            motionProfile.reset();
            send(command, 0.0);
            return;
        }

        if (consecutive) {
            motionProfile.recordAppliedStep(clamp(actualRate, -maximumPhysicalStep, maximumPhysicalStep));
        }

        double profileRpm = maximumPhysicalStep * 12.0;
        double trackingAcceleration = axis == CannonAxis.YAW && Math.abs(error) > 3.0 && Math.abs(desiredRate) >= 0.15 && error * desiredRate > 0.0 ? 1.5 : 1.0;
        double plannedStep = motionProfile.nextTrackingStep(error, profileRpm, desired, tick, axis == CannonAxis.YAW, trackingAcceleration);
        double unbrakedStep = plannedStep;

        double direction = Math.signum(error);
        double closingVelocity = Math.max(0.0, (actualRate - desiredRate) * direction);

        if (closingVelocity > 0.06 && Math.abs(desiredRate) < 0.15) {
            double projectedRemaining = ControllerMotionProfile.projectedRemaining(Math.abs(error), closingVelocity, BRAKE_LOOKAHEAD_TICKS);
            if (Math.abs(plannedStep) > projectedRemaining) {
                plannedStep = Math.copySign(projectedRemaining, plannedStep);
                motionProfile.recordAppliedStep(plannedStep);
            }
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.smoothing",
                    level,
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "axis", axis,
                    "actualAngle", actual,
                    "desiredAngle", desired,
                    "error", error,
                    "actualRate", actualRate,
                    "targetRate", desiredRate,
                    "closingVelocity", closingVelocity,
                    "profileRpm", profileRpm,
                    "maximumPhysicalStep", maximumPhysicalStep,
                    "unbrakedStep", unbrakedStep,
                    "finalPlannedStep", plannedStep,
                    "brakingLimited",
                    Math.abs(plannedStep) < Math.abs(unbrakedStep),
                    "physicalDegreesPerMotorRpm", physicalDegreesPerMotorRpm,
                    "speedRatio", speedRatio,
                    "maximumRpm", maximumRpm,
                    "reverseHoldTicks", reverseHoldTicks,
                    "profileVelocity",
                    motionProfile.traceVelocityDegPerTick(),
                    "profileTargetRate",
                    motionProfile.traceTargetRateDegPerTick()
            );
        }

        double rpm = clamp(plannedStep / physicalDegreesPerMotorRpm, -maximumRpm, maximumRpm);
        if (Math.abs(rpm) < 0.5) {
            rpm = 0.0;
        }

        rpm = Math.rint(rpm * 2.0) / 2.0;

        if (pendingFineReverseSign != 0) {
            boolean confirmed = pendingFineReverseTick == tick - 1 && Math.abs(rpm) > EPSILON && (int) Math.signum(rpm) == pendingFineReverseSign;

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        confirmed ? "cbc_shaft.fine_reverse_confirmed" : "cbc_shaft.fine_reverse_cancelled",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "requestedRpm", rpm,
                        "angleError", error,
                        "desiredRate", desiredRate
                );
            }

            clearFineReversal();

            if (confirmed) {
                reverseHoldTicks = Math.max(0, ControllerMotionProfile.REVERSAL_HOLD_TICKS - 2);
                motionProfile.recordAppliedStep(0.0);
                send(command, 0.0);
                return;
            }
        }

        if (reverseHoldTicks > 0) {
            int completedHoldTicks = ControllerMotionProfile.REVERSAL_HOLD_TICKS - reverseHoldTicks;

            boolean movingTarget = Math.abs(desiredRate) >= 0.10;
            boolean physicallySlowed = Math.abs(actualRate) <= ControllerMotionProfile.REVERSAL_RELEASE_SPEED_DEG_PER_TICK;

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.reversal",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "completedHoldTicks", completedHoldTicks,
                        "remainingHoldTicks", reverseHoldTicks,
                        "movingTarget", movingTarget,
                        "physicallySlowed", physicallySlowed,
                        "actualRate", actualRate,
                        "desiredRate", desiredRate,
                        "requestedRpm", rpm,
                        "previousRpm", lastCommandedRpm
                );
            }

            if (movingTarget && completedHoldTicks >= 1 && physicallySlowed) {
                reverseHoldTicks = 0;
            } else {
                reverseHoldTicks--;
                motionProfile.recordAppliedStep(0.0);
                send(command, 0.0);
                return;
            }
        }

        if (Math.abs(lastCommandedRpm) > EPSILON && Math.abs(rpm) > EPSILON && Math.signum(lastCommandedRpm) != Math.signum(rpm)) {
            if (Math.abs(error) <= 0.5 && Math.abs(desiredRate) < 0.15) {
                pendingFineReverseSign = (int) Math.signum(rpm);
                pendingFineReverseTick = tick;

                if (MotionTraceRecorder.isEnabled()) {
                    MotionTraceRecorder.record(
                            "cbc_shaft.fine_reverse_deferred",
                            level,
                            controller.getBlockPos(),
                            "mount", mount.getBlockPos(),
                            "axis", axis,
                            "previousRpm", lastCommandedRpm,
                            "requestedRpm", rpm,
                            "angleError", error,
                            "actualRate", actualRate,
                            "desiredRate", desiredRate
                    );
                }

                motionProfile.recordAppliedStep(0.0);
                send(command, 0.0);
                return;
            }

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_shaft.reversal_start",
                        level,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "axis", axis,
                        "previousRpm", lastCommandedRpm,
                        "requestedRpm", rpm,
                        "actualRate", actualRate,
                        "desiredRate", desiredRate,
                        "angleError", error,
                        "holdTicks", ControllerMotionProfile.REVERSAL_HOLD_TICKS
                );
            }

            reverseHoldTicks = Math.max(0, ControllerMotionProfile.REVERSAL_HOLD_TICKS - 1);
            motionProfile.recordAppliedStep(0.0);
            send(command, 0.0);
            return;
        }

        double maxChange = Math.max(8.0, maximumRpm * 0.20);
        double rpmBeforeRampLimit = rpm;
        rpm = clamp(rpm, lastCommandedRpm - maxChange, lastCommandedRpm + maxChange);
        rpm = Math.rint(rpm * 2.0) / 2.0;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.final_command",
                    level,
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "axis", axis,
                    "actualAngle", actual,
                    "desiredAngle", desired,
                    "angleError", error,
                    "plannedStep", plannedStep,
                    "rpmBeforeRampLimit", rpmBeforeRampLimit,
                    "maxRpmChange", maxChange,
                    "previousRpm", lastCommandedRpm,
                    "commandedRpm", rpm,
                    "maximumRpm", maximumRpm,
                    "speedRatio", speedRatio
            );
        }

        send(command, rpm);
    }

    private double delta(double earlier, double later) {
        return axis == CannonAxis.YAW ? KineticAngleMath.shortestDelta(earlier, later) : later - earlier;
    }

    private void send(DoubleConsumer command, double rpm) {
        lastCommandedRpm = Double.isFinite(rpm) ? rpm : 0.0;
        command.accept(lastCommandedRpm);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_shaft.output",
                    traceLevel,
                    traceControllerPos,
                    "mount", traceMountPos,
                    "axis", axis,
                    "commandedRpm", lastCommandedRpm,
                    "speedRatio", speedRatio,
                    "reverseHoldTicks", reverseHoldTicks,
                    "probeTicks", probeTicks,
                    "hadDrive", hadDrive
            );
        }
    }


    private static boolean isCurrentDriveNode(Level level, KineticBlockEntity node) {
        if (level == null || node == null || !level.isLoaded(node.getBlockPos())) {
            return false;
        }

        BlockEntity worldEntity = level.getBlockEntity(node.getBlockPos());
        if (node instanceof CannonMountInterfaceBlockEntity) {
            return worldEntity instanceof CannonMountBlockEntity parent
                    && !parent.isRemoved()
                    && parent.getLevel() == level
                    && (parent.getYawInterface() == node || parent.getPitchInterface() == node);
        }

        return !node.isRemoved() && node.getLevel() == level && worldEntity == node;
    }


    private record ConfirmedDriveNode(KineticBlockEntity entity, BlockState state) {}

    private void rememberConfirmedDrivePath(KineticBlockEntity selected, KineticBlockEntity controller) {
        Level level = controller.getLevel();

        if (level == null || selected == controller || selected.getLevel() != level) {
            return;
        }

        List<ConfirmedDriveNode> path = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        KineticBlockEntity node = selected;

        for (int hop = 0; hop < MAX_SOURCE_HOPS; hop++) {
            if (!visited.add(node.getBlockPos())
                    || !node.hasSource()
                    || node.source == null) {
                return;
            }

            path.add(new ConfirmedDriveNode(
                    node, node.getBlockState()));

            BlockPos sourcePos = node.source;

            if (!level.isLoaded(sourcePos)) {
                return;
            }

            BlockEntity upstream =
                    level.getBlockEntity(sourcePos);

            if (!(upstream instanceof KineticBlockEntity prev)
                    || !RotationPropagator.isConnected(prev, node)) {
                return;
            }

            if (prev == controller) {
                path.add(new ConfirmedDriveNode(
                        controller, controller.getBlockState()));

                confirmedDrivePath = List.copyOf(path);
                return;
            }

            node = prev;
        }
    }

    private boolean hasConfirmedStoppedDrivePath(KineticBlockEntity selected, KineticBlockEntity controller, Direction outputSide) {
        Level level = controller.getLevel();

        if (level == null
                || selected == null
                || !hadDrive
                || Math.abs(lastCommandedRpm) > EPSILON
                || !Double.isFinite(speedRatio)
                || Math.abs(speedRatio) <= EPSILON
                || confirmedDrivePath.size() < 2
                || confirmedDrivePath.getFirst().entity() != selected
                || confirmedDrivePath.getLast().entity() != controller
                || controller.hasSource()
                || controller.isOverStressed()
                || selected.isOverStressed()
                || Math.abs(controller.getSpeed()) > EPSILON
                || Math.abs(controller.getTheoreticalSpeed()) > EPSILON
                || hasForeignOutputSource(controller, outputSide)) {
            return false;
        }

        for (int i = 0; i < confirmedDrivePath.size(); i++) {
            ConfirmedDriveNode recorded = confirmedDrivePath.get(i);

            KineticBlockEntity node = recorded.entity();
            if (!isCurrentDriveNode(level, node) || recorded.state().getBlock() != node.getBlockState().getBlock()) {
                return false;
            }

            if (node != controller) {
                if (node.isSource() || node.isOverStressed() || Math.abs(node.getSpeed()) > EPSILON || Math.abs(node.getTheoreticalSpeed()) > EPSILON) {
                    return false;
                }
            }

            if (i + 1 < confirmedDrivePath.size()) {
                KineticBlockEntity upstream = confirmedDrivePath.get(i + 1).entity();

                if (node.hasSource() && (node.source == null || !node.source.equals(upstream.getBlockPos()))) {
                    return false;
                }

                if (!RotationPropagator.isConnected(upstream, node)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean hasForeignOutputSource(KineticBlockEntity controller, Direction outputSide) {
        Level level = controller.getLevel();
        BlockPos first = controller.getBlockPos().relative(outputSide);
        if (!level.isLoaded(first)) {
            return true;
        }

        BlockEntity neighbour = level.getBlockEntity(first);
        return neighbour instanceof KineticBlockEntity kinetic && kinetic != controller && (kinetic.hasSource() || kinetic.isSource()) && !sourcedBy(kinetic, controller);
    }

    public static boolean sourcedBy(KineticBlockEntity endpoint, KineticBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null || endpoint == controller || endpoint.getLevel() != level) {
            return false;
        }

        if (!controller.hasSource()
                && Math.abs(controller.getTheoreticalSpeed()) > EPSILON
                && controller.hasNetwork()
                && endpoint.hasNetwork()
                && controller.getOrCreateNetwork() == endpoint.getOrCreateNetwork()) {
            return true;
        }

        Set<BlockPos> visited = new HashSet<>();
        KineticBlockEntity node = endpoint;

        for (int hop = 0; hop < MAX_SOURCE_HOPS; hop++) {

            if (!visited.add(node.getBlockPos()) || !node.hasSource() || node.source == null) {
                return false;
            }

            BlockPos source = node.source;
            if (source.equals(controller.getBlockPos())) {
                return !controller.hasSource() && RotationPropagator.isConnected(controller, node);
            }

            if (!level.isLoaded(source)) {
                return false;
            }

            BlockEntity upstream = level.getBlockEntity(source);
            if (!(upstream instanceof KineticBlockEntity prev) || !RotationPropagator.isConnected(prev, node)) {
                return false;
            }

            node = prev;
        }

        return false;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}