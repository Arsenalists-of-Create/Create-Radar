package com.happysg.radar.block.radar.track;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarContactCategory;

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

        return new RadarTrack(
                id,
                contact.getPosition(),
                contact.getVelocity(),
                gameTime,
                toInternalCategory(contact.getCategory()),
                "create_radar:external_contact",
                1.0F
        );
    }

    private static TrackCategory toInternalCategory(RadarContactCategory category) {
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