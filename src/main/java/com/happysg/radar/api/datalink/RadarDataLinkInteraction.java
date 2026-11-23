package com.happysg.radar.api.datalink;

@FunctionalInterface
public interface RadarDataLinkInteraction {
    Result interact(RadarDataLinkContext context);
    enum Result { PASS, SUCCESS, FAIL }
}
