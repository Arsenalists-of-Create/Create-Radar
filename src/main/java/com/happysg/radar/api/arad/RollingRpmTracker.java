package com.happysg.radar.api.arad;

/**
 * Fixed-size rolling RPM sampler. Input direction is intentionally discarded;
 * rate is the absolute change in the rolling average, expressed in RPM/second.
 */
public final class RollingRpmTracker {
    public static final int SAMPLE_INTERVAL_TICKS = 10;
    public static final int WINDOW_TICKS = 20 * 20;
    public static final int WINDOW_SAMPLES = WINDOW_TICKS / SAMPLE_INTERVAL_TICKS;

    public record Snapshot(float rollingRpm, float rollingRate) {
        public static final Snapshot ZERO = new Snapshot(0.0f, 0.0f);
    }

    private final float[] samples;
    private final float samplesPerSecond;
    private int nextSample;
    private int sampleCount;
    private double sampleSum;
    private float rollingRpm;
    private float rollingRate;
    private boolean hasRollingRpm;

    public RollingRpmTracker() {
        this(WINDOW_SAMPLES, 20.0f / SAMPLE_INTERVAL_TICKS);
    }

    RollingRpmTracker(int windowSamples, float samplesPerSecond) {
        if (windowSamples <= 0 || !Float.isFinite(samplesPerSecond)
                || samplesPerSecond <= 0.0f) {
            throw new IllegalArgumentException("Invalid rolling RPM window");
        }
        this.samples = new float[windowSamples];
        this.samplesPerSecond = samplesPerSecond;
    }

    public Snapshot sample(float suppliedRpm) {
        float magnitude = Float.isFinite(suppliedRpm)
                ? Math.abs(suppliedRpm) : 0.0f;
        if (sampleCount == samples.length) {
            sampleSum -= samples[nextSample];
        } else {
            sampleCount++;
        }

        samples[nextSample] = magnitude;
        nextSample = (nextSample + 1) % samples.length;
        sampleSum += magnitude;

        float previousRollingRpm = rollingRpm;
        rollingRpm = (float) (sampleSum / sampleCount);
        rollingRate = hasRollingRpm
                ? Math.abs(rollingRpm - previousRollingRpm) * samplesPerSecond
                : 0.0f;
        hasRollingRpm = true;
        return snapshot();
    }

    public Snapshot snapshot() {
        return hasRollingRpm
                ? new Snapshot(rollingRpm, rollingRate)
                : Snapshot.ZERO;
    }
}
