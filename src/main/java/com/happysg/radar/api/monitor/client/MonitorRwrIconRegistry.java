package com.happysg.radar.api.monitor.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side registry for radar warning receiver display icons.
 * Icons are keyed by the public radar/RWR type ID.
 */
public final class MonitorRwrIconRegistry {

    private static final Map<ResourceLocation, ResourceLocation> ICONS = new LinkedHashMap<>();

    private MonitorRwrIconRegistry() {}

    /**
     * Registers an icon texture for an RWR radar type.
     *
     * @throws IllegalStateException if an icon is already registered for the supplied type ID
     */
    public static void register(ResourceLocation typeId, ResourceLocation texture) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(texture, "texture");
        ResourceLocation existing = ICONS.putIfAbsent(typeId, texture);

        if (existing != null) {
            throw new IllegalStateException("RWR icon already registered for " + typeId);
        }
    }

    public static Optional<ResourceLocation> get(ResourceLocation typeId) {
        if (typeId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(ICONS.get(typeId));
    }

    public static boolean has(ResourceLocation typeId) {
        return typeId != null && ICONS.containsKey(typeId);
    }

    public static Map<ResourceLocation, ResourceLocation> registeredIcons() {
        return Collections.unmodifiableMap(ICONS);
    }
}