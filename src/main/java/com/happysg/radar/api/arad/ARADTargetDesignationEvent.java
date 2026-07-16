package com.happysg.radar.api.arad;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired on the game event bus when an ARAD-linked monitor assigns or clears a radar emitter target.
 * Consumers may use the RWR position to resolve whatever weapons belong to the receiver's platform.
 */
public final class ARADTargetDesignationEvent extends Event {
    public enum Action {
        ASSIGN,
        CLEAR
    }

    /**
     * The noisy target generated for an assignment. A Sable target supplies both its stable sublevel
     * identity and a target-local point; consumers should prefer those fields over the world fallback.
     */
    public record Target(
            BlockPos radarPos,
            @Nullable UUID emitterId,
            double rangeRatio,
            Vec3 noisyWorldPosition,
            @Nullable UUID targetSublevelId,
            @Nullable Vec3 targetLocalPosition
    ) {
        public Target {
            Objects.requireNonNull(radarPos, "radarPos");
            Objects.requireNonNull(noisyWorldPosition, "noisyWorldPosition");
            if ((targetSublevelId == null) != (targetLocalPosition == null)) {
                throw new IllegalArgumentException("A moving ARAD target requires both sublevel id and local position");
            }
        }
    }

    private final Action action;
    private final ServerLevel level;
    private final BlockPos rwrPos;
    private final BlockPos monitorPos;
    private final @Nullable String sourceId;
    private final @Nullable Target target;

    public ARADTargetDesignationEvent(
            Action action,
            ServerLevel level,
            BlockPos rwrPos,
            BlockPos monitorPos,
            @Nullable String sourceId,
            @Nullable Target target
    ) {
        this.action = Objects.requireNonNull(action, "action");
        this.level = Objects.requireNonNull(level, "level");
        this.rwrPos = Objects.requireNonNull(rwrPos, "rwrPos").immutable();
        this.monitorPos = Objects.requireNonNull(monitorPos, "monitorPos").immutable();
        this.sourceId = sourceId;
        this.target = target;

        if (action == Action.ASSIGN && (sourceId == null || sourceId.isBlank() || target == null)) {
            throw new IllegalArgumentException("An ARAD assignment requires a source id and target");
        }
        if (action == Action.CLEAR && target != null) {
            throw new IllegalArgumentException("An ARAD clear event cannot contain a target");
        }
    }

    public Action action() {
        return action;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos rwrPos() {
        return rwrPos;
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public @Nullable String sourceId() {
        return sourceId;
    }

    public @Nullable Target target() {
        return target;
    }
}
