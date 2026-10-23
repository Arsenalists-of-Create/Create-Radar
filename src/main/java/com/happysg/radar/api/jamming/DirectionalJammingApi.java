package com.happysg.radar.api.jamming;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarContactCategory;
import com.happysg.radar.block.arad.jammer.DirectionalJammingService;
import com.happysg.radar.block.radar.track.RadarContactTrackAdapter;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public integration for directional radar jamming.
 * Callers provide ordinary public {@link RadarContact}s.
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
    ) {}

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
     * Applies directional-jamming effects to an external radar
     * emitter's canonical contacts.
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


    /**
     * Registers or refreshes an external jammer contribution.
     *
     * @param level server level containing the jammer
     * @param jammerPos canonical jammer block position
     * @param targetSourceId target radar's source ID
     * @param jammerWorldPosition jammer position in world space
     * @param targetRadarWorldPosition target radar in world space
     * @param jammerRpm current jammer RPM
     * @param radarRollingRpm target radar rolling RPM
     * @param radarRollingRate target radar rolling RPM rate
     * @param alignmentDegrees angular alignment error in degrees
     */
    public static void heartbeatJammer(
            ServerLevel level,
            BlockPos jammerPos,
            String targetSourceId,
            Vec3 jammerWorldPosition,
            Vec3 targetRadarWorldPosition,
            float jammerRpm,
            float radarRollingRpm,
            float radarRollingRate,
            float alignmentDegrees
    ) {
        DirectionalJammingService.heartbeat(
                level,
                jammerPos,
                targetSourceId,
                jammerWorldPosition,
                targetRadarWorldPosition,
                jammerRpm,
                radarRollingRpm,
                radarRollingRate,
                alignmentDegrees
        );
    }

    /**
     * Immediately removes an external jammer contribution.
     */
    public static void removeJammer(ServerLevel level, BlockPos jammerPos) {
        DirectionalJammingService.remove(level, jammerPos);
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