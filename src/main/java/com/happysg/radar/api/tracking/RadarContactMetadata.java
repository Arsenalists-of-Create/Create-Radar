package com.happysg.radar.api.tracking;

public interface RadarContactMetadata {
    default String getRadarEntityType() { return "create_radar:external_contact"; }
    default float getRadarEntityHeight() { return 1.0F; }
    default boolean isRadarFriendly() { return false; }
    default boolean isRadarSynthetic() { return false; }
    default long getRadarScannedTime() { return -1L; }
}
