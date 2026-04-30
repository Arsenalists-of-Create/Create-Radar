package com.happysg.radar.config.server;

import net.createmod.catnip.config.ConfigBase;

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
    public final ConfigInt radarBaseRange = i(0, 0, "radarBaseRange", "Base range of a radar receiver in blocks");
    public final ConfigInt dishRangeIncrease = i(16, 1, "dishRangeIncrease", "Range increase per dish block in blocks");
    public final ConfigInt planeRadarRange = i(250,1,1000,"planeRadarRange","increases the range of the plane radar(in blocks)");
    public final ConfigBool gearRadarBearingSpeed = b(false, "gearRadarBearingSpeed", "If true, radar bearings will rotate slower the more dishes are connected to them");
    public final ConfigInt radarFOV = i(90, 1, 360, "radarFOV", "Field of view of a radar in degrees");
    public final ConfigFloat radarRotationMultiplier = f(2.0f, 1.0f, 10.0f, "radarRotationMultiplier", "Multiplier for the radar rotation speed (scanning logic only)");
    public final ConfigFloat radarSpeedMultiplier = f(2.0f, 1.0f, 10.0f, "radarSpeedMultiplier", "Multiplier for the physical rotation speed of radar bearings");

    public final ConfigGroup weaponControl = group(3, "weaponControlConfig", "Configs for weapon aiming and firing");
    public final ConfigFloat autoFireTolerance = f(10.0f, 0.1f, 10.0f, "autoFireTolerance", "Angular tolerance in degrees for the cannon to fire. Higher values allow firing even if not perfectly aimed.");
    public final ConfigFloat autoFireStabilityEps = f(5.0f, 0.1f, 10.0f, "autoFireStabilityEps", "The maximum target movement in blocks between ticks to consider the aim stable.");
    public final ConfigInt autoFireStabilityTicks = i(0, 0, 100, "autoFireStabilityTicks", "How many ticks the aim must be stable before firing. Set to 0 for instant firing.");
    public final ConfigFloat autoFireLatencyTicks = f(3.0f, 0.0f, 20.0f, "autoFireLatencyTicks", "Additional lead time in ticks to compensate for network and processing delays. Increase if hitting the tail.");
    public final ConfigFloat autoFireLeadMultiplier = f(1.2f, 0.1f, 5.0f, "autoFireLeadMultiplier", "Multiplies the calculated lead. Use > 1.0 if hitting the tail, < 1.0 if overshooting.");
    public final ConfigFloat autoFireAimOffset = f(0.0f, -10.0f, 10.0f, "autoFireAimOffset", "Vertical/Horizontal aim offset in blocks for fine tuning.");

    public final ConfigGroup guidedFuzeConfig = group(3,"guidedFuzeConfig", "Configs for the guided fuze");
    public final ConfigFloat guidedFuzeMaxSeekDegrees  = f(30.0f,1.0f,180f,"guidedFuzeMaxSeekDegrees","The size of the cone the guided fuze can track targets from. Values are in degrees and are bi-directional");
    public final ConfigFloat guidedFuzeMaxDegreesPerTick = f(3,1,"guidedFuzeMaxDegreesPerTick", "The maximum number of degrees per tick the guided fuze can correct its course");
    public final ConfigBool guidedFuzeSeekBeforeApex = b(false,"guidedFuzeSeekBeforeApex","Determines if the guided fuze can seek its target before it has began to fall");
}