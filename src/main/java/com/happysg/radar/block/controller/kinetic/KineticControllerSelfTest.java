package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deterministic regression checks for generator servo and fail-safe behavior. */
public final class KineticControllerSelfTest {
    private static final double EPSILON = 1.0e-9;

    private KineticControllerSelfTest() {
    }

    public static void main(String[] args) {
        List<String> failures = runChecks();
        if (!failures.isEmpty()) {
            failures.forEach(failure -> System.err.println("FAIL " + failure));
            throw new IllegalStateException("Kinetic controller self-test failed");
        }
        System.out.println("PASS kinetic controller generator and fail-safe checks");
    }

    public static List<String> runChecks() {
        List<String> failures = new ArrayList<>();
        checkAngleMath(failures);
        checkFrames(failures);
        checkFirstNonzeroAndReadiness(failures);
        checkPowerLossAndFailClosedStates(failures);
        checkUnavailableAndReassembly(failures);
        checkReversal(failures);
        checkWatchdog(failures);
        checkDebugSwivelSweep(failures);
        checkDebugSwivelFollow(failures);
        return List.copyOf(failures);
    }

    private static void checkAngleMath(List<String> failures) {
        expectClose(failures, "wrap_negative", KineticAngleMath.wrap360(-1.0), 359.0);
        expectClose(failures, "shortest_forward_wrap",
                KineticAngleMath.shortestDelta(359.0, 1.0), 2.0);
        expectTrue(failures, "inclusive_wrapped_min",
                KineticAngleMath.isInInclusiveWrappedInterval(350.0, 350.0, 10.0));
        expectTrue(failures, "inclusive_wrapped_max",
                KineticAngleMath.isInInclusiveWrappedInterval(10.0, 350.0, 10.0));
        expectFalse(failures, "wrapped_interval_rejects",
                KineticAngleMath.isInInclusiveWrappedInterval(180.0, 350.0, 10.0));
    }

    private static void checkFrames(List<String> failures) {
        UUID id = new UUID(1, 2);
        KineticMountFrame yawDown = frame(id, Direction.DOWN, 1, 90.0);
        KineticMountFrame yawUp = frame(id, Direction.UP, -1, 90.0);
        expectClose(failures, "yaw_down_mapping", yawDown.bearingTargetFor(120.0), 30.0);
        expectClose(failures, "yaw_up_mapping", yawUp.bearingTargetFor(120.0), 330.0);
        expectClose(failures, "inverse_mapping", yawUp.controllerTargetFor(330.0), 120.0);

        KineticMountFrame pitchMatched = frame(id, Direction.EAST, 1, 0.0);
        KineticMountFrame pitchMirrored = frame(id, Direction.WEST, -1, 0.0);
        expectClose(failures, "pitch_matched", pitchMatched.bearingTargetFor(25.0), 25.0);
        expectClose(failures, "pitch_mirrored", pitchMirrored.bearingTargetFor(25.0), 335.0);

        expectTrue(failures, "yaw_uses_bearing_up_not_controller_down",
                KineticMountFrame.conversionSignFor(CannonAxis.YAW, Direction.UP,
                        Direction.DOWN, Direction.SOUTH) == -1);
        expectTrue(failures, "yaw_uses_bearing_down_not_controller_up",
                KineticMountFrame.conversionSignFor(CannonAxis.YAW, Direction.DOWN,
                        Direction.UP, Direction.SOUTH) == 1);
        expectTrue(failures, "pitch_east_south_raises_with_negative_rotation",
                KineticMountFrame.conversionSignFor(CannonAxis.PITCH, Direction.EAST,
                        Direction.EAST, Direction.SOUTH) == -1);
        expectTrue(failures, "pitch_west_south_raises_with_positive_rotation",
                KineticMountFrame.conversionSignFor(CannonAxis.PITCH, Direction.WEST,
                        Direction.WEST, Direction.SOUTH) == 1);
        expectTrue(failures, "pitch_east_north_reverses_with_cannon",
                KineticMountFrame.conversionSignFor(CannonAxis.PITCH, Direction.EAST,
                        Direction.EAST, Direction.NORTH) == 1);
    }

    private static void checkFirstNonzeroAndReadiness(List<String> failures) {
        FakeRig rig = new FakeRig();
        KineticControllerState state = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution present = KineticMountAdapterResolution.present(rig.adapter);

        tick(state, rig, present, true, 30.0); // lock stability guard
        tick(state, rig, present, true, 30.0);
        expectTrue(failures, "first_nonzero_commands_generator", rig.commandedRpm < -EPSILON);
        expectTrue(failures, "command_clamped_to_input",
                Math.abs(rig.commandedRpm) <= rig.availableInputRpm + EPSILON);
        expectTrue(failures, "command_clamped_to_swivel_limit",
                Math.abs(rig.commandedRpm) <= 32.0 + EPSILON);
        expectClose(failures, "swivel_drive_limit_is_32_rpm",
                rig.adapter.maximumDriveRpm(64.0), 32.0);
        expectFalse(failures, "sequence_context_unused", rig.adapter.sequenceContext);

        double travel = 0.0;
        double previous = rig.adapter.targetAngle;
        for (int i = 0; i < 100 && !state.isReady(present, true, 30.0, 0.5); i++) {
            rig.advance();
            travel += Math.abs(KineticAngleMath.shortestDelta(previous, rig.adapter.targetAngle));
            previous = rig.adapter.targetAngle;
            tick(state, rig, present, true, 30.0);
        }
        expectTrue(failures, "physical_pose_settles",
                state.isReady(present, true, 30.0, 0.5));
        expectClose(failures, "settle_stops_generator", rig.commandedRpm, 0.0);
        expectTrue(failures, "no_full_revolution", travel < 60.0);

        rig.adapter.targetAngle = 30.1;
        rig.adapter.physicalAngle = 30.1;
        state.onTargetChanged(true, 30.1, 0.5);
        expectTrue(failures, "sub_tolerance_jitter_does_not_restart_revision",
                state.isReady(present, true, 30.1, 0.5));
        expectClose(failures, "sub_tolerance_jitter_keeps_generator_stopped",
                rig.commandedRpm, 0.0);

        state.onTargetChanged(true, 60.0, 0.5);
        expectFalse(failures, "same_tick_readiness_invalidation",
                state.isReady(present, true, 60.0, 0.5));
    }

    private static void checkPowerLossAndFailClosedStates(List<String> failures) {
        FakeRig rig = new FakeRig();
        KineticControllerState state = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution present = KineticMountAdapterResolution.present(rig.adapter);
        tick(state, rig, present, true, 0.0);
        tick(state, rig, present, true, 40.0);
        expectTrue(failures, "powered_generator_moves", Math.abs(rig.commandedRpm) > EPSILON);

        rig.availableInputRpm = 0.0;
        tick(state, rig, present, true, 40.0);
        expectClose(failures, "power_loss_stops_synchronously", rig.commandedRpm, 0.0);
        rig.availableInputRpm = 8.0;
        tick(state, rig, present, true, 40.0);
        expectTrue(failures, "power_restore_resumes", Math.abs(rig.commandedRpm) > EPSILON);
        expectTrue(failures, "restored_command_clamped",
                Math.abs(rig.commandedRpm) <= 8.0 + EPSILON);

        FakeRig delayedRig = new FakeRig();
        delayedRig.propagationDelayTicks = 2;
        KineticControllerState delayedState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution delayedPresent =
                KineticMountAdapterResolution.present(delayedRig.adapter);
        tick(delayedState, delayedRig, delayedPresent, true, 20.0);
        tick(delayedState, delayedRig, delayedPresent, true, 20.0);
        expectFalse(failures, "delayed_propagation_not_falsely_ready",
                delayedState.isAtDestination());
        tick(delayedState, delayedRig, delayedPresent, true, 20.0);
        tick(delayedState, delayedRig, delayedPresent, true, 20.0);
        expectTrue(failures, "delayed_propagation_recovers", delayedRig.adapter.driven);
        expectFalse(failures, "delayed_propagation_not_blocked", delayedState.isBlocked());

        rig.adapter.assembled = false;
        tick(state, rig, present, true, 40.0);
        expectClose(failures, "disassembly_stops", rig.commandedRpm, 0.0);
        rig.adapter.assembled = true;
        rig.adapter.locked = false;
        tick(state, rig, present, true, 40.0);
        expectClose(failures, "unlock_stops", rig.commandedRpm, 0.0);
        rig.adapter.locked = true;

        tick(state, rig, KineticMountAdapterResolution.ambiguous("multiple"), true, 40.0);
        expectClose(failures, "ambiguous_stops", rig.commandedRpm, 0.0);

        FakeRig foreignRig = new FakeRig();
        foreignRig.adapter.free = false;
        foreignRig.foreignOwned = true;
        KineticControllerState foreignState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution foreignPresent =
                KineticMountAdapterResolution.present(foreignRig.adapter);
        tick(foreignState, foreignRig, foreignPresent, true, 20.0);
        tick(foreignState, foreignRig, foreignPresent, true, 20.0);
        expectClose(failures, "foreign_endpoint_never_powered", foreignRig.commandedRpm, 0.0);
        expectTrue(failures, "foreign_endpoint_blocks_revision", foreignState.isBlocked());

        FakeRig contextRig = new FakeRig();
        KineticControllerState contextState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution contextPresent =
                KineticMountAdapterResolution.present(contextRig.adapter);
        tick(contextState, contextRig, contextPresent, true, 0.0);
        tick(contextState, contextRig, contextPresent, true, 20.0);
        contextRig.adapter.sequenceContext = true;
        tick(contextState, contextRig, contextPresent, true, 20.0);
        expectClose(failures, "sequence_contamination_stops", contextRig.commandedRpm, 0.0);
        expectTrue(failures, "sequence_contamination_blocks", contextState.isBlocked());

        FakeRig staleContextRig = new FakeRig();
        staleContextRig.adapter.sequenceContext = true;
        KineticControllerState staleContextState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution staleContextPresent =
                KineticMountAdapterResolution.present(staleContextRig.adapter);
        tick(staleContextState, staleContextRig, staleContextPresent, true, 0.0);
        expectFalse(failures, "detached_legacy_context_is_discarded",
                staleContextRig.adapter.sequenceContext);
        expectFalse(failures, "detached_legacy_context_does_not_block",
                staleContextState.isBlocked());
    }

    private static void checkUnavailableAndReassembly(List<String> failures) {
        FakeRig rig = new FakeRig();
        KineticControllerState state = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution present = KineticMountAdapterResolution.present(rig.adapter);
        tick(state, rig, present, true, 0.0);
        tick(state, rig, present, true, 20.0);

        tick(state, rig, KineticMountAdapterResolution.unavailable("chunk"), true, 20.0);
        expectTrue(failures, "unavailable_suppresses_legacy", state.isStructuralMode());
        expectClose(failures, "unavailable_stops_generator", rig.commandedRpm, 0.0);

        CompoundTag saved = new CompoundTag();
        state.write(saved, false);
        KineticControllerState reloaded = new KineticControllerState(CannonAxis.YAW);
        reloaded.read(saved, false, false);
        FakeRig sameAssembly = new FakeRig();
        sameAssembly.adapter.frame = rig.adapter.frame;
        KineticMountAdapterResolution samePresent =
                KineticMountAdapterResolution.present(sameAssembly.adapter);
        tick(reloaded, sameAssembly, samePresent, true, 20.0);
        tick(reloaded, sameAssembly, samePresent, true, 20.0);
        expectTrue(failures, "unload_preserves_frame_and_resumes",
                Math.abs(sameAssembly.commandedRpm) > EPSILON);
        for (int i = 0; i < 100 && !reloaded.isReady(samePresent, true, 20.0, 0.5); i++) {
            sameAssembly.advance();
            tick(reloaded, sameAssembly, samePresent, true, 20.0);
        }
        expectTrue(failures, "same_assembly_settles_before_reassembly",
                reloaded.isReady(samePresent, true, 20.0, 0.5));

        sameAssembly.adapter.frame = frame(new UUID(9, 9), Direction.UP, -1, 0.0);
        tick(reloaded, sameAssembly, samePresent, true, 20.0);
        expectFalse(failures, "reassembly_invalidates_readiness", reloaded.isAtDestination());
        expectClose(failures, "reassembly_stops_old_frame_command",
                sameAssembly.commandedRpm, 0.0);
    }

    private static void checkReversal(List<String> failures) {
        FakeRig rig = new FakeRig();
        KineticControllerState state = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution present = KineticMountAdapterResolution.present(rig.adapter);
        tick(state, rig, present, true, 0.0);
        tick(state, rig, present, true, 20.0);
        expectTrue(failures, "forward_command_negative", rig.commandedRpm < 0.0);

        state.onTargetChanged(true, -20.0, 0.5);
        tick(state, rig, present, true, -20.0);
        expectClose(failures, "reversal_passes_through_zero", rig.commandedRpm, 0.0);
        for (int i = 0; i < 8; i++) {
            tick(state, rig, present, true, -20.0);
        }
        expectTrue(failures, "reversal_eventually_changes_sign", rig.commandedRpm > 0.0);
    }

    private static void checkWatchdog(List<String> failures) {
        FakeRig rig = new FakeRig();
        KineticControllerState state = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution present = KineticMountAdapterResolution.present(rig.adapter);
        tick(state, rig, present, true, 0.0);
        tick(state, rig, present, true, 10.0);
        state.invalidate(); // Network churn must not reset the travel budget.
        rig.command(0.0);
        rig.adapter.targetAngle = 170.0;
        rig.adapter.physicalAngle = 170.0;
        tick(state, rig, present, true, 10.0);
        expectClose(failures, "watchdog_stops_runaway", rig.commandedRpm, 0.0);
        expectTrue(failures, "watchdog_blocks_same_revision", state.isBlocked());
        tick(state, rig, present, true, 10.0);
        expectClose(failures, "blocked_revision_cannot_restart", rig.commandedRpm, 0.0);

        FakeRig stuckRig = new FakeRig();
        stuckRig.acceptDrive = false;
        KineticControllerState stuckState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution stuckPresent =
                KineticMountAdapterResolution.present(stuckRig.adapter);
        for (int i = 0; i < 50; i++) {
            tick(stuckState, stuckRig, stuckPresent, true, 20.0);
        }
        expectTrue(failures, "stuck_endpoint_times_out", stuckState.isBlocked());
        expectClose(failures, "stuck_endpoint_stops_generator", stuckRig.commandedRpm, 0.0);

        FakeRig incrementalRig = new FakeRig();
        KineticControllerState incrementalState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution incrementalPresent =
                KineticMountAdapterResolution.present(incrementalRig.adapter);
        tick(incrementalState, incrementalRig, incrementalPresent, true, 10.0);
        tick(incrementalState, incrementalRig, incrementalPresent, true, 10.0);
        for (int i = 0; i < 20 && !incrementalState.isBlocked(); i++) {
            incrementalRig.adapter.targetAngle = KineticAngleMath.wrap360(
                    incrementalRig.adapter.targetAngle + 30.0);
            incrementalRig.adapter.physicalAngle = incrementalRig.adapter.targetAngle;
            tick(incrementalState, incrementalRig, incrementalPresent, true, 10.0);
        }
        expectTrue(failures, "incremental_runaway_cannot_hide_wraps",
                incrementalState.isBlocked());
        expectClose(failures, "incremental_runaway_stops_generator",
                incrementalRig.commandedRpm, 0.0);

        FakeRig movingTargetRig = new FakeRig();
        KineticControllerState movingTargetState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution movingTargetPresent =
                KineticMountAdapterResolution.present(movingTargetRig.adapter);
        tick(movingTargetState, movingTargetRig, movingTargetPresent, true, 0.0);
        tick(movingTargetState, movingTargetRig, movingTargetPresent, true, 10.0);
        double movingTarget = 10.0;
        for (int i = 0; i < 20 && !movingTargetState.isBlocked(); i++) {
            movingTarget += 1.0;
            movingTargetRig.adapter.targetAngle = KineticAngleMath.wrap360(
                    movingTargetRig.adapter.targetAngle + 30.0);
            movingTargetRig.adapter.physicalAngle = movingTargetRig.adapter.targetAngle;
            tick(movingTargetState, movingTargetRig, movingTargetPresent, true, movingTarget);
        }
        expectTrue(failures, "moving_target_cannot_reset_watchdog",
                movingTargetState.isBlocked());
        expectClose(failures, "moving_target_runaway_stops_generator",
                movingTargetRig.commandedRpm, 0.0);
        movingTarget += 5.0;
        tick(movingTargetState, movingTargetRig, movingTargetPresent, true, movingTarget);
        expectTrue(failures, "moving_target_cannot_clear_watchdog_latch",
                movingTargetState.isBlocked());
        expectClose(failures, "moving_target_blocked_latch_keeps_generator_stopped",
                movingTargetRig.commandedRpm, 0.0);

        FakeRig followRig = new FakeRig();
        KineticControllerState followState = new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution followPresent =
                KineticMountAdapterResolution.present(followRig.adapter);
        followState.beginContinuousTracking();
        double followTarget = 10.0;
        tick(followState, followRig, followPresent, true, followTarget);
        for (int i = 0; i < 160 && !followState.isBlocked(); i++) {
            followTarget = Math.min(170.0, followTarget + 0.5);
            followRig.advance();
            tick(followState, followRig, followPresent, true, followTarget);
        }
        expectFalse(failures, "continuous_follow_does_not_expire_total_duration",
                followState.isBlocked());

        FakeRig stalledFollowRig = new FakeRig();
        stalledFollowRig.acceptDrive = false;
        KineticControllerState stalledFollowState =
                new KineticControllerState(CannonAxis.YAW);
        KineticMountAdapterResolution stalledFollowPresent =
                KineticMountAdapterResolution.present(stalledFollowRig.adapter);
        stalledFollowState.beginContinuousTracking();
        for (int i = 0; i < 120 && !stalledFollowState.isBlocked(); i++) {
            tick(stalledFollowState, stalledFollowRig, stalledFollowPresent, true, 20.0);
        }
        expectTrue(failures, "continuous_follow_still_blocks_when_stalled",
                stalledFollowState.isBlocked());
    }

    private static void checkDebugSwivelSweep(List<String> failures) {
        DebugSwivelSweep sweep = new DebugSwivelSweep();
        List<SweepCommand> commands = new ArrayList<>();
        DebugSwivelSweep.TargetCommand command =
                (target, running) -> commands.add(new SweepCommand(target, running));
        sweep.start(30.0, 12.0, 77.0, false, 2.0, command);
        expectTrue(failures, "debug_sweep_active_after_start", sweep.isActive());
        expectSweepCommand(failures, "debug_sweep_moves_relative_to_capture",
                commands, 0, 42.0, true);

        sweep.enforce(999.0, command);
        expectSweepCommand(failures, "debug_sweep_reasserts_override",
                commands, 1, 42.0, true);
        expectTrue(failures, "debug_sweep_waits_for_outbound_pose",
                sweep.tick(true, false, 2.0, command) == null);
        expectTrue(failures, "debug_sweep_starts_return",
                sweep.tick(true, true, 2.0, command) == null);
        expectSweepCommand(failures, "debug_sweep_returns_to_capture",
                commands, 2, 12.0, true);
        expectTrue(failures, "debug_sweep_reports_completion",
                "completed".equals(sweep.tick(true, true, 2.0, command)));
        expectSweepCommand(failures, "debug_sweep_restores_production_command",
                commands, 3, 77.0, false);
        expectFalse(failures, "debug_sweep_inactive_after_completion", sweep.isActive());

        commands.clear();
        sweep.start(-20.0, 5.0, 33.0, true, 1.0, command);
        expectTrue(failures, "debug_sweep_invalid_selection_cancels",
                "selection_invalid".equals(sweep.tick(false, false, 1.0, command)));
        expectSweepCommand(failures, "debug_sweep_cancel_restores_target",
                commands, 1, 33.0, true);
        expectFalse(failures, "debug_sweep_inactive_after_cancel", sweep.isActive());

        commands.clear();
        sweep.start(10.0, 0.0, 21.0, false, Double.NaN, command);
        String timeout = null;
        for (int i = 0; i < 101 && timeout == null; i++) {
            timeout = sweep.tick(true, false, Double.NaN, command);
        }
        expectTrue(failures, "debug_sweep_stage_timeout",
                "stage_timeout_moving_out".equals(timeout));
        expectSweepCommand(failures, "debug_sweep_timeout_restores_target",
                commands, 1, 21.0, false);
    }

    private static void checkDebugSwivelFollow(List<String> failures) {
        Vec3 origin = Vec3.ZERO;
        expectClose(failures, "debug_follow_yaw_south",
                DebugSwivelFollow.yawTargetDegrees(origin, new Vec3(0.0, 0.0, 10.0)), 0.0);
        expectClose(failures, "debug_follow_yaw_west",
                DebugSwivelFollow.yawTargetDegrees(origin, new Vec3(-10.0, 0.0, 0.0)), 90.0);
        expectClose(failures, "debug_follow_yaw_north",
                DebugSwivelFollow.yawTargetDegrees(origin, new Vec3(0.0, 0.0, -10.0)), 180.0);
        expectClose(failures, "debug_follow_yaw_east",
                DebugSwivelFollow.yawTargetDegrees(origin, new Vec3(10.0, 0.0, 0.0)), 270.0);
        expectClose(failures, "debug_follow_pitch_up",
                DebugSwivelFollow.pitchTargetDegrees(origin, new Vec3(10.0, 10.0, 0.0)), 45.0);
        expectClose(failures, "debug_follow_pitch_down",
                DebugSwivelFollow.pitchTargetDegrees(origin, new Vec3(10.0, -10.0, 0.0)), -45.0);

        DebugSwivelFollow follow = new DebugSwivelFollow();
        UUID playerId = new UUID(8, 9);
        KineticMountFrame followFrame = frame(new UUID(10, 11), Direction.DOWN, 1, 0.0);
        List<SweepCommand> commands = new ArrayList<>();
        DebugSwivelSweep.TargetCommand command =
                (target, running) -> commands.add(new SweepCommand(target, running));
        follow.start(playerId, followFrame, Direction.EAST, 77.0, false);
        expectTrue(failures, "debug_follow_active_after_start", follow.isActive());
        expectTrue(failures, "debug_follow_tracks_selected_player", follow.follows(playerId));
        expectTrue(failures, "debug_follow_matches_original_frame",
                follow.matches(followFrame, Direction.EAST));
        expectClose(failures, "debug_follow_persists_production_target",
                follow.persistentTarget(12.0), 77.0);
        expectFalse(failures, "debug_follow_persists_production_running",
                follow.persistentRunning(true));

        follow.update(25.0, command);
        expectSweepCommand(failures, "debug_follow_commands_live_target",
                commands, 0, 25.0, true);
        expectTrue(failures, "debug_follow_stop_reason",
                "stopped_by_command".equals(follow.stop("stopped_by_command", command)));
        expectSweepCommand(failures, "debug_follow_restores_production_command",
                commands, 1, 77.0, false);
        expectFalse(failures, "debug_follow_inactive_after_stop", follow.isActive());
    }

    private static void expectSweepCommand(List<String> failures, String name,
                                           List<SweepCommand> commands, int index,
                                           double target, boolean running) {
        if (commands.size() <= index) {
            failures.add(name + " missing command index=" + index);
            return;
        }
        SweepCommand actual = commands.get(index);
        if (Math.abs(actual.target() - target) > EPSILON || actual.running() != running) {
            failures.add(name + " expected=" + target + "/" + running
                    + " actual=" + actual.target() + "/" + actual.running());
        }
    }

    private record SweepCommand(double target, boolean running) {
    }

    private static KineticMountFrame frame(UUID id, Direction facing, int sign, double neutral) {
        return new KineticMountFrame(KineticMountFrame.CURRENT_VERSION, facing, facing,
                id, Direction.SOUTH, sign, neutral);
    }

    private static void tick(KineticControllerState state, FakeRig rig,
                             KineticMountAdapterResolution resolution,
                             boolean running, double target) {
        state.tick(BlockPos.ZERO, resolution, running, target, 0.5,
                rig.availableInputRpm, true, rig::command);
    }

    private static void expectClose(List<String> failures, String name, double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > EPSILON) {
            failures.add(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectTrue(List<String> failures, String name, boolean actual) {
        if (!actual) failures.add(name + " expected=true actual=false");
    }

    private static void expectFalse(List<String> failures, String name, boolean actual) {
        if (actual) failures.add(name + " expected=false actual=true");
    }

    private static final class FakeRig {
        private final FakeAdapter adapter = new FakeAdapter();
        private double availableInputRpm = 16.0;
        private double commandedRpm;
        private boolean foreignOwned;
        private boolean acceptDrive = true;
        private int propagationDelayTicks;

        private void command(double rpm) {
            commandedRpm = rpm;
            if (Math.abs(rpm) > EPSILON) {
                if (!acceptDrive || propagationDelayTicks > 0) {
                    if (propagationDelayTicks > 0) {
                        propagationDelayTicks--;
                    }
                    adapter.free = true;
                    adapter.driven = false;
                    adapter.endpointRpm = 0.0;
                    return;
                }
                adapter.free = false;
                adapter.driven = true;
                adapter.sequenceContext = false; // Create copies the generator's null context.
                adapter.endpointRpm = -rpm;
            } else {
                adapter.driven = false;
                if (!foreignOwned) {
                    adapter.free = true;
                }
                adapter.endpointRpm = 0.0;
            }
        }

        private void advance() {
            double movement = adapter.endpointRpm * 0.3 * adapter.positiveRotationSign;
            adapter.targetAngle = KineticAngleMath.wrap360(adapter.targetAngle + movement);
            adapter.physicalAngle = adapter.targetAngle;
        }
    }

    private static final class FakeAdapter implements KineticMountAdapter {
        private boolean valid = true;
        private boolean assembled = true;
        private boolean locked = true;
        private boolean free = true;
        private boolean driven;
        private boolean sequenceContext;
        private double targetAngle;
        private double physicalAngle;
        private double endpointRpm;
        private int positiveRotationSign = 1;
        private KineticMountFrame frame = frame(new UUID(1, 1), Direction.DOWN, 1, 0.0);

        @Override public CannonAxis axis() { return CannonAxis.YAW; }
        @Override public Direction relativeDirection() { return Direction.EAST; }
        @Override public boolean isValid() { return valid; }
        @Override public boolean hasSameEndpoint(KineticMountAdapter other) { return other == this; }
        @Override public boolean isAssembled() { return assembled; }
        @Override public boolean isLocked() { return locked; }
        @Override public double getTargetAngleDegrees() { return targetAngle; }
        @Override public double getPhysicalAngleDegrees() {
            return valid && assembled ? physicalAngle : Double.NaN;
        }
        @Override public boolean wakePhysicalAssembly() { return valid && assembled; }
        @Override public KineticMountFrame frameIdentity() { return assembled ? frame : null; }
        @Override public double getEndpointTheoreticalSpeed() { return endpointRpm; }
        @Override public int getPositiveRotationSign() { return positiveRotationSign; }
        @Override public boolean isEndpointFree() { return free && !driven; }
        @Override public boolean isEndpointSafelyReleased() {
            return isEndpointFree() && !sequenceContext;
        }
        @Override public boolean isDrivenBy(BlockPos controllerPos) {
            return driven && BlockPos.ZERO.equals(controllerPos);
        }
        @Override public boolean hasSequenceContext() { return sequenceContext; }
        @Override public boolean discardStaleSequenceContextIfFree() {
            if (!isEndpointFree()) return false;
            sequenceContext = false;
            return true;
        }
        @Override public double maximumDriveRpm(double availableInputRpm) {
            return Math.max(0.0, Math.min(Math.abs(availableInputRpm), 32.0));
        }
        @Override public double effectiveDegreesPerTick(double expectedEndpointSpeed) {
            return Math.abs(expectedEndpointSpeed) * 0.3;
        }
    }
}
