package com.happysg.radar.api.targeting;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RadarTargetingRegistry {

    private static final Map<String, RadarTargetingProvider> PROVIDERS = new LinkedHashMap<>();

    private RadarTargetingRegistry() {}

    public static synchronized void register(String id, RadarTargetingProvider provider) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Targeting provider id must not be blank");
        }

        PROVIDERS.put(id, Objects.requireNonNull(provider, "provider"));
    }

    public static RadarTargetingSolution resolve(RadarTargetingContext context) {
        Objects.requireNonNull(context, "context");

        List<RadarTargetingProvider> providers;

        synchronized (RadarTargetingRegistry.class) {
            providers = List.copyOf(PROVIDERS.values());
        }

        for (RadarTargetingProvider provider : providers) {
            RadarTargetingSolution solution = provider.resolve(context);

            if (solution != null) {
                return solution;
            }
        }

        return context.defaultSolution();
    }
}