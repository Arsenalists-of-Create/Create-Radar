package com.happysg.radar.api.monitor;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MonitorWorldHitTestRegistry {

    private static final Map<ResourceLocation, MonitorWorldHitTestHandler> HANDLERS = new LinkedHashMap<>();
    private MonitorWorldHitTestRegistry() {}

    public static void register(ResourceLocation radarTypeId, MonitorWorldHitTestHandler handler) {
        Objects.requireNonNull(radarTypeId, "radarTypeId");
        Objects.requireNonNull(handler, "handler");

        MonitorWorldHitTestHandler existing = HANDLERS.putIfAbsent(radarTypeId, handler);

        if (existing != null) {
            throw new IllegalStateException("Monitor world hit-test handler already registered for " + radarTypeId);
        }
    }

    public static Optional<MonitorWorldHitTestHandler> get(@Nullable ResourceLocation radarTypeId) {
        if (radarTypeId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(HANDLERS.get(radarTypeId));
    }

    public static boolean has(@Nullable ResourceLocation radarTypeId) {
        return radarTypeId != null && HANDLERS.containsKey(radarTypeId);
    }
}