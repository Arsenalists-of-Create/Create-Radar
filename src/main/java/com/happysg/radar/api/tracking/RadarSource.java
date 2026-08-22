package com.happysg.radar.api.tracking;

import java.util.Collection;
import java.util.UUID;

/**
 * Public view of a radar source.
 */
public interface RadarSource {

    /**
     * @return currently reported radar contacts
     */
    Collection<? extends RadarContact> getContacts();

    /**
     * @return {@code true} if this radar is currently operational and emitting
     */
    boolean isRunning();

    /**
     * Returns the radar's current effective detection range.
     *
     * @return range in blocks
     */
    float getRange();

    /**
     * @return stable emitter identifier
     */
    UUID getEmitterId();
}