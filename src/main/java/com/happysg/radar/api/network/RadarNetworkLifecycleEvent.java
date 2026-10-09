package com.happysg.radar.api.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

import java.util.Objects;

public class RadarNetworkLifecycleEvent extends Event {

    public enum Action {
        ATTACHED,
        DETACHED,
        MOVED,
        RECONCILED
    }

    private final ServerLevel level;
    private final BlockPos filtererPos;
    private final BlockPos radarPos;
    private final BlockPos previousRadarPos;
    private final Action action;

    public RadarNetworkLifecycleEvent(
            ServerLevel level,
            BlockPos filtererPos,
            BlockPos radarPos,
            BlockPos previousRadarPos,
            Action action
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.filtererPos = Objects.requireNonNull(filtererPos, "filtererPos").immutable();
        this.radarPos = Objects.requireNonNull(radarPos, "radarPos").immutable();
        this.previousRadarPos = previousRadarPos == null ? null : previousRadarPos.immutable();
        this.action = Objects.requireNonNull(action, "action");
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos filtererPos() {
        return filtererPos;
    }

    public BlockPos radarPos() {
        return radarPos;
    }

    public BlockPos previousRadarPos() {
        return previousRadarPos;
    }

    public Action action() {
        return action;
    }
}