package com.happysg.radar.config.server;

import net.createmod.catnip.config.ConfigBase;

public class RadarServerConfig extends ConfigBase {
    public final ConfigBase.ConfigInt radarLinkRange = this.i(128, 1, "radarLinkRange", new String[]{"Maximum possible distance in blocks between radar links in blocks"});
    public final ConfigBase.ConfigInt monitorMaxSize = this.i(9, 1, "monitorMaxSize", new String[]{"Maximum size of monitor MultiBlock"});
    public final ConfigBase.ConfigFloat targetLoosenThreshold = this.f(3.0F, 0.0F, "targetLoosenThreshold", new String[]{"how fast a target must be moving before looser firing conditions are applied (value is in m/s)"});
    public final ConfigBase.ConfigInt targetLoosenAmount = this.i(15, 0, 180, "targetLoosenAmount", new String[]{"increases the tolerance used to determine if cannons are pointing at the target (degrees)"});
    public final ConfigBase.ConfigBool useNewTargetingComputer = this.b(true, "useNewTargetingComputer", new String[]{"Use the new simulated targeting computer as the primary CBC auto aiming path"});
    public final ConfigBase.ConfigBool forceLegacyCannonLeadSolver = this.b(false, "forceLegacyCannonLeadSolver", new String[]{"Force CBC auto aiming to use the legacy lead solver instead of the new targeting computer"});
    public final ConfigBase.ConfigBool allowLegacyCannonLeadFallback = this.b(true, "allowLegacyCannonLeadFallback", new String[]{"Allow the legacy lead solver when the new targeting computer cannot produce a usable shot"});
    public final ConfigBase.ConfigInt leadFiringDelay = this.i(0, 0, 1000, "firingDelay", new String[]{"The firing delay used in leading calculation. Higher values may prove useful in laggy environments"});
    public final ConfigBase.ConfigFloat sprintJumpMinHorizontalSpeed = this.f(0.12F, 0.0F, "sprintJumpMinHorizontalSpeed", new String[]{"Minimum horizontal target speed in blocks/tick for sprint-jump movement classification"});
    public final ConfigBase.ConfigFloat sprintJumpVerticalSpeedThreshold = this.f(0.12F, 0.0F, "sprintJumpVerticalSpeedThreshold", new String[]{"Minimum vertical target speed in blocks/tick for sprint-jump movement classification"});
    public final ConfigBase.ConfigInt sprintJumpStableTicks = this.i(1, 0, 20, "sprintJumpStableTicks", new String[]{"Stable aim ticks required before firing at sprint-jumping targets"});
    public final ConfigBase.ConfigFloat sprintJumpMinConfidence = this.f(0.08F, 0.0F, 1.0F, "sprintJumpMinConfidence", new String[]{"Minimum new solver confidence required for sprint-jumping targets"});
    public final ConfigBase.ConfigInt erraticTargetStableTicks = this.i(0, 0, 20, "erraticTargetStableTicks", new String[]{"Stable aim ticks required before firing at elytra or erratic targets"});
    public final ConfigBase.ConfigFloat erraticTargetMinConfidence = this.f(0.03F, 0.0F, 1.0F, "erraticTargetMinConfidence", new String[]{"Minimum new solver confidence required for elytra or erratic targets"});
    public final ConfigBase.ConfigFloat erraticTargetAimStableEpsilon = this.f(1.5F, 0.0F, "erraticTargetAimStableEpsilon", new String[]{"Allowed aim point movement in blocks before resetting stability for elytra or erratic targets"});
    public final ConfigBase.ConfigFloat controllerPhysbearingMaxSpeed = this.f(25.0F, 2.0F, 25.0F, "controllerPhysbearingMaxSpeed", new String[]{"Increases the max Rotational speed of phys bearings controlled by Pitch/Yaw controllers"});
    public final ConfigBase.ConfigInt binoRaycastRange = this.i(512, 1, 1000, "binocularRange", new String[]{"The range at which the binocular can acquire a target"});
    public final ConfigBase.ConfigGroup radarStats = this.group(3, "radarStatsConfig", new String[]{"Configs for radar bearing and radar stats"});
    public final ConfigBase.ConfigInt maxRadarRange = this.i(1000, 1, "maxRadarRange", new String[]{"Maximum range of a Radar Contraption in blocks"});
    public final ConfigBase.ConfigInt radarYScanRange = this.i(20, 1, "radarYScanRange", new String[]{"Maximum vertical scan range of a radar in blocks"});
    public final ConfigBase.ConfigInt radarBaseRange = this.i(20, 1, "radarBaseRange", new String[]{"Base range of a radar receiver in blocks"});
    public final ConfigBase.ConfigInt dishRangeIncrease = this.i(10, 1, "dishRangeIncrease", new String[]{"Range increase per dish block in blocks"});
    public final ConfigBase.ConfigInt planeRadarRange = this.i(250, 1, 1000, "planeRadarRange", new String[]{"increases the range of the plane radar(in blocks)"});
    public final ConfigBase.ConfigBool gearRadarBearingSpeed = this.b(true, "gearRadarBearingSpeed", new String[]{"If true, radar bearings will rotate slower the more dishes are connected to them"});
    public final ConfigBase.ConfigInt radarFOV = this.i(90, 1, 360, "radarFOV", new String[]{"Field of view of a radar in degrees"});
    public final ConfigBase.ConfigGroup guidedFuzeConfig = this.group(3, "guidedFuzeConfig", new String[]{"Configs for the guided fuze"});
    public final ConfigBase.ConfigFloat guidedFuzeMaxSeekDegrees = this.f(180.0F, 1.0F, 180.0F, "guidedFuzeMaxSeekDegrees", new String[]{"The size of the cone the guided fuze can track targets from. Values are in degrees and are bi-directional (180 = full circle)"});
    public final ConfigBase.ConfigFloat guidedFuzeMaxDegreesPerTick = this.f(3.0F, 1.0F, "guidedFuzeMaxDegreesPerTick", new String[]{"The maximum number of degrees per tick the guided fuze can correct its course"});
    public final ConfigBase.ConfigBool guidedFuzeSeekBeforeApex = this.b(false, "guidedFuzeSeekBeforeApex", new String[]{"Determines if the guided fuze can seek its target before it has began to fall"});

    public String getName() {
        return "Radar Server";
    }
}
