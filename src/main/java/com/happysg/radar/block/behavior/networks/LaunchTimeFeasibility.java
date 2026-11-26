package com.happysg.radar.block.behavior.networks;

import java.util.function.IntUnaryOperator;

public final class LaunchTimeFeasibility {
    private LaunchTimeFeasibility() {}
    public record Result(int launchDelayTicks, boolean feasible, int requiredSlewTicks, int candidatesChecked) {}

    public static Result earliest(int trackingLeadTicks, int maxPredictionTicks, IntUnaryOperator requiredSlewAt) {
        int max = Math.max(0, maxPredictionTicks);
        int first = Math.min(max, Math.max(0, trackingLeadTicks));
        int checked = 0;

        for (int delay = first; delay <= max; delay++) {
            int required = requiredSlewAt.applyAsInt(delay);
            checked++;

            int available = delay - first;
            if (required >= 0 && required <= available) {
                return new Result(delay, true, required, checked);
            }
        }

        return new Result(first, false, Integer.MAX_VALUE, checked);
    }

    public static int refinedDelay(int plannedDelay, int trackingLead, int requiredSlew, int maxDelay) {
        if (plannedDelay < 0 || trackingLead < 0 || requiredSlew < 0 || maxDelay < 0) {
            return -1;
        }

        int available = Math.max(0, plannedDelay - trackingLead);

        if (requiredSlew <= available) {
            return plannedDelay;
        }

        long corrected = Math.max((long) plannedDelay + 1, (long) trackingLead + requiredSlew);
        return corrected <= maxDelay ? (int) corrected : -1;
    }
}
