package com.happysg.radar.api.radar;

import com.happysg.radar.CreateRadar;

public final class RadarDisplayProfiles {

    public static final RadarDisplayProfile GENERIC = new RadarDisplayProfile(
            CreateRadar.asResource("generic"),
            RadarSweepStyle.NONE,
            false,
            true
    );

    public static final RadarDisplayProfile GROUND = new RadarDisplayProfile(
            CreateRadar.asResource("ground"),
            RadarSweepStyle.ROTATING,
            false,
            true
    );

    public static final RadarDisplayProfile SKY = new RadarDisplayProfile(
            CreateRadar.asResource("sky"),
            RadarSweepStyle.ROTATING,
            true,
            true
    );

    public static final RadarDisplayProfile AIRBORNE = new RadarDisplayProfile(
            CreateRadar.asResource("airborne"),
            RadarSweepStyle.OSCILLATING,
            true,
            true
    );

    public static final RadarDisplayProfile SONAR = new RadarDisplayProfile(
            CreateRadar.asResource("sonar"),
            RadarSweepStyle.PULSE,
            false,
            true
    );

    private RadarDisplayProfiles() {}
}