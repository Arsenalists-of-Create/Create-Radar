package com.happysg.radar.api.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

import java.util.Objects;

public class RadarNetworkControllerMovedEvent extends Event {
    private final ServerLevel level;
    private final BlockPos previousPosition;
    private final BlockPos position;

    public RadarNetworkControllerMovedEvent(ServerLevel level, BlockPos previousPosition, BlockPos position) {
        this.level = Objects.requireNonNull(level, "level");
        this.previousPosition = Objects.requireNonNull(previousPosition, "previousPosition").immutable();
        this.position = Objects.requireNonNull(position, "position").immutable();
    }

    public ServerLevel level() { return level; }
    public BlockPos previousPosition() { return previousPosition; }
    public BlockPos position() { return position; }
}