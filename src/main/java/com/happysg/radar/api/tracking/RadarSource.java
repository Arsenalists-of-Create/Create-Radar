package com.happysg.radar.api.tracking;

import java.util.Collection;
import java.util.UUID;

public interface RadarSource {
    Collection<? extends RadarContact> getContacts();
    boolean isRunning();
    float getRange();
    UUID getEmitterId();
}
