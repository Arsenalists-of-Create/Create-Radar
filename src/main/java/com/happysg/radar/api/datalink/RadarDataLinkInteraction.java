package com.happysg.radar.api.datalink;

/**
 * Server-side behavior for Data Link endpoint pairs that cannot use
 * Create: Radars' normal radar/monitor/controller attachment paths.
 */
@FunctionalInterface
public interface RadarDataLinkInteraction {

    /**
     * Attempts to complete a Data Link interaction.
     */
    Result interact(RadarDataLinkContext context);

    enum Result {
        PASS,
        SUCCESS,
        FAIL
    }
}