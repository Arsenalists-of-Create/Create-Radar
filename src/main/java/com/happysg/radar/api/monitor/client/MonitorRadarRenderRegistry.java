package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side registry for custom monitor radar renderers.
 *
 * <p>Renderers are keyed by
 * {@link com.happysg.radar.api.radar.RadarDisplayProfile#typeId()}.</p>
 */
public final class MonitorRadarRenderRegistry {
    private static final Map<ResourceLocation, MonitorRadarRenderer> RENDERERS = new LinkedHashMap<>();
    private MonitorRadarRenderRegistry() {}

    /**
     * Registers a custom monitor renderer for a radar display type.
     *
     * @throws IllegalStateException if a renderer is already registered for the supplied type
     */
    public static void register(ResourceLocation typeId, MonitorRadarRenderer renderer) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(renderer, "renderer");
        MonitorRadarRenderer existing = RENDERERS.putIfAbsent(typeId, renderer);

        if (existing != null) {
            throw new IllegalStateException("Monitor radar renderer already registered for " + typeId);
        }
    }

    public static Optional<MonitorRadarRenderer> get(ResourceLocation typeId) {
        if (typeId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                RENDERERS.get(typeId)
        );
    }

    public static Optional<MonitorRadarRenderer> get(MonitorRadarSnapshot radar) {
        if (radar == null) {
            return Optional.empty();
        }

        return get(radar.displayProfile().typeId());
    }

    public static boolean has(ResourceLocation typeId) {
        return typeId != null && RENDERERS.containsKey(typeId);
    }

    public static Map<ResourceLocation, MonitorRadarRenderer> registeredRenderers() {
        return Collections.unmodifiableMap(RENDERERS);
    }
}