package com.happysg.radar.compat.cbc_at;


public final class CBCATLaunchMath {
    private CBCATLaunchMath() {
    }

    public static float initialSpeed(
            float baseSpeed,
            float speedIncreasePerBarrel,
            int barrelCount,
            int maxSpeedIncreases,
            boolean poweredRocket,
            boolean strongHeavyRound
    ) {
        int increases = Math.max(0, Math.min(barrelCount, maxSpeedIncreases));
        float speed = poweredRocket
                ? baseSpeed * 0.5F + increases * speedIncreasePerBarrel * 0.25F
                : baseSpeed + increases * speedIncreasePerBarrel;
        return strongHeavyRound ? speed * 1.5F : speed;
    }

    public static int flightLifetime(int materialLifetime, int fuelTicks, boolean poweredRocket, boolean heavyAutocannon) {
        int base = Math.max(1, materialLifetime);
        if (poweredRocket) {
            return base + Math.max(0, fuelTicks);
        }
        return heavyAutocannon ? base * 3 : base;
    }
}
