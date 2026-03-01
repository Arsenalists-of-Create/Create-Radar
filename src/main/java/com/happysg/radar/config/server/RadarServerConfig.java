package com.happysg.radar.config.server;


import com.simibubi.create.foundation.config.ConfigBase;

public class RadarServerConfig extends ConfigBase {
    @Override
    public String getName() {
        return "Radar Server";
    }

    public final ConfigInt radarLinkRange = i(128, 1, "radarLinkRange", "Maximum possible distance in blocks between radar links in blocks");
    public final ConfigInt monitorMaxSize = i(9, 1, "monitorMaxSize", "Maximum size of monitor MultiBlock");
    public final ConfigFloat radarGuidanceTurnRate = f(.15f, 0f, 1f, "radarGuidanceTurnRate", "Turn rate of radar guidance for CBCMW Missiles");
    public final ConfigInt leadFiringDelay = i(0,0,1000,"firingDelay", "The firing delay used in leading calculation. Higher values may prove useful in laggy environments");
    public final ConfigFloat controllerPhysbearingMaxSpeed = f(25,2,25,"controllerPhysbearingMaxSpeed", "Increases the max Rotational speed of phys bearings controlled by Pitch/Yaw controllers");
    public final ConfigInt binoRaycastRange = i(512,1,1000,"binocularRange", "The range at which the binocular can acquire a target");

    public final ConfigGroup radarStats = group(3,"radarStatsConfig","Configs for radar bearing and radar stats");
    public final ConfigInt maxRadarRange = i(1000, 1, "maxRadarRange", "Maximum range of a Radar Contraption in blocks");
    public final ConfigInt radarYScanRange = i(20, 1, "radarYScanRange", "Maximum vertical scan range of a radar in blocks");
    public final ConfigInt radarBaseRange = i(20, 1, "radarBaseRange", "Base range of a radar receiver in blocks");
    public final ConfigInt dishRangeIncrease = i(10, 1, "dishRangeIncrease", "Range increase per dish block in blocks");
    public final ConfigInt planeRadarRange = i(250,1,1000,"planeRadarRange","increases the range of the plane radar(in blocks)");
    public final ConfigBool gearRadarBearingSpeed = b(true, "gearRadarBearingSpeed", "If true, radar bearings will rotate slower the more dishes are connected to them");
    public final ConfigInt radarFOV = i(90, 1, 360, "radarFOV", "Field of view of a radar in degrees");

    public final ConfigGroup guidedFuzeConfig = group(3,"guidedFuzeConfig", "Configs for the guided fuze");
    public final ConfigFloat guidedFuzeMaxSeekDegrees  = f(30.0f,1.0f,180f,"guidedFuzeMaxSeekDegrees","The size of the cone the guided fuze can track targets from. Values are in degrees and are bi-directional");
    public final ConfigFloat guidedFuzeMaxDegreesPerTick = f(3,1,"guidedFuzeMaxDegreesPerTick", "The maximum number of degrees per tick the guided fuze can correct its course");
    public final ConfigBool guidedFuzeSeekBeforeApex = b(false,"guidedFuzeSeekBeforeApex","Determines if the guided fuze can seek its target before it has began to fall");
}