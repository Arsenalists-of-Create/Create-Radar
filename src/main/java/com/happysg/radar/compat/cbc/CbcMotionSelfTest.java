package com.happysg.radar.compat.cbc;

import com.happysg.radar.targeting.TargetingSolverSelfTest;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CbcMotionSelfTest {
    private static final double EPSILON = 1.0E-9;

    private CbcMotionSelfTest() {
    }

    public static List<TargetingSolverSelfTest.Result> runChecks() {
        List<TargetingSolverSelfTest.Result> results = new ArrayList<>();
        results.add(checkDirectMountMotion());
        results.add(checkVelocitySampling());
        results.add(checkAccelerationSampling());
        return List.copyOf(results);
    }

    private static TargetingSolverSelfTest.Result checkDirectMountMotion() {
        DirectCbcMountMotion.State state = new DirectCbcMountMotion.State();
        double position = 0.0;
        double maximumStep = 0.0;
        boolean overshot = false;
        int ticks = 0;
        while (ticks < 80 && Math.abs(90.0 - position) > EPSILON) {
            double step = state.nextStep(90.0 - position, 120.0);
            maximumStep = Math.max(maximumStep, Math.abs(step));
            position += step;
            overshot |= position > 90.0 + EPSILON;
            ticks++;
        }

        double estimate = DirectCbcMountMotion.estimateTicks(90.0, 120.0);
        boolean passed = !overshot
                && Math.abs(position - 90.0) <= EPSILON
                && maximumStep <= DirectCbcMountMotion.MAX_DEGREES_PER_TICK
                + EPSILON
                && estimate > 0.0
                && estimate <= 40.0;
        return result("direct_cbc_bounded_motion", passed,
                "ticks=" + ticks + " estimate=" + estimate
                        + " maxStep=" + maximumStep
                        + " final=" + position);
    }

    private static TargetingSolverSelfTest.Result checkVelocitySampling() {
        UUID id = UUID.randomUUID();
        VelocityTracker.clear(id);
        Vec3 first = VelocityTracker.sample(id, Vec3.ZERO, 100L);
        Vec3 normalized = VelocityTracker.sample(
                id, new Vec3(2.0, 0.0, 0.0), 102L);
        Vec3 sameTick = VelocityTracker.sample(
                id, new Vec3(200.0, 0.0, 0.0), 102L);
        Vec3 gapReset = VelocityTracker.sample(
                id, new Vec3(3.0, 0.0, 0.0), 105L);
        Vec3 afterReset = VelocityTracker.sample(
                id, new Vec3(4.0, 0.0, 0.0), 106L);
        Vec3 spikeReset = VelocityTracker.sample(
                id, new Vec3(100.0, 0.0, 0.0), 107L);
        VelocityTracker.clear(id);

        boolean passed = first.equals(Vec3.ZERO)
                && Math.abs(normalized.x - 0.35) <= EPSILON
                && sameTick.equals(normalized)
                && gapReset.equals(Vec3.ZERO)
                && Math.abs(afterReset.x - 0.35) <= EPSILON
                && spikeReset.equals(Vec3.ZERO);
        return result("target_velocity_gap_and_spike_reset", passed,
                "normalized=" + normalized + " sameTick=" + sameTick
                        + " gap=" + gapReset + " spike=" + spikeReset);
    }

    private static TargetingSolverSelfTest.Result checkAccelerationSampling() {
        UUID id = UUID.randomUUID();
        AccelerationTracker.clear(id);
        Vec3 first = AccelerationTracker.getAccelerationPerTick2(
                id, new Vec3(0.2, 0.0, 0.0), 100L);
        Vec3 normalized = AccelerationTracker.getAccelerationPerTick2(
                id, new Vec3(0.4, 0.0, 0.0), 102L);
        Vec3 sameTick = AccelerationTracker.getAccelerationPerTick2(
                id, new Vec3(5.0, 0.0, 0.0), 102L);
        Vec3 gapReset = AccelerationTracker.getAccelerationPerTick2(
                id, new Vec3(0.6, 0.0, 0.0), 105L);
        Vec3 stopped = AccelerationTracker.getAccelerationPerTick2(
                id, Vec3.ZERO, 106L);
        AccelerationTracker.clear(id);

        boolean passed = first.equals(Vec3.ZERO)
                && Math.abs(normalized.x - 0.035) <= EPSILON
                && sameTick.equals(normalized)
                && gapReset.equals(Vec3.ZERO)
                && stopped.x < 0.0;
        return result("target_acceleration_timestamp_sampling", passed,
                "normalized=" + normalized + " sameTick=" + sameTick
                        + " gap=" + gapReset + " stopped=" + stopped);
    }

    private static TargetingSolverSelfTest.Result result(
            String name, boolean passed, String detail) {
        return new TargetingSolverSelfTest.Result(name, passed, detail);
    }
}
