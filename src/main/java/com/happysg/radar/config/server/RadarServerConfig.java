package com.happysg.radar.config.server;

import com.simibubi.create.foundation.config.ConfigBase;


public class RadarServerConfig extends ConfigBase {
    @Override
    public String getName() {
        return "Radar Server";
    }

    public final ConfigBase.ConfigInt radarLinkRange = i(128, 1, "radarLinkRange", "Maximum possible distance in blocks between radar links in blocks");
    public final ConfigInt maxRadarRange = i(1000, 1, "maxRadarRange", "Maximum range of a Radar Contraption in blocks");
    public final ConfigInt monitorMaxSize = i(9, 1, "monitorMaxSize", "Maximum size of monitor MultiBlock");
    public final ConfigInt dishRangeIncrease = i(10, 1, "dishRangeIncrease", "Range increase per dish block in blocks");
    public final ConfigInt radarYScanRange = i(20, 1, "radarYScanRange", "Maximum vertical scan range of a radar in blocks");
    public final ConfigInt radarBaseRange = i(20, 1, "radarBaseRange", "Base range of a radar receiver in blocks");
    public final ConfigInt radarFOV = i(90, 1, 360, "radarFOV", "Field of view of a radar in degrees");
    public final ConfigFloat radarGuidanceTurnRate = f(.15f, 0f, 1f, "radarGuidanceTurnRate", "Turn rate of radar guidance for CBCMW Missiles");

    public final ConfigBool gearRadarBearingSpeed = b(true, "gearRadarBearingSpeed", "If true, radar bearings will rotate slower the more dishes are connected to them");
}