package com.happysg.radar.api.datalink;

/**
 * Describes how an endpoint participates in a Create: Radars network.
 */
public enum RadarDataLinkEndpointType {

    /**
     * A radar-like source that contributes contacts to the network.
     */
    RADAR,

    /**
     * A display/monitor endpoint.
     */
    MONITOR,

    /**
     * A controller participating in the weapon/control side of a network.
     */
    WEAPON_CONTROLLER,

    /**
     * The endpoint handles Data Link interaction through a custom
     * RadarDataLinkInteraction rather than the normal attachment path.
     */
    CUSTOM
}