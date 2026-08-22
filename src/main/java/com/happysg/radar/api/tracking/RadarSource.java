package com.happysg.radar.api.tracking;

import java.util.Collection;
import java.util.UUID;

/**
 * Public view of a radar source.
 */
public interface RadarSource {

    Collection<? extends RadarContact> getContacts();

    boolean isRunning();

    float getRange();

    UUID getEmitterId();
}