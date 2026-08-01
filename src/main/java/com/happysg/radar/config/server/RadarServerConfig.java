package com.happysg.radar.config.server;

import net.createmod.catnip.config.ConfigBase;

public class RadarServerConfig extends ConfigBase {
    public final ConfigInt monitorMaxSize = i(9, 1, "monitorMaxSize", "Maximum size of monitor MultiBlock");
    public final ConfigFloat targetLoosenThreshold = f(3.0F, 0.0F, "targetLoosenThreshold", "how fast a target must be moving before looser firing conditions are applied (value is in m/s)");
    public final ConfigInt targetLoosenAmount = i(15, 0, 180, "targetLoosenAmount", "increases the tolerance used to determine if cannons are pointing at the target (degrees)");
    public final ConfigBool useNewTargetingComputer = b(true, "useNewTargetingComputer", "Use the new simulated targeting computer as the primary CBC auto aiming path");
    public final ConfigBool forceLegacyCannonLeadSolver = b(false, "forceLegacyCannonLeadSolver", "Force CBC auto aiming to use the legacy lead solver instead of the new targeting computer");
    public final ConfigFloat sprintJumpMinHorizontalSpeed = f(0.16F, 0.0F, "sprintJumpMinHorizontalSpeed", "Minimum horizontal target speed in blocks per tick for sprint-jump movement to count as erratic");
    public final ConfigFloat sprintJumpVerticalSpeedThreshold = f(0.12F, 0.0F, "sprintJumpVerticalSpeedThreshold", "Minimum upward target speed in blocks per tick for sprint-jump movement to count as erratic");
    public final ConfigInt erraticTargetStableTicks = i(1, 0, 20, "erraticTargetStableTicks", "Required stable aim ticks when target motion is erratic");
    public final ConfigInt targetTrackingLeadTicks = i(3, 0, 20, "targetTrackingLeadTicks", "Extra target prediction ticks used to compensate for radar and cannon tracking latency");
    public final ConfigInt leadFiringDelay = i(0, 0, 1000, "firingDelay", "The firing delay used in leading calculation. Higher values may prove useful in laggy environments");
    public final ConfigFloat controllerPhysbearingMaxSpeed = f(25.0F, 2.0F, 25.0F, "controllerPhysbearingMaxSpeed", "Increases the max Rotational speed of phys bearings controlled by Pitch/Yaw controllers");
    public final ConfigInt binoRaycastRange = i(512, 1, 1000, "binocularRange", "The range at which the binocular can acquire a target");

    public final ConfigGroup controllerCollisionViewConfig = group(3, "controllerCollisionViewConfig", "Controller GUI collision snapshot limits");
    public final ConfigInt controllerCollisionViewMaxHalfSpan = i(64, 5, 256, "maxHalfSpan", "Maximum half-span of a controller collision view in blocks");
    public final ConfigInt controllerCollisionViewMaxScannedBlocks = i(100000, 500, 1000000, "maxScannedBlocks", "Maximum block positions inspected for one controller collision snapshot");
    public final ConfigInt controllerCollisionViewMaxBoxes = i(16384, 128, 16384, "maxCollisionBoxes", "Maximum collision boxes returned in one controller collision snapshot");

    public final ConfigGroup chaffConfig = group(3, "chaffConfig", "Firework chaff lock-breaking behavior");
    public final ConfigBool chaffEnabled = b(true, "enabled", "Allow fireworks to temporarily break radar locks");
    public final ConfigFloat chaffRadius = f(8.0F, 0.0F, 64.0F, "radius", "Maximum firework detonation distance from a selected target in blocks");
    public final ConfigInt chaffVolleyWindowTicks = i(40, 1, 1200, "volleyWindowTicks", "Quiet time before nearby firework chaff starts a new probability volley");
    public final ConfigFloat chaffMinChance = f(0.20F, 0.0F, 1.0F, "minChance", "Lock-break chance for one small firework star");
    public final ConfigFloat chaffMaxSingleChance = f(0.60F, 0.0F, 1.0F, "maxSingleChance", "Lock-break chance for a maximum-strength firework");
    public final ConfigFloat chaffMaxVolleyChance = f(0.75F, 0.0F, 1.0F, "maxVolleyChance", "Absolute combined lock-break chance cap for one firework volley");
    public final ConfigInt chaffMinDurationTicks = i(10, 1, 1200, "minDurationTicks", "Lock-break duration for one small firework star");
    public final ConfigInt chaffMaxDurationTicks = i(70, 1, 1200, "maxDurationTicks", "Maximum lock-break duration for one firework (3.5 seconds by default)");
    public final ConfigInt chaffResistanceTicks = i(100, 0, 1200, "resistanceTicks", "Reduced-success window after a chaff effect expires (5 seconds by default)");
    public final ConfigFloat chaffResistanceChanceMultiplier = f(0.50F, 0.0F, 1.0F, "resistanceChanceMultiplier", "Chance multiplier while post-chaff resistance is active");
    public final ConfigFloat chaffPermanentBreakChancePerSuccess = f(0.02F, 0.0F, 1.0F, "permanentBreakChancePerSuccess", "Permanent lock-break chance added by each successful chaff roll");

    public final ConfigGroup sableSilhouetteConfig = group(3, "sableSilhouetteConfig", "Configs for Sable radar monitor silhouettes");
    public final ConfigInt sableSilhouetteMaxScannedBlocks = i(20000, 1, "sableSilhouetteMaxScannedBlocks", "Maximum loaded Sable sublevel blocks inspected while building one radar silhouette");
    public final ConfigInt sableSilhouetteMaxCollisionBoxes = i(12000, 1, "sableSilhouetteMaxCollisionBoxes", "Maximum collision boxes stored in one Sable radar silhouette");
    public final ConfigInt sableSilhouetteRebuildDebounceTicks = i(40, 1, "sableSilhouetteRebuildDebounceTicks", "Delay after Sable sublevel block changes before rebuilding a silhouette");
    public final ConfigInt sableSilhouetteFailureCooldownTicks = i(200, 1, "sableSilhouetteFailureCooldownTicks", "Cooldown before retrying a failed or too-large Sable silhouette scan");
    public final ConfigInt sableSilhouetteRefreshTicks = i(1200, 20, "sableSilhouetteRefreshTicks", "Conservative refresh interval for visible Sable silhouettes");
    public final ConfigBool sableSilhouetteUseFallbackBox = b(true, "sableSilhouetteUseFallbackBox", "Use a simple local bounding-box silhouette when detailed scanning is unavailable");
    public final ConfigBool sableSilhouetteDebugLogging = b(false, "sableSilhouetteDebugLogging", "Log Sable silhouette builds, failures, and revision changes");

    public final ConfigGroup radarOcclusionConfig = group(3, "radarOcclusionConfig", "Block occlusion behavior for native radars");
    public final ConfigBool radarOcclusionEnabled = b(true, "enabled", "Allow solid blocks to obstruct native radar signals");
    public final ConfigInt groundRadarMaxSolidBlocks = i(16, 0, "groundRadarMaxSolidBlocks", "Maximum number of solid blocks a ground radar signal can pass through");
    public final ConfigInt skyPlaneRadarMaxSolidBlocks = i(8, 0, "skyPlaneRadarMaxSolidBlocks", "Maximum number of solid blocks a sky or plane radar signal can pass through");

    public final ConfigGroup radarStats = group(3, "radarStatsConfig", "Configs for radar bearing and radar stats");
    public final ConfigInt maxRadarRange = i(2048, 1, "maxRadarRange", "Maximum range of a Radar Contraption in blocks");
    public final ConfigInt radarYScanRange = i(32, 1, "radarYScanRange", "Maximum vertical scan range of a radar in blocks");
    public final ConfigInt radarBaseRange = i(1, 1, "radarBaseRange", "Base range of a radar receiver in blocks");
    public final ConfigInt dishRangeIncrease = i(32, 1, "dishRangeIncrease", "Range increase per dish block in blocks");
    public final ConfigInt planeRadarRange = i(256, 1, 1000, "planeRadarRange", "increases the range of the plane radar(in blocks)");
    public final ConfigBool gearRadarBearingSpeed = b(true, "gearRadarBearingSpeed", "If true, radar bearings will rotate slower the more dishes are connected to them");
    public final ConfigInt radarFOV = i(90, 1, 360, "radarFOV", "Field of view of a radar in degrees");

    public final ConfigGroup skyRadarStats = group(3,"skyRadarStats", "Configs for sky radar");
    public final ConfigInt maxSkyRadarRange = i(4096, 1, "maxSkyRadarRange", "Maximum horizontal range of a sky radar in blocks");
    //public final ConfigInt skyRadarBaseRange = i(0, 1, "skyRadarBaseRange", "Base horizontal range of a sky radar in blocks");
    public final ConfigInt skyRadarFOV = i(90, 1, 360, "skyRadarFOV", "Field of view of a sky radar in degrees");
    public final ConfigInt skyRadarMinY = i(85,-64,256, "skyRadarMinY", "The minimum Y level for a sky radar to function");
    public final ConfigInt skyRadarDishRangeIncrease = i(64,1,"skyRadarDishRangeIncrease", "Range increase per dish block in blocks for the sky radar");


    public final ConfigGroup guidedFuzeConfig = group(3, "guidedFuzeConfig", "Configs for the guided fuze");
    public final ConfigFloat guidedFuzeMaxSeekDegrees = f(180.0F, 1.0F, 180.0F, "guidedFuzeMaxSeekDegrees", "The size of the cone the guided fuze can track targets from. Values are in degrees and are bi-directional (180 = full circle)");
    public final ConfigFloat guidedFuzeMaxDegreesPerTick = f(3.0F, 1.0F, "guidedFuzeMaxDegreesPerTick", "The maximum number of degrees per tick the guided fuze can correct its course");
    public final ConfigBool guidedFuzeSeekBeforeApex = b(false, "guidedFuzeSeekBeforeApex", "Determines if the guided fuze can seek its target before it has began to fall");

    public String getName() {
        return "Radar Server";
    }
}
