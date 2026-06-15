package com.happysg.radar.config.server;

import net.createmod.catnip.config.ConfigBase;

public class RadarServerConfig extends ConfigBase {
    public final ConfigInt radarLinkRange = this.i(128, 1, "radarLinkRange", "Maximum possible distance in blocks between radar links in blocks");
    public final ConfigInt monitorMaxSize = this.i(9, 1, "monitorMaxSize", "Maximum size of monitor MultiBlock");
    public final ConfigFloat targetLoosenThreshold = this.f(3.0F, 0.0F, "targetLoosenThreshold", "how fast a target must be moving before looser firing conditions are applied (value is in m/s)");
    public final ConfigInt targetLoosenAmount = this.i(15, 0, 180, "targetLoosenAmount", "increases the tolerance used to determine if cannons are pointing at the target (degrees)");
    public final ConfigBool useNewTargetingComputer = this.b(true, "useNewTargetingComputer", "Use the new simulated targeting computer as the primary CBC auto aiming path");
    public final ConfigBool forceLegacyCannonLeadSolver = this.b(false, "forceLegacyCannonLeadSolver", "Force CBC auto aiming to use the legacy lead solver instead of the new targeting computer");
    public final ConfigBool allowLegacyCannonLeadFallback = this.b(true, "allowLegacyCannonLeadFallback", "Allow the legacy lead solver when the new targeting computer cannot produce a usable shot");
    public final ConfigFloat sprintJumpMinHorizontalSpeed = this.f(0.16F, 0.0F, "sprintJumpMinHorizontalSpeed", "Minimum horizontal target speed in blocks per tick for sprint-jump movement to count as erratic");
    public final ConfigFloat sprintJumpVerticalSpeedThreshold = this.f(0.12F, 0.0F, "sprintJumpVerticalSpeedThreshold", "Minimum upward target speed in blocks per tick for sprint-jump movement to count as erratic");
    public final ConfigInt erraticTargetStableTicks = this.i(1, 0, 20, "erraticTargetStableTicks", "Required stable aim ticks when target motion is erratic");
    public final ConfigInt leadFiringDelay = this.i(0, 0, 1000, "firingDelay", "The firing delay used in leading calculation. Higher values may prove useful in laggy environments");
    public final ConfigFloat controllerPhysbearingMaxSpeed = this.f(25.0F, 2.0F, 25.0F, "controllerPhysbearingMaxSpeed", "Increases the max Rotational speed of phys bearings controlled by Pitch/Yaw controllers");
    public final ConfigInt binoRaycastRange = this.i(512, 1, 1000, "binocularRange", "The range at which the binocular can acquire a target");
    public final ConfigGroup radarStats = this.group(3, "radarStatsConfig", "Configs for radar bearing and radar stats");
    public final ConfigInt maxRadarRange = this.i(1000, 1, "maxRadarRange", "Maximum range of a Radar Contraption in blocks");
    public final ConfigInt radarYScanRange = this.i(20, 1, "radarYScanRange", "Maximum vertical scan range of a radar in blocks");
    public final ConfigInt radarBaseRange = this.i(20, 1, "radarBaseRange", "Base range of a radar receiver in blocks");
    public final ConfigInt dishRangeIncrease = this.i(10, 1, "dishRangeIncrease", "Range increase per dish block in blocks");
    public final ConfigInt planeRadarRange = this.i(250, 1, 1000, "planeRadarRange", "increases the range of the plane radar(in blocks)");
    public final ConfigBool gearRadarBearingSpeed = this.b(true, "gearRadarBearingSpeed", "If true, radar bearings will rotate slower the more dishes are connected to them");
    public final ConfigInt radarFOV = this.i(90, 1, 360, "radarFOV", "Field of view of a radar in degrees");
    public final ConfigGroup guidedFuzeConfig = this.group(3, "guidedFuzeConfig", "Configs for the guided fuze");
    public final ConfigFloat guidedFuzeMaxSeekDegrees = this.f(180.0F, 1.0F, 180.0F, "guidedFuzeMaxSeekDegrees", "The size of the cone the guided fuze can track targets from. Values are in degrees and are bi-directional (180 = full circle)");
    public final ConfigFloat guidedFuzeMaxDegreesPerTick = this.f(3.0F, 1.0F, "guidedFuzeMaxDegreesPerTick", "The maximum number of degrees per tick the guided fuze can correct its course");
    public final ConfigBool guidedFuzeSeekBeforeApex = this.b(false, "guidedFuzeSeekBeforeApex", "Determines if the guided fuze can seek its target before it has began to fall");

    public String getName() {
        return "Radar Server";
    }
}
