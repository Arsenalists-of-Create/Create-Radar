package com.happysg.radar.api.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public record MonitorWorldHitTestContext(
        Level level,
        BlockPos monitorPos,
        Direction monitorFacing,
        Vec3 worldHitPosition,

        double normalizedX,
        double normalizedZ,

        double displayXOffset,
        double displayZOffset,

        int monitorWidth,
        int monitorHeight,
        List<MonitorRadarSnapshot> radars,
        List<MonitorContactSnapshot> contacts
) {
    public MonitorWorldHitTestContext {
        level = Objects.requireNonNull(level, "level");
        monitorPos = Objects.requireNonNull(monitorPos, "monitorPos").immutable();
        monitorFacing = Objects.requireNonNull(monitorFacing, "monitorFacing");
        worldHitPosition = Objects.requireNonNull(worldHitPosition, "worldHitPosition");
        radars = radars == null ? List.of() : List.copyOf(radars);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        monitorWidth = Math.max(1, monitorWidth);
        monitorHeight = Math.max(1, monitorHeight);
    }
}