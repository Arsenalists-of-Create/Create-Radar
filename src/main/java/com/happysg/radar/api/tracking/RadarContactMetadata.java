
package com.happysg.radar.api.tracking;

/**
 * Optional metadata for externally supplied radar contacts.
 * Implement alongside RadarContact to provide additional
 * tracking information
 */
public interface RadarContactMetadata {

    /**
     * Entity type identifier used for display and filtering.
     */
    default String getRadarEntityType() {
        return "create_radar:external_contact";
    }

    /**
     * Approximate entity height in blocks.
     */
    default float getRadarEntityHeight() {
        return 1.0F;
    }

    /**
     * Whether this contact is considered friendly.
     */
    default boolean isRadarFriendly() {
        return false;
    }

    /**
     * Whether this contact represents a synthetic target.
     */
    default boolean isRadarSynthetic() {
        return false;
    }

    /**
     * Server game tick at which the contact was last observed.
     * A negative value requests the current game tick.
     */
    default long getRadarScannedTime() {
        return -1L;
    }
}
