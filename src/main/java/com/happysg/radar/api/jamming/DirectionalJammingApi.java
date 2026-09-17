package com.happysg.radar.api.jamming;

import com.happysg.radar.block.arad.jammer.DirectionalJammingService;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public integration surface for radar emitters supplied by other mods. */
public final class DirectionalJammingApi {
    private DirectionalJammingApi() {
    }

    public record EmitterContext(
            String sourceId,
            UUID emitterId,
            Vec3 position,
            Vec3 forward,
            double range,
            float halfAngleDegrees
    ) {
    }

    public record GuidanceObservation(
            Vec3 position,
            Vec3 velocity,
            long scannedTime,
            boolean jammed,
            boolean synthetic,
            long sampleToken
    ) {
    }

    public static Collection<RadarTrack> reportedTracks(
            ServerLevel level,
            EmitterContext emitter,
            Collection<RadarTrack> rawTracks
    ) {
        if (emitter == null) {
            return rawTracks == null ? List.of() : rawTracks;
        }
        return DirectionalJammingService.reportedExternalTracks(level,
                emitter.sourceId(), emitter.emitterId(), emitter.position(),
                emitter.forward(), emitter.range(), emitter.halfAngleDegrees(),
                rawTracks);
    }

    /**
     * Resolves a reported track for guidance. Canonical coordinates are used as
     * the clean baseline so the complete guidance offset is applied exactly once.
     */
    public static GuidanceObservation resolveGuidance(
            RadarTrack track,
            Vec3 canonicalPosition,
            Vec3 canonicalVelocity
    ) {
        if (track == null) {
            return null;
        }
        RadarTrack.JammingData jamming = track.getJammingData();
        boolean synthetic = track.isSynthetic();
        boolean canonical = !synthetic && finite(canonicalPosition)
                && finite(canonicalVelocity);
        Vec3 position = canonical ? canonicalPosition : track.position();
        Vec3 velocity = canonical ? canonicalVelocity : track.velocity();
        if (canonical && jamming != null) {
            position = position.add(jamming.guidancePositionOffset());
            velocity = velocity.add(jamming.guidanceVelocityOffset());
        }
        return new GuidanceObservation(position, velocity, track.scannedTime(),
                jamming != null, synthetic,
                jamming == null ? 0L : jamming.sampleToken());
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
