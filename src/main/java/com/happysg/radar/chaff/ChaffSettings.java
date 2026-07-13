package com.happysg.radar.chaff;

import com.happysg.radar.config.RadarConfig;

public record ChaffSettings(boolean enabled, double radius, int volleyWindowTicks,
                            double minChance, double maxSingleChance, double maxVolleyChance,
                            int minDurationTicks, int maxDurationTicks) {

    public static ChaffSettings current() {
        double configuredMinChance = RadarConfig.server().chaffMinChance.get();
        double configuredSingleMax = RadarConfig.server().chaffMaxSingleChance.get();
        double minChance = clamp01(Math.min(configuredMinChance, configuredSingleMax));
        double singleMax = clamp01(Math.max(configuredMinChance, configuredSingleMax));
        double volleyMax = Math.max(singleMax, clamp01(RadarConfig.server().chaffMaxVolleyChance.get()));

        int configuredMinDuration = RadarConfig.server().chaffMinDurationTicks.get();
        int configuredMaxDuration = RadarConfig.server().chaffMaxDurationTicks.get();
        int minDuration = Math.max(1, Math.min(configuredMinDuration, configuredMaxDuration));
        int maxDuration = Math.max(minDuration, Math.max(configuredMinDuration, configuredMaxDuration));

        return new ChaffSettings(
                RadarConfig.server().chaffEnabled.get(),
                Math.max(0.0D, RadarConfig.server().chaffRadius.get()),
                Math.max(1, RadarConfig.server().chaffVolleyWindowTicks.get()),
                minChance,
                singleMax,
                volleyMax,
                minDuration,
                maxDuration
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
