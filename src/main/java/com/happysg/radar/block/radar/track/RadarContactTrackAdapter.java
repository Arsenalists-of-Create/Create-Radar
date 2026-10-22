package com.happysg.radar.block.radar.track;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarContactCategory;
import com.happysg.radar.api.jamming.DirectionalJammingApi;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class RadarContactTrackAdapter {

    private RadarContactTrackAdapter() {}

    @Nullable
    public static RadarTrack toTrack(RadarContact contact, long gameTime) {
        if (contact == null) {
            return null;
        }

        if (contact instanceof RadarTrack track) {
            return track.copy();
        }

        String id = contact.getId();

        if (id == null || id.isBlank()) {
            return null;
        }

        if (contact.getPosition() == null || contact.getVelocity() == null || contact.getCategory() == null) {
            return null;
        }

        DirectionalJammingApi.ReportedContact reported = contact instanceof DirectionalJammingApi.ReportedContact value ? value : null;

        RadarTrack track = new RadarTrack(
                id,
                contact.getPosition(),
                contact.getVelocity(),
                reported == null ? gameTime : reported.scannedTime(),
                toInternalCategory(contact.getCategory()),
                reported == null || reported.entityType() == null ? "create_radar:external_contact" : reported.entityType(),
                reported == null ? 1.0F : reported.entityHeight()
        );

        if (reported != null) {
            track.setFriendly(reported.friendly());
            track.setSynthetic(reported.synthetic());

            DirectionalJammingApi.JammingState jamming = reported.jamming();

            if (jamming != null) {
                track.setJammingData(new RadarTrack.JammingData(
                        jamming.radarSourceId(),
                        jamming.outerStrength(),
                        jamming.directionalStrength(),
                        jamming.severeStrength(),
                        jamming.friendlyOutageChance(),
                        jamming.guidancePositionOffset(),
                        jamming.guidanceVelocityOffset(),
                        jamming.sampleToken()
                ));
            }
        }

        return track;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static TrackCategory toInternalCategory(RadarContactCategory category) {
        return switch (category) {
            case PLAYER -> TrackCategory.PLAYER;
            case MOB -> TrackCategory.MOB;
            case HOSTILE -> TrackCategory.HOSTILE;
            case ANIMAL -> TrackCategory.ANIMAL;
            case SABLE -> TrackCategory.SABLE;
            case PROJECTILE -> TrackCategory.PROJECTILE;
            case CONTRAPTION -> TrackCategory.CONTRAPTION;
            case ITEM -> TrackCategory.ITEM;
            case MISC -> TrackCategory.MISC;
            case MISSILE -> TrackCategory.MISSILE;
        };
    }
}