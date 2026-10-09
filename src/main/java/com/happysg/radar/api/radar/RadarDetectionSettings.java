package com.happysg.radar.api.radar;

/**
 * Public detection-category settings supplied by a Create: Radars network.
 * This intentionally describes what categories a sensor should detect.
 * Identification, IFF, friend/foe and display configuration are separate concerns.
 */
public record RadarDetectionSettings(
        boolean player,
        boolean sable,
        boolean contraption,
        boolean mob,
        boolean projectile,
        boolean animal,
        boolean item
) { }