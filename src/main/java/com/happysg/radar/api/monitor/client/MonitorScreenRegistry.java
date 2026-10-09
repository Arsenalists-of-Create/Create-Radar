package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MonitorScreenRegistry {
    private static final Map<ResourceLocation, MonitorScreenFactory> FACTORIES = new LinkedHashMap<>();
    private MonitorScreenRegistry() {}

    public static void register(ResourceLocation radarTypeId, MonitorScreenFactory factory) {
        Objects.requireNonNull(radarTypeId, "radarTypeId");
        Objects.requireNonNull(factory, "factory");

        MonitorScreenFactory existing = FACTORIES.putIfAbsent(radarTypeId, factory);

        if (existing != null) {
            throw new IllegalStateException("Monitor screen factory already registered for " + radarTypeId);
        }
    }

    public static Optional<MonitorScreenFactory> get(@Nullable ResourceLocation radarTypeId) {
        if (radarTypeId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(FACTORIES.get(radarTypeId));
    }

    public static @Nullable Screen create(ClientLevel level, BlockPos monitorPos, List<MonitorRadarSnapshot> radars) {
        if (level == null || monitorPos == null || radars == null || radars.isEmpty()) {
            return null;
        }

        for (MonitorRadarSnapshot radar : radars) {
            MonitorScreenFactory factory = FACTORIES.get(radar.displayProfile().typeId());

            if (factory == null) {
                continue;
            }

            Screen screen = factory.create(level, monitorPos, radars);

            if (screen != null) {
                return screen;
            }
        }

        return null;
    }
}