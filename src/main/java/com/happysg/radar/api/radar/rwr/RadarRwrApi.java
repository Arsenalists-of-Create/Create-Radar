package com.happysg.radar.api.radar.rwr;

import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.api.arad.ARADTargeting;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class RadarRwrApi {
    private RadarRwrApi() {}

    public static void heartbeat(ServerLevel level, RadarRwrEmitter emitter) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(emitter, "emitter");
        ARADTargeting.heartbeatRwrEmitter(level, emitter);
    }

    public static void heartbeatVirtual(ServerLevel level, RadarVirtualRwrEmitter emitter, int ttlTicks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(emitter, "emitter");

        ExternalRwrEmitterRegistry.SelectionMetadata metadata = emitter.selectionMetadata() == null ? null : new ExternalRwrEmitterRegistry.SelectionMetadata(
                emitter.selectionMetadata().emitterId(),
                emitter.selectionMetadata().rollingRpm(),
                emitter.selectionMetadata().rollingRate()
        );

        ExternalRwrEmitterRegistry.EmitterState state = new ExternalRwrEmitterRegistry.EmitterState(
                emitter.sourceId(),
                emitter.position(),
                emitter.forward(),
                emitter.range(),
                emitter.halfAngleDegrees(),
                emitter.targetShipId(),
                ExternalRwrEmitterRegistry.ThreatStage.valueOf(emitter.stage().name()),
                emitter.radarTypeId(),
                emitter.requireLineOfSight(),
                metadata
        );

        ExternalRwrEmitterRegistry.heartbeat(level, state, ttlTicks);
    }

    public static void removeVirtual(ServerLevel level, String sourceId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sourceId, "sourceId");
        ExternalRwrEmitterRegistry.remove(level, sourceId);
    }
}