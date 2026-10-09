package com.happysg.radar.api.jamming;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarContactCategory;
import com.happysg.radar.block.arad.jammer.DirectionalJammingService;
import com.happysg.radar.block.radar.track.RadarContactTrackAdapter;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public integration for directional radar jamming.
 * Callers provide ordinary public {@link RadarContact}s. Create: Radars
 * performs all native track conversion and jamming internally.
 */
public final class DirectionalJammingApi {
    private DirectionalJammingApi() {}

    public record EmitterContext(
            String sourceId,
            UUID emitterId,
            Vec3 position,
            Vec3 forward,
            double range,
            float halfAngleDegrees
    ) { }

    /**
     * Public description of jamming state attached to a reported contact.
     */
    public record JammingState(
            String radarSourceId,
            float outerStrength,
            float directionalStrength,
            float severeStrength,
            float friendlyOutageChance,
            Vec3 guidancePositionOffset,
            Vec3 guidanceVelocityOffset,
            long sampleToken
    ) {
        public JammingState {
            radarSourceId = radarSourceId == null ? "" : radarSourceId;
            guidancePositionOffset = guidancePositionOffset == null ? Vec3.ZERO : guidancePositionOffset;
            guidanceVelocityOffset = guidanceVelocityOffset == null ? Vec3.ZERO : guidanceVelocityOffset;
        }

        public float priorityScore() {
            return severeStrength * 100.0F + directionalStrength * 10.0F + outerStrength;
        }
    }

    /**
     * Public contact returned by the directional-jamming system.
     * The position and velocity represent the contact as actually observed by the emitter
     */
    public record ReportedContact(
            String id,
            Vec3 position,
            Vec3 velocity,
            RadarContactCategory category,
            long scannedTime,
            String entityType,
            float entityHeight,
            boolean friendly,
            boolean synthetic,
            @Nullable JammingState jamming
    ) implements RadarContact {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Vec3 getPosition() {
            return position;
        }

        @Override
        public Vec3 getVelocity() {
            return velocity;
        }

        @Override
        public RadarContactCategory getCategory() {
            return category;
        }

        public boolean jammed() {
            return jamming != null;
        }
    }

    public record GuidanceObservation(
            Vec3 position,
            Vec3 velocity,
            long scannedTime,
            boolean jammed,
            boolean synthetic,
            long sampleToken
    ) { }

    /**
     * Applies Create: Radars directional-jamming effects to an external radar
     * emitter's canonical contacts.
     * The supplied contacts should represent the emitter's clean/raw
     * observations. Returned contacts may contain corrupted positions,
     * velocities, dropouts, or synthetic contacts.
     */
    public static Collection<RadarContact> reportedTracks(ServerLevel level, EmitterContext emitter, Collection<? extends RadarContact> rawContacts) {
        if (rawContacts == null) {
            return List.of();
        }

        if (emitter == null) {
            return List.copyOf(rawContacts);
        }

        if (level == null) {
            return List.copyOf(rawContacts);
        }

        List<RadarTrack> internal = new ArrayList<>(rawContacts.size());

        for (RadarContact contact : rawContacts) {
            RadarTrack track = RadarContactTrackAdapter.toTrack(contact, level.getGameTime());

            if (track != null) {
                internal.add(track);
            }
        }

        Collection<RadarTrack> reported = DirectionalJammingService.reportedExternalTracks(
                level,
                emitter.sourceId(),
                emitter.emitterId(),
                emitter.position(),
                emitter.forward(),
                emitter.range(),
                emitter.halfAngleDegrees(),
                internal
        );

        if (reported == null || reported.isEmpty()) {
            return List.of();
        }

        List<RadarContact> result = new ArrayList<>(reported.size());

        for (RadarTrack track : reported) {
            if (track != null) {
                result.add(toPublic(track));
            }
        }

        return List.copyOf(result);
    }

    /**
     * Resolves a reported contact for weapon guidance.
     */
    @Nullable
    public static GuidanceObservation resolveGuidance(RadarContact contact, Vec3 canonicalPosition, Vec3 canonicalVelocity) {
        if (contact == null) {
            return null;
        }

        ReportedContact reported = contact instanceof ReportedContact value ? value : null;

        boolean synthetic = reported != null && reported.synthetic();

        JammingState jamming = reported == null ? null : reported.jamming();

        boolean canonical = !synthetic && finite(canonicalPosition) && finite(canonicalVelocity);

        Vec3 position = canonical ? canonicalPosition : contact.getPosition();
        Vec3 velocity = canonical ? canonicalVelocity : contact.getVelocity();

        if (canonical && jamming != null) {
            position = position.add(jamming.guidancePositionOffset());

            velocity = velocity.add(jamming.guidanceVelocityOffset());
        }

        long scannedTime = reported == null ? 0L : reported.scannedTime();

        return new GuidanceObservation(
                position,
                velocity,
                scannedTime,
                jamming != null,
                synthetic,
                jamming == null ? 0L : jamming.sampleToken()
        );
    }

    private static ReportedContact toPublic(RadarTrack track) {
        RadarTrack.JammingData internal = track.getJammingData();

        JammingState jamming =
                internal == null
                        ? null
                        : new JammingState(
                        internal.radarSourceId(),
                        internal.outerStrength(),
                        internal.directionalStrength(),
                        internal.severeStrength(),
                        internal.friendlyOutageChance(),
                        internal.guidancePositionOffset(),
                        internal.guidanceVelocityOffset(),
                        internal.sampleToken()
                );

        return new ReportedContact(
                track.getId(),
                track.getPosition(),
                track.getVelocity(),
                track.getCategory(),
                track.getScannedTime(),
                track.getEntityType(),
                track.getEnityHeight(),
                track.isFriendly(),
                track.isSynthetic(),
                jamming
        );
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}