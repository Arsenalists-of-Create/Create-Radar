package com.happysg.radar.api.monitor;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record MonitorContactSnapshot(String id, Vec3 position, boolean selectable, String sourceType) {
    public MonitorContactSnapshot {
        id = Objects.requireNonNull(id, "id");
        position = Objects.requireNonNull(position, "position");
        sourceType = Objects.requireNonNullElse(sourceType, "generic");
    }
}