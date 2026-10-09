package com.happysg.radar.api.monitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MonitorContactSelectionRegistry {

    private static final Map<String, MonitorContactSelectionHandler> HANDLERS = new LinkedHashMap<>();

    private MonitorContactSelectionRegistry() {}

    public static void register(String sourceType, MonitorContactSelectionHandler handler) {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(handler, "handler");
        MonitorContactSelectionHandler existing = HANDLERS.putIfAbsent(sourceType, handler);

        if (existing != null) {
            throw new IllegalStateException("Monitor contact selection handler already registered for " + sourceType);
        }
    }

    public static Optional<MonitorContactSelectionHandler> get(String sourceType) {
        if (sourceType == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(HANDLERS.get(sourceType));
    }

    public static boolean has(String sourceType) {
        return sourceType != null && HANDLERS.containsKey(sourceType);
    }
}