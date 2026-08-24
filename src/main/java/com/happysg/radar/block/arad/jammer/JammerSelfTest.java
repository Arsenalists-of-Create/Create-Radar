package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.api.arad.RollingRpmTracker;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class JammerSelfTest {
    private static final float EPSILON = 1.0e-4f;

    private JammerSelfTest() {
    }

    public static void main(String[] args) {
        verifyPlacementDefaults();
        verifyKineticStepping();
        verifyRollingRpmTelemetry();
        verifyOrientationBasis();
        System.out.println("PASS directional jammer placement, aiming, and RPM telemetry checks");
    }

    private static void verifyPlacementDefaults() {
        requireNear(JammerBlockEntity.defaultYaw(Direction.EAST, null), 0.0f,
                "east wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.SOUTH, null), 90.0f,
                "south wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.WEST, null), 180.0f,
                "west wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.NORTH, null), 270.0f,
                "north wall yaw");

        requireNear(JammerBlockEntity.defaultYaw(Direction.UP, Direction.NORTH),
                90.0f, "floor placement should face a north-facing player");
        requireNear(JammerBlockEntity.defaultYaw(Direction.DOWN, Direction.EAST),
                180.0f, "ceiling placement should face an east-facing player");
        requireNear(JammerBlockEntity.defaultYaw(Direction.UP, null), 270.0f,
                "non-player vertical placement fallback");
    }

    private static void verifyKineticStepping() {
        requireNear(JammerBlockEntity.degreesPerTick(64.0f), 19.2f,
                "positive RPM conversion");
        requireNear(JammerBlockEntity.degreesPerTick(-64.0f), 19.2f,
                "negative RPM conversion must use magnitude");

        requireNear(JammerBlockEntity.moveTowardWrapped(350.0f, 10.0f, 5.0f),
                355.0f, "yaw should take the shortest wrapped path");
        requireNear(JammerBlockEntity.moveTowardWrapped(5.0f, 355.0f, 5.0f),
                0.0f, "yaw should wrap through north");
        requireNear(JammerBlockEntity.moveTowardWrapped(10.0f, 90.0f, 0.0f),
                10.0f, "zero RPM should pause yaw");

        float step = JammerBlockEntity.degreesPerTick(32.0f);
        requireNear(JammerBlockEntity.moveTowardWrapped(0.0f, 90.0f, step),
                step, "yaw receives the full per-axis step");
        requireNear(JammerBlockEntity.moveToward(0.0f, 45.0f, step), step,
                "pitch receives the full per-axis step");
        requireNear(JammerBlockEntity.clampPitch(120.0f), 90.0f,
                "upper pitch clamp");
        requireNear(JammerBlockEntity.clampPitch(-120.0f), -90.0f,
                "lower pitch clamp");
    }

    private static void verifyRollingRpmTelemetry() {
        require(RollingRpmTracker.WINDOW_SAMPLES == 40,
                "20-second window sampled every 10 ticks must contain 40 samples");

        RollingRpmTracker steady = new RollingRpmTracker();
        for (int sample = 0; sample < RollingRpmTracker.WINDOW_SAMPLES; sample++) {
            steady.sample(sample % 2 == 0 ? 50.0f : -50.0f);
        }
        requireNear(steady.snapshot().rollingRpm(), 50.0f,
                "rolling RPM uses input magnitude");
        requireNear(steady.snapshot().rollingRate(), 0.0f,
                "constant RPM magnitude has zero rolling rate");

        RollingRpmTracker changing = new RollingRpmTracker();
        for (int sample = 0; sample < RollingRpmTracker.WINDOW_SAMPLES; sample++) {
            changing.sample(100.0f);
        }
        float slowRate = changing.sample(140.0f).rollingRate();
        float fastRate = changing.sample(180.0f).rollingRate();
        requireNear(changing.snapshot().rollingRpm(), 103.0f,
                "rolling window evicts the oldest samples");
        require(slowRate > 0.0f,
                "changed RPM produces a positive rolling rate");
        require(fastRate > slowRate,
                "larger rolling-RPM changes produce a greater rate");
    }

    private static void verifyOrientationBasis() {
        requireOrientation(Direction.UP, 270.0f, 0.0f,
                new Vector3f(0, 0, -1), new Vector3f(0, 1, 0),
                "floor north");
        requireOrientation(Direction.DOWN, 270.0f, 0.0f,
                new Vector3f(0, 0, -1), new Vector3f(0, -1, 0),
                "ceiling north inverted");
        requireOrientation(Direction.EAST, 0.0f, 0.0f,
                new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                "east wall outward and upright");

        Quaternionf pole = JammerOrientation.rotation(Direction.UP, 45.0f,
                90.0f);
        Vector3f poleForward = new Vector3f(0, 0, -1).rotate(pole);
        requireFinite(poleForward, "vertical aim orientation");
        requireVectorNear(poleForward, new Vector3f(0, 1, 0),
                "positive pitch should aim upward");
    }

    private static void requireOrientation(Direction mountFacing, float yaw,
                                           float pitch,
                                           Vector3f expectedForward,
                                           Vector3f expectedUp,
                                           String label) {
        Quaternionf rotation = JammerOrientation.rotation(mountFacing, yaw,
                pitch);
        Vector3f actualForward = new Vector3f(0, 0, -1).rotate(rotation);
        Vector3f actualUp = new Vector3f(0, 1, 0).rotate(rotation);
        requireFinite(actualForward, label + " forward");
        requireFinite(actualUp, label + " up");
        requireVectorNear(actualForward, expectedForward, label + " forward");
        requireVectorNear(actualUp, expectedUp, label + " up");
    }

    private static void requireFinite(Vector3f vector, String label) {
        require(Float.isFinite(vector.x()) && Float.isFinite(vector.y())
                        && Float.isFinite(vector.z()),
                label + " contains a non-finite component: " + vector);
    }

    private static void requireVectorNear(Vector3f actual, Vector3f expected,
                                          String label) {
        require(actual.distance(expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void requireNear(float actual, float expected,
                                    String label) {
        require(Math.abs(actual - expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
