package com.happysg.radar.api.radar;

/**
 * Optional capability for a radar source that accepts network detection filters.
 */
public interface RadarDetectionConfigurable {
    void applyRadarDetectionSettings(RadarDetectionSettings settings);
}