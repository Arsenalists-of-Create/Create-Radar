package com.happysg.radar.api.radar.rwr;

public record RadarRwrEvaluation(boolean emitting, boolean detectable, boolean lockCapable, boolean locked, float signalStrength) {
    public static RadarRwrEvaluation notEmitting() {
        return new RadarRwrEvaluation(false, false, false, false, 0.0F);
    }
}