package com.happysg.radar.api.monitor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MonitorRadarData {

    private static final MonitorRadarData EMPTY = new MonitorRadarData(Map.of());
    private final Map<ResourceLocation, CompoundTag> entries;

    public MonitorRadarData(Map<ResourceLocation, CompoundTag> entries) {
        Objects.requireNonNull(entries, "entries");
        Map<ResourceLocation, CompoundTag> copy = new LinkedHashMap<>();

        entries.forEach((id, tag) -> {
            if (id != null && tag != null) {
                copy.put(id, tag.copy());
            }
        });

        this.entries = Collections.unmodifiableMap(copy);
    }

    public static MonitorRadarData empty() {
        return EMPTY;
    }

    public Optional<CompoundTag> get(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }

        CompoundTag tag = entries.get(id);
        return tag == null ? Optional.empty() : Optional.of(tag.copy());
    }

    public boolean has(ResourceLocation id) {
        return id != null && entries.containsKey(id);
    }

    public Map<ResourceLocation, CompoundTag> entries() {
        Map<ResourceLocation, CompoundTag> copy = new LinkedHashMap<>();
        entries.forEach((id, tag) -> copy.put(id, tag.copy()));
        return Collections.unmodifiableMap(copy);
    }
}