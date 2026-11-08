package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.api.targeting.RadarTargetingContext;
import com.happysg.radar.api.targeting.RadarTargetingRegistry;
import com.happysg.radar.api.targeting.RadarTargetingSolution;
import com.happysg.radar.api.weapon.*;
import com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.AccelerationTracker;
import com.happysg.radar.compat.cbc.CBCMuzzleUtil;
import com.happysg.radar.compat.cbc.CannonLead;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.debug.*;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import com.happysg.radar.block.controller.kinetic.ControllerMotionProfile;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.cbc.VelocityTracker;
import com.happysg.radar.compat.cbc_at.CBCATCannonCompat;
import com.happysg.radar.compat.cbc_at.CBCATRocketAimSolver;
import com.happysg.radar.compat.cbcmoreshells.CBCMSAimSolver;
import com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWGuidanceCompat;
import com.happysg.radar.compat.sable.SableLaunchMotion;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.happysg.radar.compat.sable.SableSilhouetteServerCache;
import com.happysg.radar.compat.sable.SubLevelAimResolver;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.radarproxfuze.AdvancedProximityFuze;
import com.happysg.radar.compat.sable.SableWeaponFiringAccess;
import com.happysg.radar.targeting.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

public class WeaponFiringControl {
    private static final Logger LOGGER = LogUtils.getLogger();
    public TargetingConfig targetingConfig;
    private Vec3 target;
    private float offset;
    private Vec3 lastOffsetAim;
    private int aimStableTicks;
    private static final int AIM_STABLE_REQUIRED = 2;
    private static final double AIM_STABLE_EPS = (double)0.5F;
    @Nullable
    public final CannonMountContext cannonMount;
    @Nullable
    public final RadarWeaponAdapter weapon;
    public AutoPitchControllerBlockEntity pitchController;
    public AutoYawControllerBlockEntity yawController;
    public FireControllerBlockEntity fireController;
    public WeaponNetworkRuntime.WeaponGroupView view;
    @Nullable
    private WeaponNetworkRuntime.WeaponControlView controlView;
    private boolean topologyValid = true;
    public final Level level;
    @Nullable
    private final BlockPos yawOnlyMountPos;
    private RadarTrack activetrack;
    private Entity targetEntity;
    private SubLevelAccess targetSublevel;
    private BlockPos binoTargetPos;
    private boolean binoMode;
    private UUID targetShipId;
    @Nullable
    private Vec3 lastAimPoint;
    @Nullable
    private Double fixedMountDesiredYaw;
    private List<List<Double>> cachedSableAngles;
    private Vec3 cachedSableAimTarget;
    private long cachedSableSolveTick;
    private Double cachedSablePitchDeg;
    private Double cachedSableYawDeg;
    @Nullable
    private Vec3 cachedSableWorldAimDirection;
    private static final int SABLE_SOLVE_INTERVAL = 3;
    private static final int SUBLEVEL_AIM_REACQUIRE_TICKS = 10;
    private static final int SUBLEVEL_AIM_CANDIDATE_LIMIT = 64;
    @Nullable
    private CachedSublevelAim cachedSublevelAim;
    @Nullable
    private ResolvedSublevelAim currentSublevelAim;
    private final Map<Integer, VisCache> visCache;
    double maxSimDistanceBlocks;
    private static final double NEW_SOLVER_MIN_CONFIDENCE = 0.05;
    private static final int TARGETING_DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final int MAX_NEW_SOLVER_FLIGHT_TICKS = 2400;
    private static final double CLOSE_TARGET_SOLVE_EVERY_TICK_BLOCKS = 64.0;
    private static final double EXTREME_TARGET_SPEED_MULTIPLIER = 2.0;
    private static final int ASYNC_TARGETING_FIRE_FRESH_TICKS = 4;
    private static final int ASYNC_TARGETING_TASK_TIMEOUT_TICKS = 100;
    private static final int ASYNC_TARGETING_REFRESH_TICKS = 2;
    private static final long TARGET_MOTION_HISTORY_MAX_GAP_TICKS = 2L;
    private static final long ERRATIC_LEAD_RELEASE_HOLD_TICKS = 3L;
    private static final double ASYNC_STEERING_MAX_DIVERGENCE_COS =
            Math.cos(Math.toRadians(10.0));
    @Nullable
    private FiringAlignmentTolerance lastFiringAlignmentTolerance;
    private static final int TARGETING_EXECUTOR_THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final int TARGETING_EXECUTOR_QUEUE_CAPACITY = 16;
    private static final ThreadPoolExecutor TARGETING_EXECUTOR = createTargetingExecutor();
    private final TargetingComputer targetingComputer;
    private final TargetingComputer asyncTargetingComputer;
    private final TargetingComputer cbcAtRocketTargetingComputer;
    private final TargetingComputer asyncCBCAtRocketTargetingComputer;
    private final TargetingComputer cbcmsTargetingComputer;
    private final ProjectileSimulator validationProjectileSimulator;
    private final ObstructionChecker mainThreadObstructionChecker;
    private long lastTargetingDebugLogTick;
    private long lastPhysicalYawSampleTick = Long.MIN_VALUE;
    private double lastPhysicalYawSample = Double.NaN;
    private double measuredPhysicalYawRate;
    private boolean inLaunchFeasibilityProbe;
    @Nullable
    private UUID trackingLeadTargetId;
    private long erraticLeadHoldUntilTick = Long.MIN_VALUE;
    private int pendingBallisticRefinementFloorTicks = -1;
    private boolean cachedBallisticReachableForFire = true;
    private boolean lastSyncBallisticReachableForFire = true;
    private boolean cachedBallisticSolutionAccepted = false;
    private boolean rigidTargetExternalMotionOverride;
    private long lastRigidTargetDiagnosticTick = Long.MIN_VALUE;
    @Nullable
    private String lastRigidTargetDiagnosticState;
    @Nullable
    private TargetingResult cachedTargetingResult;
    @Nullable
    private UUID cachedTargetingTargetId;
    @Nullable
    private Vec3 cachedTargetingSolvePos;
    private long cachedTargetingRequestTick;
    @Nullable
    private String cachedTargetingShotFingerprint;
    @Nullable
    private Vec3 cachedTargetingOriginPos;
    @Nullable
    private AsyncTargetingRequest cachedTargetingRequest;
    private long cachedAsyncSableMotionTick = Long.MIN_VALUE;
    @Nullable
    private UUID cachedAsyncSableMotionShipId;
    @Nullable
    private RigidBodyLaunchMath.Motion cachedAsyncSableMotion;
    private boolean lastTargetingSteeringProvisional;
    private boolean lastTargetingFireFresh;
    private String lastTargetingFreshnessReason;
    private long lastCBCMSSolveAttemptTick;
    @Nullable
    private UUID lastCBCMSSolveTargetId;
    @Nullable
    private String lastCBCMSSolveFingerprint;
    @Nullable
    private TargetingResult lastCBCMSSolveResult;
    @Nullable
    private CompletableFuture<AsyncTargetingResult> pendingTargetingFuture;
    @Nullable
    private AsyncTargetingRequest pendingTargetingRequest;
    private long lastJammingSampleToken;
    private long asyncSolveSubmitted;
    private long asyncSolveCompleted;
    private long asyncSolveSuperseded;
    private long asyncSolveTimedOut;
    private long asyncSolveRejected;
    // For Logging
    private long lastAsyncDiagnosticTick = Long.MIN_VALUE;
    private String lastAsyncRawResult = "not_completed";
    private String lastAsyncValidationReason = "not_checked";
    private int lastAsyncMaxFlightTicks = -1;
    // For Logging
    @Nullable
    private UUID lastSableVelocityTargetId;
    @Nullable
    private Vec3 lastSableVelocityTargetPos;
    private long lastSableVelocityTargetTick;
    private Vec3 lastSableVelocityPerTick;
    private final Map<String, LosCache> losSelectionCache;
    private final Map<UUID, TargetMotionState> targetMotionStates;
    private final LosCache losPrefireCache;
    public List<SafeZone> safeZones;
    private long lastTargetTick;
    private long cachedMountAimFrameTick = Long.MIN_VALUE;
    @Nullable
    private MountAimFrame cachedMountAimFrame;
    @Nullable
    private UUID lastResolvedSourceSublevelId;
    @Nullable
    private AimCommandEvaluation lastAimCommandEvaluation;
    @Nullable
    private String lastAutoAimLimitTargetId;
    private long lastAutoAimLimitTick = Long.MIN_VALUE;
    private boolean lastAutoAimLimitReachable;

    private static double clamp01(double v) {
        return v < (double)0.0F ? (double)0.0F : (v > (double)1.0F ? (double)1.0F : v);
    }

    private static double invSpan(double min, double max) {
        double span = max - min;
        return Math.abs(span) < 1.0E-9 ? (double)0.0F : (double)1.0F / span;
    }

    private static Vec3 fracToWorld(AABB bb, double fx, double fy, double fz) {
        return new Vec3(bb.minX + (bb.maxX - bb.minX) * fx, bb.minY + (bb.maxY - bb.minY) * fy, bb.minZ + (bb.maxZ - bb.minZ) * fz);
    }

    private static void worldToFrac(AABB bb, Vec3 p, VisCache c) {
        double invX = invSpan(bb.minX, bb.maxX);
        double invY = invSpan(bb.minY, bb.maxY);
        double invZ = invSpan(bb.minZ, bb.maxZ);
        c.fx = clamp01((p.x - bb.minX) * invX);
        c.fy = clamp01((p.y - bb.minY) * invY);
        c.fz = clamp01((p.z - bb.minZ) * invZ);
        c.hasFrac = true;
    }

    @Nullable
    private static RadarWeaponAdapter resolveRegisteredWeapon(@Nullable CannonMountContext mount) {
        if (mount == null || !(mount.getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }

        return RadarWeaponRegistry.find(serverLevel, mount.getBlockPos());
    }

    public WeaponFiringControl(AutoPitchControllerBlockEntity controller, CannonMountContext cannonMount, AutoYawControllerBlockEntity yawController) {
        this(controller, resolveRegisteredWeapon(cannonMount), cannonMount, yawController);
    }

    public WeaponFiringControl(AutoPitchControllerBlockEntity controller, RadarWeaponAdapter weapon, AutoYawControllerBlockEntity yawController) {
        this(controller, weapon, null, yawController);
    }

    private WeaponFiringControl(AutoPitchControllerBlockEntity controller, @Nullable RadarWeaponAdapter weapon, @Nullable CannonMountContext cannonMount, AutoYawControllerBlockEntity yawController) {
        this(controller, weapon, cannonMount, yawController, controller.getLevel(), null);
    }

    public WeaponFiringControl(AutoYawControllerBlockEntity yaw, BlockPos mountPos) {
        this(null, yaw.getLevel() instanceof ServerLevel serverLevel ? RadarWeaponRegistry.find(serverLevel, mountPos) : null, CannonMountContext.resolveEndpoint(yaw.getLevel(), mountPos), yaw, yaw.getLevel(), mountPos);
    }

    private WeaponFiringControl(@Nullable AutoPitchControllerBlockEntity controller, @Nullable RadarWeaponAdapter weapon, @Nullable CannonMountContext cannonMount, @Nullable AutoYawControllerBlockEntity yawController, Level level, @Nullable BlockPos yawOnlyMountPos) {
        this.targetingConfig = TargetingConfig.DEFAULT;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.targetShipId = null;
        this.lastAimPoint = null;
        this.fixedMountDesiredYaw = null;
        this.cachedSableAngles = null;
        this.cachedSableAimTarget = null;
        this.cachedSableSolveTick = -1L;
        this.cachedSablePitchDeg = null;
        this.cachedSableYawDeg = null;
        this.cachedSublevelAim = null;
        this.currentSublevelAim = null;
        this.visCache = new HashMap<>();
        this.maxSimDistanceBlocks = 8192.0;
        this.mainThreadObstructionChecker = new ObstructionChecker();
        this.targetingComputer = TargetingComputer.createDefault();
        this.asyncTargetingComputer = new TargetingComputer(null, new ProjectileSimulator(), null, ObstructionChecker.NONE);
        this.cbcAtRocketTargetingComputer = CBCATRocketAimSolver.createComputer(this.mainThreadObstructionChecker);
        this.asyncCBCAtRocketTargetingComputer = CBCATRocketAimSolver.createComputer(ObstructionChecker.NONE);
        this.cbcmsTargetingComputer = CBCMSAimSolver.createComputer(this.mainThreadObstructionChecker);
        this.validationProjectileSimulator = new ProjectileSimulator();

        this.lastTargetingDebugLogTick = Long.MIN_VALUE;
        this.cachedTargetingResult = null;
        this.cachedTargetingTargetId = null;
        this.cachedTargetingSolvePos = null;
        this.cachedTargetingRequestTick = Long.MIN_VALUE;
        this.cachedTargetingShotFingerprint = null;
        this.cachedTargetingOriginPos = null;
        this.lastTargetingSteeringProvisional = false;
        this.lastTargetingFireFresh = false;
        this.lastTargetingFreshnessReason = "no_solution";
        this.lastAimCommandEvaluation = null;

        this.lastCBCMSSolveAttemptTick = Long.MIN_VALUE;
        this.lastCBCMSSolveTargetId = null;
        this.lastCBCMSSolveFingerprint = null;
        this.lastCBCMSSolveResult = null;

        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;
        this.lastJammingSampleToken = Long.MIN_VALUE;

        this.lastSableVelocityTargetId = null;
        this.lastSableVelocityTargetPos = null;
        this.lastSableVelocityTargetTick = Long.MIN_VALUE;
        this.lastSableVelocityPerTick = Vec3.ZERO;

        this.losSelectionCache = new HashMap<>();
        this.targetMotionStates = new HashMap<>();
        this.losPrefireCache = new LosCache();

        this.safeZones = new ArrayList<>();
        this.lastTargetTick = -1L;
        this.cachedMountAimFrameTick = Long.MIN_VALUE;
        this.cachedMountAimFrame = null;
        this.lastResolvedSourceSublevelId = null;

        this.cannonMount = cannonMount;
        this.weapon = weapon;
        this.pitchController = controller;
        this.yawController = yawController;
        this.level = level;
        this.yawOnlyMountPos = yawOnlyMountPos == null ? null : yawOnlyMountPos.immutable();

        BlockPos mountPos = weapon != null ? weapon.getMountPos() : cannonMount != null ? cannonMount.getBlockPos() : null;

        LOGGER.debug("FiringControlBlockEntity.<init>() -> controller={} mountPos={} weaponApi={} cbc={}", controller, mountPos, weapon != null, cannonMount != null);
    }

    private RayResult rayClear(Vec3 start, Vec3 end) {
        RayResult result = WeaponFiringControl.RayResult.CLEAR;
        if (!this.safeZones.isEmpty()) {
            for(SafeZone zone : this.safeZones) {
                if (zone != null && zone.intersects(this.level, start, end)) {
                    return WeaponFiringControl.RayResult.BLOCKED_SAFEZONE;
                }
            }
        }

        if (result == WeaponFiringControl.RayResult.CLEAR) {
            ClipContext ctx = new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, CollisionContext.empty());
            HitResult hit = this.level.clip(ctx);
            Vec3 worldHit = hit.getLocation();

            if (Mods.SABLE.isLoaded() && hit instanceof BlockHitResult blockHit) {
                SubLevelAccess hitSublevel = SableCompanion.INSTANCE.getContaining(this.level, blockHit.getBlockPos());

                if (hitSublevel != null) {
                    worldHit = SubLevelAimResolver.toWorld(hitSublevel, worldHit);
                }
            }

            if (hit.getType() != Type.MISS) {
                double hitDist = worldHit.distanceTo(start);
                double targetDist = end.distanceTo(start);
                if (hitDist < targetDist) {
                    result = WeaponFiringControl.RayResult.BLOCKED_BLOCK;
                }
            }
        }

        if (RadarConfig.DEBUG_BEAMS && this.level instanceof ServerLevel server) {
            this.debugRay(server, start, end, result);
        }

        return result;
    }

    @Nullable
    private BlockPos getWeaponMountPos() {
        if (yawOnlyMountPos != null) {
            return yawOnlyMountPos;
        }

        if (this.weapon != null) {
            return this.weapon.getMountPos();
        }

        return this.cannonMount != null ? this.cannonMount.getBlockPos() : null;
    }

    private boolean isExternallyTriggeredYawMount(ServerLevel serverLevel) {
        if (this.yawOnlyMountPos == null || this.yawController == null || !this.isSableMount()) {
            return false;
        }

        BlockPos mountPos = getWeaponMountPos();
        if (mountPos == null) {
            return false;
        }

        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(serverLevel);

        for (Direction direction : Direction.values()) {
            BlockPos firePos = mountPos.relative(direction);

            if (!serverLevel.hasChunkAt(firePos) || !(serverLevel.getBlockEntity(firePos) instanceof FireControllerBlockEntity)) {
                continue;
            }

            BlockPos firingAuthority = runtime.getMountForController(firePos);
            if (firingAuthority != null && !mountPos.equals(firingAuthority)) {
                return true;
            }
        }

        return false;
    }

    public Vec3 getCannonRayStart() {
        if (this.weapon != null) {
            RadarMountAdapter mount = this.weapon.getMount();

            if (mount != null && mount.isValid() && mount.isAssembled()) {
                Vec3 origin = mount.getAimOrigin();

                if (origin != null) {
                    return origin;
                }
            }
        }

        return getCannonRayStart(this.cannonMount);
    }

    public void appendDiagnosticInfo(DiagnosticSnapshotBuilder builder) {
        builder.add("Weapon control", "mount", getWeaponMountPos())
                .add("Weapon control", "mode",
                        binoMode ? "binocular" : activetrack != null ? "radar track" : "idle")
                .add("Weapon control", "target present", target != null)
                .add("Weapon control", "aim stable ticks", aimStableTicks)
                .add("Weapon control", "last async max flight ticks", lastAsyncMaxFlightTicks)
                .add("Weapon control", "safe zones", safeZones.size())
                .add("Weapon control", "safe zone firing veto", this.passesSafeZone())
                .add("Weapon control", "fire controller",
                        fireController == null ? "missing" : fireController.getBlockPos())
                .add("Weapon control", "fire powered",
                        fireController != null && fireController.isPowered())
                .add("Weapon control", "async submitted", asyncSolveSubmitted)
                .add("Weapon control", "async completed", asyncSolveCompleted)
                .add("Weapon control", "async timed out", asyncSolveTimedOut,
                        asyncSolveTimedOut > 0 ? DiagnosticSeverity.WARN : DiagnosticSeverity.INFO)
                .add("Weapon control", "async rejected", asyncSolveRejected,
                        asyncSolveRejected > 0 ? DiagnosticSeverity.WARN : DiagnosticSeverity.INFO)
                .add("Weapon control", "solution freshness", lastTargetingFreshnessReason)
                .add("Weapon control", "last async diagnostic tick", lastAsyncDiagnosticTick)
                .add("Weapon control", "last async raw result", lastAsyncRawResult)
                .add("Weapon control", "last async validation", lastAsyncValidationReason);
        if (lastFiringAlignmentTolerance != null) {
            builder.add("Weapon control", "alignment range blocks",
                            lastFiringAlignmentTolerance.distanceBlocks())
                    .add("Weapon control", "alignment error budget blocks",
                            lastFiringAlignmentTolerance.lateralErrorBudgetBlocks())
                    .add("Weapon control", "alignment axes",
                            lastFiringAlignmentTolerance.axisCount())
                    .add("Weapon control", "alignment tolerance degrees",
                            lastFiringAlignmentTolerance.maximumDegrees());
        }
        if (activetrack != null) {
            builder.add("Weapon control", "track category",
                            activetrack.trackCategory())
                    .add("Weapon control", "track id",
                            shortIdentifier(activetrack.id()));
        }
        if (cachedTargetingResult != null) {
            builder.add("Targeting", "valid", cachedTargetingResult.valid())
                    .add("Targeting", "has shot",
                            cachedTargetingResult.hasShot())
                    .add("Targeting", "reason",
                            cachedTargetingResult.reason(),
                            cachedTargetingResult.hasShot()
                                    ? DiagnosticSeverity.INFO
                                    : DiagnosticSeverity.WARN)
                    .add("Targeting", "confidence",
                            cachedTargetingResult.confidence())
                    .add("Targeting", "predicted ticks",
                            cachedTargetingResult.predictedFlightTicks())
                    .add("Targeting", "miss distance",
                            cachedTargetingResult.missDistance());
        } else {
            builder.warn("Targeting", "solution", "none cached");
        }
    }

    private static String shortIdentifier(String value) {
        if (value == null || value.isBlank()) return "none";
        return "id#" + Integer.toUnsignedString(value.hashCode(), 16);
    }

    @Nullable
    private Vec3 getCannonRayStart(@Nullable CannonMountContext mount) {
        if (mount == null) {
            return null;
        } else {
            PitchOrientedContraptionEntity poce = mount.getContraption();
            AutoYawControllerBlockEntity mountYaw = Objects.equals(getWeaponMountPos(), mount.getBlockPos()) ? this.yawController : null;

            boolean structuralSwivel = this.level instanceof ServerLevel
                    && (usesStructuralPitchForMount(mount.getBlockPos()) || (mountYaw != null
                    && mountYaw.hasStructuralKineticSelectionForTargeting()));

            float partialTicks = structuralSwivel || (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(this.level, mount.getBlockPos())) ? 0.0F : 1.0F;

            if (Mods.SABLE.isLoaded()
                    && SableUtils.isBlockInShipyard(
                    this.level, mount.getBlockPos())) {
                if (poce != null) {
                    Vec3 shipyardPos = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), partialTicks);
                    return SableUtils.getWorldVec(this.level, shipyardPos);
                } else {
                    return SableUtils.getWorldVec(this.level, mount.getBlockPos().getCenter());
                }
            } else {
                return poce == null ? mount.getBlockPos().getCenter() : poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), partialTicks);
            }
        }
    }

    private AABB inflatedAabb(Entity e) {
        AABB bb = e.getBoundingBox();
        return bb.inflate((double)0.0F);
    }

    private void addFaceCandidates(List<Vec3> out, double planeVal, double uMin, double uMax, double vMin, double vMax, char axis, boolean maxFace) {
        double uMid = (uMin + uMax) * (double)0.5F;
        double vMid = (vMin + vMax) * (double)0.5F;
        out.add(this.facePoint(axis, planeVal, uMid, vMid, maxFace));
        out.add(this.facePoint(axis, planeVal, uMin, vMin, maxFace));
        out.add(this.facePoint(axis, planeVal, uMin, vMax, maxFace));
        out.add(this.facePoint(axis, planeVal, uMax, vMin, maxFace));
        out.add(this.facePoint(axis, planeVal, uMax, vMax, maxFace));
        out.add(this.facePoint(axis, planeVal, uMin, vMid, maxFace));
        out.add(this.facePoint(axis, planeVal, uMax, vMid, maxFace));
        out.add(this.facePoint(axis, planeVal, uMid, vMin, maxFace));
        out.add(this.facePoint(axis, planeVal, uMid, vMax, maxFace));
    }

    private Vec3 facePoint(char axis, double planeVal, double u, double v, boolean maxFace) {
        double eps = maxFace ? -0.01 : 0.01;
        return switch (axis) {
            case 'x' -> new Vec3(planeVal + eps, u, v);
            case 'y' -> new Vec3(u, planeVal + eps, v);
            default -> new Vec3(u, v, planeVal + eps);
        };
    }

    @Nullable
    private Vec3 getCachedVisiblePoint(Entity f) {
        int id = f.getId();
        long now = this.level.getGameTime();
        VisCache c = this.visCache.get(id);
        Vec3 start = this.getCannonRayStart();
        if (start == null) {
            return null;
        } else {
            double maxRange = this.pitchController != null ? this.pitchController.getMaxEngagementRangeBlocks() : 0.0;
            if (c != null && now - c.lastTick < 3L) {
                Vec3 cached = null;
                if (c.hasFrac) {
                    cached = fracToWorld(this.inflatedAabb(f), c.fx, c.fy, c.fz);
                } else {
                    cached = c.lastWorldPoint;
                }

                if (isVisiblePointWithinRange(cached, start, maxRange) && this.rayClear(start, cached).isClear()) {
                    return cached;
                }

                c.lastTick = 0L;
            }

            if (c == null) {
                c = new VisCache();
            }

            Vec3 vis = this.findVisiblePointOnEntityRotating(f, start, c, 10, maxRange);
            c.lastTick = now;
            this.visCache.put(id, c);
            if (vis == null) {
                c.hasFrac = false;
                c.lastWorldPoint = null;
                return null;
            } else {
                return vis;
            }
        }
    }

    @Nullable
    private Vec3 findVisiblePointOnEntityRotating(Entity e, Vec3 start, VisCache cache, int budget, double maxRange) {
        AABB bb = this.inflatedAabb(e);
        long now = this.level.getGameTime();
        List<Vec3> candidates = new ArrayList<>(24);
        Vec3 center = bb.getCenter();
        Vec3 toCannon = start.subtract(center);
        double ax = Math.abs(toCannon.x);
        double ay = Math.abs(toCannon.y);
        double az = Math.abs(toCannon.z);
        double xMin = bb.minX;
        double xMax = bb.maxX;
        double yMin = bb.minY;
        double yMax = bb.maxY;
        double zMin = bb.minZ;
        double zMax = bb.maxZ;
        double yMid = (yMin + yMax) * (double)0.5F;
        double yUpper = yMin + (yMax - yMin) * 0.75;
        double yChest = yMin + (yMax - yMin) * 0.45;
        Vec3 upper = new Vec3(center.x, yUpper, center.z);
        Vec3 chest = new Vec3(center.x, yChest, center.z);
        Vec3 mid = new Vec3(center.x, yMid, center.z);
        candidates.add(center);
        candidates.add(upper);
        candidates.add(mid);
        candidates.add(chest);
        boolean useX = ax >= ay && ax >= az;
        boolean useY = ay > ax && ay >= az;
        if (useX) {
            boolean maxFace = toCannon.x >= (double)0.0F;
            double x = maxFace ? xMax : xMin;
            this.addFaceCandidates(candidates, x, yMin, yMax, zMin, zMax, 'x', maxFace);
        } else if (useY) {
            boolean maxFace = toCannon.y >= (double)0.0F;
            double y = maxFace ? yMax : yMin;
            this.addFaceCandidates(candidates, y, xMin, xMax, zMin, zMax, 'y', maxFace);
        } else {
            boolean maxFace = toCannon.z >= (double)0.0F;
            double z = maxFace ? zMax : zMin;
            this.addFaceCandidates(candidates, z, xMin, xMax, yMin, yMax, 'z', maxFace);
        }

        if (ax >= az) {
            boolean maxFace = toCannon.x >= (double)0.0F;
            double x = maxFace ? xMax : xMin;
            this.addFaceCandidates(candidates, x, yMin, yMax, zMin, zMax, 'x', maxFace);
        } else {
            boolean maxFace = toCannon.z >= (double)0.0F;
            double z = maxFace ? zMax : zMin;
            this.addFaceCandidates(candidates, z, xMin, xMax, yMin, yMax, 'z', maxFace);
        }

        candidates.add(center);
        int n = candidates.size();
        if (n == 0) {
            return null;
        } else {
            int tries = 0;
            if (cache.hasFrac) {
                Vec3 cached = fracToWorld(bb, cache.fx, cache.fy, cache.fz);
                if (isVisiblePointWithinRange(cached, start, maxRange) && this.rayClear(start, cached).isClear()) {
                    cache.blockedStreak = 0;
                    if (now - cache.lastReacquireTick >= 10L) {
                        cache.lastReacquireTick = now;
                        if (cached.distanceToSqr(center) > 1.0E-4 && isVisiblePointWithinRange(center, start, maxRange) && this.rayClear(start, center).isClear()) {
                            worldToFrac(bb, center, cache);
                            cache.lastWorldPoint = center;
                            cache.probeCursor = 0;
                            return center;
                        }

                        if (cached.distanceToSqr(upper) > 1.0E-4 && isVisiblePointWithinRange(upper, start, maxRange) && this.rayClear(start, upper).isClear()) {
                            worldToFrac(bb, upper, cache);
                            cache.lastWorldPoint = upper;
                            cache.probeCursor = 0;
                            return upper;
                        }
                    }

                    cache.lastWorldPoint = cached;
                    return cached;
                }

                ++tries;
            }

            int remaining = budget - tries;
            if (remaining <= 0) {
                ++cache.blockedStreak;
                return null;
            } else {
                int maxNewTries = Math.min(3, remaining);
                int idx = cache.blockedStreak == 0 ? 0 : Math.floorMod(cache.probeCursor, n);

                for(int k = 0; k < maxNewTries; ++k) {
                    Vec3 end = candidates.get((idx + k) % n);
                    if (isVisiblePointWithinRange(end, start, maxRange) && this.rayClear(start, end).isClear()) {
                        worldToFrac(bb, end, cache);
                        cache.lastWorldPoint = end;
                        cache.probeCursor = (idx + k + 1) % n;
                        cache.blockedStreak = 0;
                        return end;
                    }
                }

                cache.probeCursor = (idx + maxNewTries) % n;
                ++cache.blockedStreak;
                return null;
            }
        }
    }

    public boolean checkLineOfSight(Vec3 target) {
        if (!this.binoMode && this.activetrack == null && target == null) {
            return false;
        } else {
            if (!this.binoMode && this.activetrack == null) {
                boolean var2 = true;
            }

            if (!this.targetingConfig.lineOfSight()) {
                return true;
            } else {
                float height;
                if (!this.binoMode) {
                    height = this.targetEntity != null ? this.targetEntity.getBbHeight() : (this.activetrack != null ? this.activetrack.getEnityHeight() : 1.0F);
                } else {
                    height = 1.0F;
                }

                int blocksHigh = (int)Math.ceil((double)height);
                Vec3 start = this.getCannonRayStart();
                if (this.isOutOfKnownRange(target)) {
                    return false;
                } else {
                    //LOGGER.debug("LOS DBG: trackCat={} entityType={} height={} blocksHigh={} target={}", new Object[]{this.activetrack != null ? this.activetrack.trackCategory() : "null", this.activetrack != null ? this.activetrack.entityType() : "null", height, blocksHigh, target});

                    for(int h = blocksHigh - 1; h >= 0; --h) {
                        Vec3 end = target.add((double)0.0F, (double)h + (double)0.5F, (double)0.0F);
                        if (this.rayClear(start, end).isClear()) {
                            this.offset = (float)h + 0.5F;
                            return true;
                        }
                    }

                    return false;
                }
            }
        }
    }

    private boolean isPointInShootableRange(@Nullable Vec3 point) {
        if (point == null) {
            return false;
        } else {
            double max = this.pitchController != null ? this.pitchController.getMaxEngagementRangeBlocks() : (double)0.0F;
            if (max <= (double)0.0F) {
                return true;
            } else {
                Vec3 start = this.getCannonRayStart();
                double dx = point.x - start.x;
                double dz = point.z - start.z;
                double horiz2 = dx * dx + dz * dz;
                return horiz2 <= max * max;
            }
        }
    }

    private static boolean isVisiblePointWithinRange(@Nullable Vec3 point, Vec3 start, double maxRange) {
        if (point == null) {
            return false;
        }

        if (maxRange <= 0.0) {
            return true;
        }

        double dx = point.x - start.x;
        double dz = point.z - start.z;
        double maxRangeSquared = maxRange * maxRange;

        if (dx * dx + dz * dz > maxRangeSquared) {
            return false;
        }

        return point.distanceToSqr(start) <= maxRangeSquared;
    }

    public boolean hasLineOfSightTo(@Nullable RadarTrack track, boolean requireLos) {
        if (!this.isPrimaryWeaponStateOk()) {
            return false;
        } else if (!requireLos) {
            return true;
        } else if (track == null) {
            return false;
        } else {
            if (isSableTrack(track) && this.level instanceof ServerLevel sl) {
                Vec3 aim = this.resolveEngagementAimPoint(sl, track, true);
                return aim != null && this.isPointInShootableRange(aim);
            }
            Vec3 p = track.position();
            if (p == null) {
                return false;
            } else if (!this.isPointInShootableRange(p)) {
                return false;
            } else {
                long now = this.level.getGameTime();
                String key = track.getId();
                LosCache c = this.losSelectionCache.get(key);
                if (c != null && now - c.tick <= 10L) {
                    return c.ok;
                } else {
                    boolean ok = this.computeLosToTrack(track);
                    if (c == null) {
                        c = new LosCache();
                    }

                    c.ok = ok;
                    c.tick = now;
                    this.losSelectionCache.put(key, c);
                    return ok;
                }
            }
        }
    }

    private boolean computeLosToTrack(@Nullable RadarTrack track) {
        if (track == null) {
            return false;
        } else {
            Vec3 p = track.position();
            if (p == null) {
                return false;
            } else {
                Vec3 start = this.getCannonRayStart();
                if (this.level instanceof ServerLevel sl) {
                    boolean shouldBeEntity = !track.isSynthetic()
                            && (track.trackCategory() == TrackCategory.PLAYER
                            || track.trackCategory() == TrackCategory.HOSTILE
                            || track.trackCategory() == TrackCategory.ANIMAL
                            || track.trackCategory() == TrackCategory.PROJECTILE
                            || track.trackCategory() == TrackCategory.MISSILE);
                    Entity e = null;

                    try {
                        UUID uuid = UUID.fromString(track.getId());
                        e = sl.getEntity(uuid);
                    } catch (Throwable var8) {
                    }

                    if (e != null && e.isAlive()) {
                        return this.getCachedVisiblePoint(e) != null;
                    }

                    if (shouldBeEntity) {
                        return false;
                    }
                }

                for(int i = 0; i < 4; ++i) {
                    Vec3 end = p.add((double)0.0F, (double)0.25F + (double)i * (double)0.5F, (double)0.0F);
                    if (this.isPointInShootableRange(end) && this.rayClear(start, end).isClear()) {
                        return true;
                    }
                }

                return false;
            }
        }
    }

    private void debugRay(ServerLevel server, Vec3 start, Vec3 end, RayResult result) {
        float r;
        float g;
        float b;
        switch (result.ordinal()) {
            case 0:
                r = 0.0F;
                g = 1.0F;
                b = 0.0F;
                break;
            case 1:
                r = 1.0F;
                g = 0.0F;
                b = 0.0F;
                break;
            case 2:
                r = 1.0F;
                g = 1.0F;
                b = 0.0F;
                break;
            default:
                r = 1.0F;
                g = 1.0F;
                b = 1.0F;
        }

        double dist = start.distanceTo(end);
        Vec3 dir = end.subtract(start).normalize();

        for(double d = (double)0.0F; d < dist; d += (double)0.25F) {
            Vec3 p = start.add(dir.scale(d));
            server.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.0F), p.x, p.y, p.z, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
        }
    }

    public void clearBinoTarget() {
        this.visCache.clear();
        this.clearSublevelAimCache();
        this.binoMode = false;
        this.binoTargetPos = null;
        this.targetSublevel = null;
        this.targetShipId = null;
        this.target = null;
        this.activetrack = null;
        this.lastJammingSampleToken = Long.MIN_VALUE;
        this.lastAimPoint = null;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.clearTargetingResultCache();
        this.stopFireCannon();
        this.endStructuralRadarTracking();
    }

    public void setSafeZones(List<SafeZone> safeZones) {
        List<SafeZone> updated = safeZones == null ? List.of() : List.copyOf(safeZones);

        if (this.safeZones.equals(updated)) {
            return;
        }

        this.safeZones = new ArrayList<>(updated);

        this.clearTargetingResultCache();
        this.stopFireCannon();
    }

    public Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public SubLevelAccess getShipByUUID(ServerLevel level, String uuid) {
        if (!Mods.SABLE.isLoaded()) {
            return null;
        }
        return SableWeaponFiringAccess.findSubLevel(level, UUID.fromString(uuid));
    }

    public void refreshControllers() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.pitchController == null) {
            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(serverLevel);
            BlockPos yawPos = this.yawController == null ? null : this.yawController.getBlockPos();

            this.controlView = null;
            this.view = yawPos == null ? null : runtime.getWeaponGroupViewFromEndpoint(yawPos);

            this.topologyValid = yawPos != null
                    && yawOnlyMountPos != null
                    && this.view != null
                    && this.view.pitchPos() == null
                    && yawPos.equals(this.view.yawPos())
                    && yawOnlyMountPos.equals(this.view.mountPos())
                    && runtime.hasValidWeaponGroup(yawOnlyMountPos);

            FireControllerBlockEntity previousFire = this.fireController;

            this.fireController = this.topologyValid
                    && this.view.firingPos() != null
                    && serverLevel.getBlockEntity(this.view.firingPos()) instanceof FireControllerBlockEntity fire ? fire : null;

            if (previousFire != null && previousFire != this.fireController) {
                previousFire.setPowered(false);
            }

            if (!this.topologyValid) {
                stopFireCannon();
            }

            return;
        }

        Set<FireControllerBlockEntity> previousFires = currentFireControllers();
        BlockPos pitchPos = this.pitchController == null ? null : this.pitchController.getBlockPos();

        if (pitchPos == null) {
            clearControllerTopology(previousFires);
            return;
        }

        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(serverLevel);

        this.view = runtime.getWeaponGroupViewFromEndpoint(pitchPos);
        this.controlView = runtime.getWeaponControlViewFromPitch(pitchPos);

        BlockPos mountPos = getWeaponMountPos();

        if (this.view == null || this.controlView == null || mountPos == null) {
            clearControllerTopology(previousFires);
            return;
        }

        if (this.level.getBlockEntity(this.controlView.pitchPos())
                instanceof AutoPitchControllerBlockEntity autopitch) {
            this.pitchController = autopitch;
        } else {
            clearControllerTopology(previousFires);
            return;
        }

        WeaponNetworkRuntime.MountChannelView channel = this.controlView.channelForMount(mountPos);
        if (channel == null) {
            clearControllerTopology(previousFires);
            return;
        }

        this.yawController = channel.yawPos() != null && this.level.getBlockEntity(channel.yawPos()) instanceof AutoYawControllerBlockEntity yaw ? yaw : null;
        this.fireController = channel.firingPos() != null && this.level.getBlockEntity(channel.firingPos()) instanceof FireControllerBlockEntity fire ? fire : null;
        this.topologyValid = this.controlView.validTopology();

        Set<FireControllerBlockEntity> activeFires = currentFireControllers();

        for (FireControllerBlockEntity previous : previousFires) {
            if (!activeFires.contains(previous)) {
                previous.setPowered(false);
            }
        }

        if (!this.topologyValid) {
            stopFireCannon();
        }
    }

    private void clearControllerTopology(Set<FireControllerBlockEntity> previousFires) {
        for (FireControllerBlockEntity fire : previousFires) {
            fire.setPowered(false);
        }
        stopWeaponOutput();

        this.view = null;
        this.controlView = null;
        this.topologyValid = false;
        this.yawController = null;
        this.fireController = null;
    }

    private Set<FireControllerBlockEntity> currentFireControllers() {
        Set<FireControllerBlockEntity> controllers = new HashSet<>();

        if (this.fireController != null) {
            controllers.add(this.fireController);
        }

        BlockPos mountPos = getWeaponMountPos();

        if (this.level instanceof ServerLevel serverLevel && mountPos != null) {
            for (BlockPos firePos : WeaponNetworkRuntime.get(serverLevel).getFiringControllersForMount(mountPos)) {
                if (serverLevel.getBlockEntity(firePos) instanceof FireControllerBlockEntity fire) {
                    controllers.add(fire);
                }
            }
        }

        return controllers;
    }

    private boolean usesStructuralPitchForMount(@Nullable BlockPos mountPos) {
        return this.pitchController != null && this.pitchController.hasStructuralKineticSelectionForTargeting();
    }

    private boolean usesStructuralPitchForAuthority() {
        BlockPos mountPos = this.cannonMount != null
                ? this.cannonMount.getBlockPos() : this.weapon != null
                ? this.weapon.getMountPos() : null;

        return usesStructuralPitchForMount(mountPos);
    }

    /**
     * Returns whether this mount has a resolvable aiming profile.
     * Firing readiness is intentionally excluded.
     */
    public static boolean hasResolvableShot(@Nullable CannonMountContext mount, @Nullable ServerLevel level) {
        if (mount == null || level == null) {
            return false;
        }
        PitchOrientedContraptionEntity entity = mount.getContraption();
        if (entity == null || !entity.isAlive() || !(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return false;
        }
        WeaponShotProfile adapter = WeaponShotAdapterRegistry.resolve(createNativeShotContext(level, mount));

        if (adapter != null) {
            return adapter.aimMode() == WeaponShotProfile.AimMode.DIRECT
                    || adapter.aimMode() == WeaponShotProfile.AimMode.BALLISTIC
                    && adapter.projectileModel() != null;
        }

        if (CannonUtil.isCBCMWLauncher(cannon)) {
            return false;
        }

        if (CannonUtil.isLaserCannon(cannon)) {
            return true;
        }

        if (CannonUtil.isLaserCannon(cannon)) {
            return true;
        }
        if (CannonUtil.isCBCMSCannon(cannon)) {
            return CannonUtil.resolveCBCMSShotState(cannon, level) != null;
        }
        if (CannonUtil.isCBCATCannon(cannon)) {
            return CannonUtil.resolveCBCATShotState(cannon, level) != null;
        }
        if (CannonUtil.isCBCMWCannon(cannon)) {
            return CannonUtil.resolveCBCMWShotState(cannon, level) != null;
        }
        if (CannonUtil.isBigCannon(cannon)) {
            CannonUtil.BigCannonShotState shot =
                    CannonUtil.resolveBigCannonShotState(cannon, level);
            return shot != null && shot.hasProjectile()
                    && Float.isFinite(shot.speed()) && shot.speed() > 0.0F;
        }
        return !CannonUtil.isAutocannonFamily(cannon)
                || CannonUtil.resolveLoadedAutocannonBallistics(
                cannon, level) != null;
    }

    public static boolean hasResolvableShot(@Nullable RadarWeaponAdapter weapon, @Nullable ServerLevel level) {
        if (weapon == null || level == null || !weapon.isValid() || !weapon.isAssembled()) {
            return false;
        }

        WeaponShotContext context = new WeaponShotContext(level, weapon.createContext(level));
        WeaponShotProfile profile = WeaponShotAdapterRegistry.resolve(context);

        if (profile == null) {
            return false;
        }

        return profile.aimMode() == WeaponShotProfile.AimMode.DIRECT || profile.aimMode() == WeaponShotProfile.AimMode.BALLISTIC && profile.projectileModel() != null;
    }

    private static WeaponShotContext createNativeShotContext(ServerLevel level, CannonMountContext mount) {
        BlockPos mountPos = mount.getBlockPos();
        RadarWeaponAdapter weapon = RadarWeaponRegistry.find(level, mountPos);
        RadarMountAdapter radarMount = weapon != null ? weapon.getMount() : RadarMountRegistry.find(level, mountPos);

        return new WeaponShotContext(level, mountPos, radarMount, weapon);
    }

    private boolean isOutOfKnownRange(@Nullable Vec3 point) {
        if (point == null) {
            return true;
        } else {
            double max = this.pitchController != null ? this.pitchController.getMaxEngagementRangeBlocks() : (double)0.0F;
            if (max <= (double)0.0F) {
                return false;
            } else {
                Vec3 start = this.getCannonRayStart();
                return point.distanceToSqr(start) > max * max;
            }
        }
    }

    private Vec3 solveGenericBallisticAim(
            ServerLevel serverLevel, WeaponShotProfile shot,
            Vec3 targetPos, Vec3 targetVelocity, Vec3 targetAcceleration,
            @Nullable RigidBodyTargetMotion rigidBinoTarget
    ) {
        ProjectileModel model = shot.projectileModel() == null ? null : ProjectileModelBridge.toInternal(shot.projectileModel());

        if (model == null || !finite(shot.muzzlePosition()) || !finite(targetPos)) {
            return null;
        }

        Vec3 inheritedVelocity = finiteOrZero(shot.inheritedVelocity());

        TargetingSnapshot snapshot =
                TargetingSnapshot.builder(serverLevel)
                        .muzzlePosition(shot.muzzlePosition())
                        .inheritedVelocity(inheritedVelocity)
                        .targetPosition(targetPos)
                        .targetVelocity(targetVelocity)
                        .targetAcceleration(rigidBinoTarget != null ? Vec3.ZERO : targetAcceleration)
                        .targetSublevelId(this.binoMode && this.targetSublevel != null ? this.targetSublevel.getUniqueId() : null)
                        .rigidBodyTargetMotion(rigidBinoTarget)
                        .projectileSpeed(model.muzzleSpeed())
                        .gravity(model.gravity())
                        .drag(model.drag())
                        .quadraticDrag(model.quadraticDrag())
                        .cbcPhysics(model.cbcPhysics())
                        .dragDensity(model.dragDensity())
                        .maxFlightTicks(Math.max(1, shot.maxFlightTicks()))
                        .gameTime(serverLevel.getGameTime())
                        .targetMotionClass(TargetMotionClass.UNKNOWN)
                        .preferHighArc(this.targetingConfig.preferHighArc())
                        .build();

        TargetingResult result = this.targetingComputer.solve(snapshot, model);

        if (result == null || !result.valid() || !result.hasShot() || result.aimSolution() == null) {
            return null;
        }

        return result.aimSolution().aimDirection();
    }

    private boolean isPrimaryWeaponStateOk() {
        if (this.weapon != null) {
            return this.weapon.isValid() && this.weapon.isAssembled();
        }

        return isMountStateOk();
    }

    @Nullable
    private Vec3 resolveGenericTargetPosition() {
        if (this.binoMode) {
            return this.binoTargetPos == null ? null : this.binoTargetPos.getCenter();
        }

        if (this.activetrack != null) {
            return this.activetrack.position();
        }

        return this.target;
    }

    private void tickGenericWeapon() {
        if (!(this.level instanceof ServerLevel serverLevel)
                || this.weapon == null
                || !this.weapon.isValid()
                || !this.weapon.isAssembled()) {
            this.stopFireCannon();
            this.endStructuralRadarTracking();
            return;
        }

        WeaponShotContext shotContext = new WeaponShotContext(serverLevel, this.weapon.createContext(serverLevel));
        WeaponShotProfile shot = resolveAdapterShot(shotContext);

        if (shot == null || shot.aimMode() == WeaponShotProfile.AimMode.DISABLED) {
            this.stopFireCannon();
            this.endStructuralRadarTracking();
            return;
        }

        Vec3 targetPos = resolveGenericTargetPosition();

        SubLevelAccess designatedShip = null;

        if (this.binoMode && this.binoTargetPos != null && Mods.SABLE.isLoaded()) {
            designatedShip = SableCompanion.INSTANCE.getContaining(serverLevel, this.binoTargetPos);

            if (designatedShip != null) {
                targetPos = this.toWorldPosition(serverLevel, this.binoTargetPos);
            }
        }

        if (!finite(targetPos)) {
            this.stopFireCannon();
            this.endStructuralRadarTracking();
            return;
        }

        Vec3 targetVelocity = this.activetrack == null
                ? Vec3.ZERO
                : finiteOrZero(this.activetrack.velocity());

        Vec3 targetAcceleration = Vec3.ZERO;
        RigidBodyLaunchMath.Motion shipMotion = null;

        if (designatedShip != null
                && shot.aimMode() == WeaponShotProfile.AimMode.BALLISTIC) {

            shipMotion = SableLaunchMotion.capture(serverLevel, designatedShip);

            if (shipMotion != null) {
                targetVelocity = shipMotion.futureVelocity(targetPos, 0.0);

                targetAcceleration = clampAcceleration(
                        shipMotion.futureVelocity(targetPos, 1.0)
                                .subtract(targetVelocity));
            } else {
                targetVelocity = VS2ShipVelocityTracker.getShipVelocityPerTick(
                        designatedShip, serverLevel, targetPos);
            }
        }

        Vec3 observedPos = targetPos;
        Vec3 observedVelocity = targetVelocity;
        Vec3 observedAcceleration = targetAcceleration;

        RadarTargetingSolution targetSolution = resolvePublicTargetingSolution(serverLevel, targetPos, targetVelocity, targetAcceleration);

        targetPos = targetSolution.position();
        targetVelocity = targetSolution.velocity();
        targetAcceleration = targetSolution.acceleration();

        boolean unmodifiedPoint = finite(targetPos)
                && finite(targetVelocity)
                && finite(targetAcceleration)
                && observedPos.distanceToSqr(targetPos) <= 1.0e-8
                && observedVelocity.distanceToSqr(targetVelocity) <= 1.0e-12
                && observedAcceleration.distanceToSqr(targetAcceleration) <= 1.0e-12;

        if (this.binoMode) {
            this.targetSublevel = unmodifiedPoint ? designatedShip : null;
            this.targetShipId = this.targetSublevel == null ? null : this.targetSublevel.getUniqueId();
            this.rigidTargetExternalMotionOverride = designatedShip != null && !unmodifiedPoint;
        }

        RigidBodyTargetMotion rigidBinoTarget = shipMotion != null && unmodifiedPoint ? new RigidBodyTargetMotion(shipMotion, observedPos, 0.0) : null;

        Vec3 aimDirection;
        if (shot.aimMode() == WeaponShotProfile.AimMode.DIRECT) {
            aimDirection = directionFromTo(shot.muzzlePosition(), targetPos);
        } else {
            aimDirection = solveGenericBallisticAim(serverLevel, shot, targetPos, targetVelocity, targetAcceleration, rigidBinoTarget);
        }

        if (!finite(aimDirection) || aimDirection.lengthSqr() < 1.0E-12) {
            this.stopFireCannon();
            return;
        }

        TargetingMath.YawPitch aim = TargetingMath.yawPitchFromDirection(aimDirection);

        double desiredPitch = aim.pitchDeg();
        double desiredYaw = wrap360(aim.yawDeg() + 270.0);

        if (!Double.isFinite(desiredPitch) || !Double.isFinite(desiredYaw)) {
            this.stopFireCannon();
            return;
        }

        FiringAlignmentTolerance firingTolerance = firingAlignmentTolerance(shot.muzzlePosition().distanceTo(targetPos), RadarConfig.server().autoFireMaxLateralErrorBlocks.get(), this.activeAlignmentAxisCount());
        this.lastFiringAlignmentTolerance = firingTolerance;

        boolean structuralPitchAccepted = true;
        if (this.pitchController != null) {
            if (usesStructuralPitchForAuthority()) {
                structuralPitchAccepted = this.pitchController.setRadarAimDirection(aimDirection, firingTolerance.maximumDegrees());
            } else {
                this.pitchController.setTargetAngle((float) desiredPitch);
            }
        }

        boolean yawAimAccepted = true;

        if (this.yawController != null) {
            if (this.yawController.hasStructuralKineticSelectionForTargeting()) {
                yawAimAccepted = this.yawController.setRadarAimDirection(aimDirection, firingTolerance.maximumDegrees());
            } else {
                this.yawController.setTargetAngle((float) desiredYaw);
            }
        }

        if (pitchController != null && (structuralPitchAccepted || pitchController.isTargetLimitConstrained())) {
            boolean pitchLimited = pitchController.isTargetLimitConstrained();
            boolean yawLimited = yawController != null && yawController.isTargetLimitConstrained();
            recordAutoAimLimitFeedback(!pitchLimited && !yawLimited);
        }

        double minimumTolerance = shot.minimumFiringToleranceDegrees();
        boolean aligned = hasCorrectGenericYawPitch(false, minimumTolerance, firingTolerance.maximumDegrees(), aimDirection);
        boolean safe = !this.passesSafeZone();
        boolean shouldFire = this.targetingConfig.autoFire() && shot.canTriggerFire() && structuralPitchAccepted && yawAimAccepted && aligned && safe;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "firing.api_gates",
                    serverLevel,
                    getWeaponMountPos(),
                    "targetPosition", targetPos,
                    "targetVelocity", targetVelocity,
                    "targetAcceleration", targetAcceleration,
                    "aimDirection", aimDirection,
                    "desiredYaw", desiredYaw,
                    "desiredPitch", desiredPitch,
                    "aimMode", shot.aimMode(),
                    "autoFire", this.targetingConfig.autoFire(),
                    "structuralPitchAccepted", structuralPitchAccepted,
                    "yawAimAccepted", yawAimAccepted,
                    "aligned", aligned,
                    "safeZoneAllowed", safe,
                    "minimumTolerance", minimumTolerance,
                    "maximumTolerance", firingTolerance.maximumDegrees(),
                    "shouldFireBeforePreparation", shouldFire
            );
        }

        if (shouldFire && !prepareAdapterShotForFire(shot, shotContext)) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "firing.api_rejected",
                        serverLevel,
                        getWeaponMountPos(),
                        "reason", "ADAPTER_FIRE_PREPARATION_FAILED",
                        "fingerprint", shot.fingerprint()
                );
            }

            shouldFire = false;
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "firing.api_final",
                    serverLevel,
                    getWeaponMountPos(),
                    "shouldFire", shouldFire,
                    "aimMode", shot.aimMode(),
                    "aligned", aligned,
                    "safeZoneAllowed", safe
            );
        }

        if (shouldFire) {
            tryFireCannon();
        } else {
            stopFireCannon();
        }
    }

    private boolean hasCorrectGenericYawPitch(boolean lag, double minimumToleranceDegrees, double maximumToleranceDegrees, @Nullable Vec3 requestedWorldDirection) {
        if (this.pitchController == null) {
            return false;
        }

        boolean pitch = this.pitchController.isAlignedForFiring(lag, minimumToleranceDegrees, maximumToleranceDegrees);
        RadarMountAdapter mount = this.weapon == null ? null : this.weapon.getMount();
        boolean yaw;

        if (this.yawController != null) {
            yaw = this.yawController.isAlignedForFiring(lag, minimumToleranceDegrees, maximumToleranceDegrees);
        } else {
            yaw = mount == null || !mount.supportsYaw();
        }

        return pitch && yaw;
    }


    public void tick() {
        if (yawOnlyMountPos != null) {
            if (!(level instanceof ServerLevel serverLevel) || yawController == null) {
                stopFireCannon();
                return;
            }

            boolean validShot = cannonMount != null ? hasResolvableShot(cannonMount, serverLevel) : hasResolvableShot(weapon, serverLevel);
            if (!validShot) {
                if (yawController.isControllerRunning()) {
                    yawController.failClosedRadarAim();
                }

                stopFireCannon();
                return;
            }
        }

        if (!this.topologyValid) {
            this.stopFireCannon();
            this.endStructuralRadarTracking();
            return;
        }

        if (this.cannonMount == null) {
            tickGenericWeapon();
            return;
        }

        this.samplePhysicalYawRate();

        if (!this.isMountStateOk()) {
            this.stopFireCannon();
            this.endStructuralRadarTracking();
        } else {
            if (this.binoMode) {
                this.lastTargetTick = this.level.getGameTime();
            } else if (this.activetrack != null && (this.activetrack.isSynthetic()
                    || this.targetEntity != null || this.targetSublevel != null)) {
                this.lastTargetTick = this.level.getGameTime();
            }

            if (!this.binoMode && this.activetrack == null) {
                this.stopFireCannon();
                this.endStructuralRadarTracking();
            } else {
                if (!this.binoMode && this.activetrack != null) {
                    if (this.level instanceof ServerLevel sl) {
                        if (this.activetrack.isSynthetic()) {
                            this.targetEntity = null;
                            this.targetSublevel = null;
                            this.targetShipId = null;
                        } else {
                            boolean isSableShip = Mods.SABLE.isLoaded() && "Sable:ship".equals(this.activetrack.entityType());
                            if (isSableShip) {
                                UUID id;
                                try {
                                    id = UUID.fromString(this.activetrack.id());
                                } catch (IllegalArgumentException var43) {
                                    LOGGER.debug("WFC: invalid Sable ship id={}, stopping fire", this.activetrack.id());
                                    this.stopFireCannon();
                                    return;
                                }

                                if (this.targetSublevel == null || !id.equals(this.targetShipId)) {
                                    this.targetSublevel = this.getShipByUUID(sl, this.activetrack.id());
                                    this.targetShipId = id;
                                    if (this.targetSublevel == null) {
                                        LOGGER.debug("WFC: Sable ship id={} not loaded, stopping fire", id);
                                        this.stopFireCannon();
                                        return;
                                    }
                                }

                                this.targetEntity = null;
                            } else {
                                Entity e = null;

                                try {
                                    e = this.getEntityByUUID(sl, UUID.fromString(this.activetrack.id()));
                                } catch (Throwable var42) {}

                                if (e == null || !e.isAlive()) {
                                    LOGGER.debug("WFC: entity id={} not loaded/alive, stopping fire", this.activetrack.id());
                                    this.stopFireCannon();
                                    return;
                                }

                                this.targetEntity = e;
                                this.targetSublevel = null;
                            }
                        }
                    }
                }

                if (!this.binoMode && this.activetrack != null
                        && !this.activetrack.isSynthetic()
                        && this.targetEntity == null && this.targetSublevel == null) {
                    LOGGER.debug("WFC: no resolved target entity/ship, stopping fire (trackId={})", this.activetrack.id());
                    this.stopFireCannon();
                } else {
                    if (!this.binoMode) {
                        if (this.activetrack != null && this.activetrack.isSynthetic()) {
                            this.target = this.activetrack.position();
                        } else if (this.targetSublevel != null) {
                            this.target = RadarTrackUtil.getPosition(this.targetSublevel);
                        } else if (this.targetEntity != null) {
                            this.target = this.getEntityAimPoint(this.targetEntity);
                        }
                    } else {
                        this.target = this.binoTargetPos.getCenter();
                    }

                    if (this.cannonMount.getContraption() != null) {
                        Contraption contraption = this.cannonMount.getContraption().getContraption();
                        if (contraption instanceof AbstractMountedCannonContraption cannon) {
                            if (this.level instanceof ServerLevel serverLevel) {
                                WeaponShotContext adapterContext = createNativeShotContext(serverLevel, this.cannonMount);
                                WeaponShotProfile adapterShot = WeaponShotAdapterRegistry.resolve(adapterContext);

                                if (adapterShot != null && adapterShot.aimMode() == WeaponShotProfile.AimMode.DISABLED) {
                                    if (MotionTraceRecorder.isEnabled()) {
                                        MotionTraceRecorder.record("firing.early_rejected", serverLevel, this.cannonMount.getBlockPos(), "reason", "WEAPON_PROFILE_DISABLED");
                                    }
                                    this.clearTargetingResultCache();
                                    this.stopFireCannon();
                                    this.endStructuralRadarTracking();
                                    return;
                                }

                                if (this.targetEntity != null && !this.targetEntity.isAlive()) {
                                    LOGGER.debug("WFC: target entity died mid-tick, stopping fire (id={})", this.targetEntity.getUUID());
                                    this.stopFireCannon();
                                } else {
                                    if (this.targetSublevel != null && !this.binoMode) {
                                        UUID id = UUID.fromString(this.activetrack.id());
                                        SubLevelAccess live = SableWeaponFiringAccess.findSubLevel(serverLevel, id);
                                        if (live == null) {
                                            LOGGER.debug("WFC: Sable ship id={} unloaded mid-tick, stopping fire", id);
                                            if (MotionTraceRecorder.isEnabled()) {
                                                MotionTraceRecorder.record("firing.early_rejected", serverLevel, this.cannonMount.getBlockPos(), "reason", "SABLE_TARGET_UNLOADED", "targetShipId", id);
                                            }
                                            this.stopFireCannon();
                                            return;
                                        }

                                        this.targetSublevel = live;
                                    }

                                    Vec3 cannonMuzzleWorld = adapterShot == null
                                            ? this.resolveMuzzleWorldPosition(cannon)
                                            : adapterShot.muzzlePosition();
                                    Vec3 shooterVel = adapterShot == null
                                            ? this.getPlatformVelocityAtMuzzle(serverLevel, cannonMuzzleWorld)
                                            : finiteOrZero(adapterShot.inheritedVelocity());
                                    Vec3 targetVel;
                                    Vec3 targetAccel;
                                    Vec3 rawTargetPos;
                                    UUID targetMotionId = null;
                                    this.currentSublevelAim = null;
                                    this.rigidTargetExternalMotionOverride = false;
                                    if (this.targetSublevel != null && !this.binoMode) {
                                        Vec3 sublevelAimOrigin = finite(cannonMuzzleWorld) ? cannonMuzzleWorld : this.getCannonRayStart();
                                        if (!finite(sublevelAimOrigin)) {
                                            if (MotionTraceRecorder.isEnabled()) {
                                                MotionTraceRecorder.record("firing.early_rejected", serverLevel, this.cannonMount.getBlockPos(), "reason", "INVALID_SABLE_AIM_ORIGIN", "aimOrigin", sublevelAimOrigin);
                                            }

                                            this.stopFireCannon();
                                            return;
                                        }
                                        ResolvedSublevelAim sublevelAim =
                                                this.resolveSublevelAim(
                                                        serverLevel,
                                                        this.targetSublevel,
                                                        sublevelAimOrigin,
                                                        this.targetingConfig
                                                                .lineOfSight(),
                                                        true);
                                        if (sublevelAim == null) {
                                            if (MotionTraceRecorder.isEnabled()) {
                                                MotionTraceRecorder.record("firing.early_rejected", serverLevel, this.cannonMount.getBlockPos(), "reason", "SABLE_AIM_RESOLUTION_FAILED", "targetShipId", this.targetSublevel == null ? null : this.targetSublevel.getUniqueId());
                                            }
                                            this.stopFireCannon();
                                            return;
                                        }
                                        this.currentSublevelAim = sublevelAim;
                                        rawTargetPos = sublevelAim.worldPoint();
                                        this.target = this.toWorldPosition(serverLevel, rawTargetPos, this.targetSublevel);
                                        Vec3 authoritativeMotionVelocity = this.toWorldVelocity(serverLevel, this.targetSublevel, this.target);
                                        targetVel = this.getSublevelTargetVelocityPerTick(
                                                this.targetSublevel,
                                                this.target,
                                                serverLevel.getGameTime(),
                                                authoritativeMotionVelocity);
                                        targetAccel = AccelerationTracker.getAccelerationPerTick2(
                                                this.sublevelMotionSampleId(this.targetSublevel.getUniqueId()),
                                                targetVel,
                                                serverLevel.getGameTime());
                                        targetMotionId = this.targetSublevel.getUniqueId();
                                    } else if (!this.binoMode && this.activetrack != null && this.activetrack.isSynthetic()) {
                                        rawTargetPos = this.activetrack.position();
                                        this.target = rawTargetPos;
                                        targetVel = this.activetrack.velocity();
                                        targetAccel = Vec3.ZERO;
                                    } else if (!this.binoMode && this.targetEntity != null) {
                                        rawTargetPos = this.getEntityAimPoint(this.targetEntity);
                                        this.target = this.toWorldPosition(serverLevel, rawTargetPos, this.targetEntity);
                                        targetVel = VelocityTracker.getEstimatedVelocityPerTick(this.targetEntity);
                                        targetAccel = AccelerationTracker.getAccelerationPerTick2(this.targetEntity.getUUID(), targetVel, serverLevel.getGameTime());
                                        targetMotionId = this.targetEntity.getUUID();
                                    } else {
                                        if (!this.binoMode || this.binoTargetPos == null) {
                                            return;
                                        }

                                        this.target = this.toWorldPosition(serverLevel, this.binoTargetPos);
                                        rawTargetPos = this.target;

                                        SubLevelAccess designatedShip = Mods.SABLE.isLoaded() ? SableCompanion.INSTANCE.getContaining(serverLevel, this.binoTargetPos) : null;
                                        if (designatedShip != null) {
                                            this.targetSublevel = designatedShip;
                                            this.targetShipId = designatedShip.getUniqueId();
                                            targetMotionId = this.targetShipId;

                                            Vec3 measuredVelocity = this.toWorldVelocity(serverLevel, designatedShip, this.target);
                                            targetVel = this.getSublevelTargetVelocityPerTick(designatedShip, this.target, serverLevel.getGameTime(), measuredVelocity);
                                            targetAccel = AccelerationTracker.getAccelerationPerTick2(this.sublevelMotionSampleId(this.targetShipId), targetVel, serverLevel.getGameTime());
                                        } else {
                                            this.targetSublevel = null;
                                            this.targetShipId = null;
                                            targetVel = Vec3.ZERO;
                                            targetAccel = Vec3.ZERO;
                                        }
                                    }

                                    TargetMotionEstimate motion = this.estimateTargetMotion(serverLevel, targetMotionId, this.targetEntity, this.target, targetVel, targetAccel);
                                    targetVel = motion.velocity();
                                    targetAccel = motion.acceleration();

                                    RadarTrack guidanceTrack = this.binoMode ? null : this.activetrack;
                                    JammedGuidance guidance = applyJammingGuidance(this.target, targetVel, guidanceTrack);

                                    this.target = guidance.position();
                                    targetVel = guidance.velocity();

                                    Vec3 preApiVelocity = targetVel;
                                    Vec3 preApiAcceleration = targetAccel;

                                    RadarTargetingSolution publicTarget = resolvePublicTargetingSolution(serverLevel, this.target, targetVel, targetAccel);
                                    this.rigidTargetExternalMotionOverride = this.targetSublevel != null && (preApiVelocity.distanceToSqr(publicTarget.velocity()) > 1.0e-12 || preApiAcceleration.distanceToSqr(publicTarget.acceleration()) > 1.0e-12);

                                    this.target = publicTarget.position();
                                    targetVel = publicTarget.velocity();
                                    targetAccel = publicTarget.acceleration();

                                    //LOGGER.debug("[RADAR-TRACE][TARGET] tick={} mount={} cannon={} " + "targetId={} targetShip={} shooterOnSable={} " + "rawTarget={} worldTarget={} " + "velocity={} acceleration={} " + "preApiVelocity={} preApiAcceleration={} " + "apiMotionOverride={} " + "muzzle={} shooterVelocity={} motionClass={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), cannon.getClass().getSimpleName(), targetMotionId, this.targetSublevel == null ? null : this.targetSublevel.getUniqueId(), this.isSableMount(), rawTargetPos, this.target, targetVel, targetAccel, preApiVelocity, preApiAcceleration, this.rigidTargetExternalMotionOverride, cannonMuzzleWorld, shooterVel, motion.motionClass());

                                    if (guidanceTrack != null && guidanceTrack.getJammingData() != null) {
                                        rawTargetPos = this.target;
                                    }

                                    double dist = this.getCannonRayStart().distanceTo(this.target);
                                    double noLeadDist = (double)1.0F;
                                    Vec3 solvePos = this.target;

                                    if (!this.binoMode && this.targetEntity != null) {
                                        if (!this.checkLineOfSight(this.target)) {
                                            if (MotionTraceRecorder.isEnabled()) {
                                                MotionTraceRecorder.record("firing.early_rejected", serverLevel, this.cannonMount.getBlockPos(), "reason", "TARGET_LINE_OF_SIGHT_BLOCKED", "targetId", this.targetEntity.getUUID(), "targetPosition", this.target);
                                            }
                                            LOGGER.debug("WFC: LOS blocked to entity, stopping fire (id={})", this.targetEntity.getUUID());
                                            this.stopFireCannon();
                                            return;
                                        }

                                        solvePos = this.target;
                                    }

                                    boolean lag = !motion.looseAim();
                                    WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(serverLevel);

                                    WeaponNetworkRuntime.WeaponGroupView grp = this.pitchController != null
                                            ? runtime.getWeaponGroupViewFromEndpoint(this.pitchController.getBlockPos()) : this.yawController != null
                                            ? runtime.getWeaponGroupViewFromEndpoint(this.yawController.getBlockPos()) : null;
                                    if (grp != null && !grp.dataLinks().isEmpty()) {
                                        Vec3 cannonOrigin = this.getCannonRayStart();
                                        double best = (double)0.0F;

                                        for(BlockPos dlPos : grp.dataLinks()) {
                                            BlockEntity be = serverLevel.getBlockEntity(dlPos);
                                            if (be instanceof DataLinkBlockEntity dl) {
                                                BlockPos srcPos = dl.getSourcePosition();
                                                BlockEntity srcBe = serverLevel.getBlockEntity(srcPos);
                                                if (srcBe instanceof IRadar radar) {
                                                    Vec3 radarWorldPos = PhysicsHandler.getWorldVec(srcBe);
                                                    double d = cannonOrigin.distanceTo(radarWorldPos);
                                                    double cap = (double)radar.getRange() + d;
                                                    if (cap > best) {
                                                        best = cap;
                                                    }
                                                }
                                            }
                                        }

                                        if (best > (double)0.0F) {
                                            this.maxSimDistanceBlocks = best;
                                        }
                                    }

                                    int trackingLeadTicks = this.stableTargetTrackingLeadTicks(targetMotionId, motion.motionClass(), serverLevel.getGameTime());
                                    Vec3 trackingSolvePos = predictTarget(solvePos, targetVel, targetAccel, (double)trackingLeadTicks, motion.motionClass(), (double)-0.08F);
                                    CannonLead.LeadSolution lead = null;
                                    TargetingResult targetingResult = null;
                                    boolean targetingResultFreshForFire = true;
                                    boolean targetingResultProvisional = false;
                                    boolean forceLegacyLead = (Boolean)RadarConfig.server().forceLegacyCannonLeadSolver.get();
                                    ResolvedProjectileState currentProjectile = this.resolveProjectileState(cannon, serverLevel);
                                    boolean adapterBallisticAim = adapterShot != null && adapterShot.aimMode() == WeaponShotProfile.AimMode.BALLISTIC;
                                    boolean adapterDirectAim = adapterShot != null && adapterShot.aimMode() == WeaponShotProfile.AimMode.DIRECT;
                                    boolean directAimRequired = adapterDirectAim || adapterShot == null && CannonUtil.isLaserCannon(cannon);
                                    boolean rejectCustomLegacy = forceLegacyLead && (currentProjectile == null ? CannonUtil.isPoweredRocket(cannon) || CannonUtil.isCBCMSCannon(cannon) : !currentProjectile.legacyEligible());
                                    boolean rejectCustomDirect = currentProjectile == null ? CannonUtil.isPoweredRocket(cannon) || CannonUtil.isCBCMSCannon(cannon) : !currentProjectile.legacyEligible();
                                    boolean useNewSolver = adapterBallisticAim || !forceLegacyLead
                                            && (Boolean)RadarConfig.server().useNewTargetingComputer.get()
                                            && !CannonUtil.isLaserCannon(cannon);
                                    if (useNewSolver && currentProjectile != null && currentProjectile.solverKind() == SolverKind.CBCMS_SERVER) {
                                        boolean sameAttempt = this.lastCBCMSSolveAttemptTick == serverLevel.getGameTime()
                                                && currentProjectile.fingerprint().equals(this.lastCBCMSSolveFingerprint)
                                                && (targetMotionId == null
                                                ? this.lastCBCMSSolveTargetId == null
                                                : targetMotionId.equals(this.lastCBCMSSolveTargetId));
                                        if (sameAttempt) {
                                            targetingResult = this.lastCBCMSSolveResult;
                                        } else {
                                            targetingResult = this.solveWithTargetingComputer(serverLevel, cannon, rawTargetPos,
                                                    solvePos, guidance.positionOffset(), targetVel, targetAccel, shooterVel, this.targetEntity,
                                                    this.targetSublevel, motion.motionClass(), trackingLeadTicks);
                                            this.lastCBCMSSolveAttemptTick = serverLevel.getGameTime();
                                            this.lastCBCMSSolveTargetId = targetMotionId;
                                            this.lastCBCMSSolveFingerprint = currentProjectile.fingerprint();
                                            this.lastCBCMSSolveResult = targetingResult;
                                            this.storeTargetingResult(serverLevel, targetMotionId, solvePos, currentProjectile.fingerprint(), targetingResult);
                                        }
                                        targetingResultFreshForFire = lastSyncBallisticReachableForFire;
                                    } else if (useNewSolver && currentProjectile != null) {
                                        targetingResult = this.pollCompletedAsyncTargetingResult(serverLevel, cannon, targetMotionId, solvePos);
                                        boolean completedAsyncResult = targetingResult != null;
                                        boolean solveEveryTick = CannonUtil.isCBCATCannon(cannon) || this.shouldSolveEveryTick(targetVel, motion, dist);
                                        if (targetingResult == null && this.shouldReuseTargetingResult(serverLevel, targetMotionId, cannon, solvePos, currentProjectile.fingerprint())) {
                                            targetingResult = this.rebaseCachedTargetingResult(cannon, solvePos);
                                        }

                                        targetingResultFreshForFire = targetingResult != null && (cachedBallisticReachableForFire || cachedBallisticSolutionAccepted) && this.isCachedTargetingResultFreshForFire(serverLevel, cannon, targetMotionId, solvePos, currentProjectile.fingerprint());
                                        targetingResultProvisional = targetingResult != null && !targetingResultFreshForFire;
                                        this.lastTargetingSteeringProvisional = targetingResultProvisional;
                                        this.lastTargetingFireFresh = targetingResultFreshForFire;
                                        this.lastTargetingFreshnessReason = targetingResult != null && !cachedBallisticReachableForFire ? (isExternallyTriggeredYawMount(serverLevel) ? "external_trigger_no_local_launch_schedule" : "ballistic_launch_unreachable") : this.asyncFreshnessReason(serverLevel, cannon, targetMotionId, solvePos, currentProjectile.fingerprint());

                                        if (this.shouldRefreshAsyncTargeting(serverLevel, solveEveryTick, completedAsyncResult)) {
                                            this.submitAsyncTargetingSolve(
                                                    serverLevel, cannon, rawTargetPos,
                                                    solvePos, guidance.positionOffset(), targetVel, targetAccel,
                                                    shooterVel, this.targetEntity,
                                                    this.targetSublevel,
                                                    motion.motionClass(),
                                                    trackingLeadTicks, targetMotionId);
                                        }
                                    }

                                    if (MotionTraceRecorder.isEnabled()
                                            && targetingResult != null
                                            && targetingResult.valid()
                                            && targetingResult.hasShot()
                                            && targetingResult.aimSolution() != null
                                            && cachedTargetingRequest != null
                                            && Objects.equals(cachedTargetingTargetId, targetMotionId)) {
                                        AimSolution verifiedAim = targetingResult.aimSolution();
                                        int requiredSlew = estimateSlewTicksForSolution(cannon, verifiedAim, targetingResult.desiredYawDeg() + 270.0, targetingResult.desiredPitchDeg());
                                        int plannedDelay = cachedTargetingRequest.leadTicks();
                                        int availableSlew = Math.max(0, plannedDelay - trackingLeadTicks);

                                        MotionTraceRecorder.record(
                                                "targeting.launch_feasibility_verification",
                                                serverLevel,
                                                getWeaponMountPos(),
                                                "plannedDelayTicks", plannedDelay,
                                                "requiredSlewTicks", requiredSlew,
                                                "availableSlewTicks", availableSlew,
                                                "verifiedReachable", requiredSlew <= availableSlew,
                                                "actualFlightTicks", targetingResult.predictedFlightTicks(),
                                                "desiredYaw", targetingResult.desiredYawDeg(),
                                                "desiredPitch", targetingResult.desiredPitchDeg(),
                                                "measuredYawVelocity", measuredPhysicalYawRate
                                        );
                                    }

                                    boolean newSolverOk = targetingResult != null && targetingResult.valid() && targetingResult.hasShot() && targetingResult.confidence() >= motion.minConfidence();
                                    if (forceLegacyLead
                                            && currentProjectile != null
                                            && !rejectCustomLegacy
                                            && !CannonUtil.isLaserCannon(cannon)
                                            && dist > noLeadDist) {
                                        lead = CannonLead.solveLeadPerTickConstantVelocity(this.cannonMount, cannon, serverLevel, shooterVel, solvePos, targetVel, (Integer)RadarConfig.server().leadFiringDelay.get() + trackingLeadTicks, this.maxSimDistanceBlocks, this.targetingConfig.preferHighArc());
                                    }

                                    boolean hasLeadSolution = lead != null && lead.aimPoint != null;
                                    boolean hasNewTargetingSolution = newSolverOk && targetingResult.aimSolution() != null;
                                    boolean hasFireEligibleNewTargetingSolution = hasNewTargetingSolution && targetingResultFreshForFire;
                                    boolean canFireWithoutLead = directAimRequired;
                                    boolean ballisticAimExpected = !canFireWithoutLead;
                                    AimUpdateMode aimUpdateMode = selectAimUpdateMode(
                                            hasResolvedAimForUpdate(
                                                    hasNewTargetingSolution,
                                                    hasLeadSolution,
                                                    canFireWithoutLead,
                                                    directAimRequired),
                                            ballisticAimExpected);
                                    Vec3 offsetAim = hasNewTargetingSolution
                                            && targetingResult.aimSolution().aimPoint() != null
                                            ? targetingResult.aimSolution().aimPoint()
                                            : (hasLeadSolution ? lead.aimPoint
                                            : (aimUpdateMode == AimUpdateMode.HOLD
                                            ? this.lastAimPoint
                                            : (directAimRequired ? solvePos : trackingSolvePos)));

                                    Double desiredPitch = null;
                                    Double desiredYaw = null;
                                    MountAimFrame mountFrame = this.resolveMountAimFrame(cannon);
                                    AimCommandEvaluation aimEvaluation = null;
                                    Vec3 worldAimDirection = hasNewTargetingSolution ? targetingResult.aimSolution().aimDirection() : null;
                                    boolean sourceUsesSublevelFrame = mountFrame.sourceSublevelId() != null;
                                    if (sourceUsesSublevelFrame) {
                                        if (directAimRequired) {
                                            worldAimDirection = directionFromTo(cannonMuzzleWorld, solvePos);
                                            aimEvaluation = mountFrame.evaluate(worldAimDirection);
                                            if (aimEvaluation.valid()) {
                                                desiredPitch = aimEvaluation.requestedPitchDeg();
                                                desiredYaw = aimEvaluation.requestedControllerYawDeg();
                                            } else {
                                                worldAimDirection = null;
                                            }
                                        } else if (hasNewTargetingSolution) {
                                            aimEvaluation = mountFrame.evaluate(targetingResult.aimSolution().aimDirection());
                                            if (aimEvaluation.valid()) {
                                                desiredPitch = aimEvaluation.requestedPitchDeg();
                                                desiredYaw = aimEvaluation.requestedControllerYawDeg();
                                                this.cachedSablePitchDeg = desiredPitch;
                                                this.cachedSableYawDeg = desiredYaw;
                                            } else {
                                                hasNewTargetingSolution = false;
                                                worldAimDirection = null;
                                                aimUpdateMode = selectAimUpdateMode(hasLeadSolution || canFireWithoutLead, ballisticAimExpected);
                                            }
                                        } else if (hasLeadSolution || canFireWithoutLead) {
                                            long now = this.level.getGameTime();
                                            int sableSolveInterval = motion.motionClass() == TargetMotionClass.ERRATIC ? 1 : SABLE_SOLVE_INTERVAL;
                                            boolean needSolve = this.cachedSableAngles == null || now - this.cachedSableSolveTick >= (long)sableSolveInterval || this.cachedSableAimTarget == null || this.cachedSableAimTarget.distanceToSqr(offsetAim) > 0.09;
                                            if (needSolve) {
                                                this.cachedSableAngles = VS2CannonTargeting.calculatePitchAndYawVS2(this.cannonMount, offsetAim, serverLevel, this.cachedSablePitchDeg, this.cachedSableYawDeg);
                                                this.cachedSableAimTarget = offsetAim;
                                                this.cachedSableSolveTick = now;
                                                this.cachedSableWorldAimDirection = null;
                                                if (this.cachedSableAngles != null && !this.cachedSableAngles.isEmpty()) {
                                                    List<Double> solved = this.cachedSableAngles.get(0);
                                                    if (solved.size() >= 2) {
                                                        this.cachedSableWorldAimDirection = this.worldDirectionFromMountCommand(mountFrame, solved.get(0), solved.get(1));
                                                    }
                                                }
                                            }

                                            worldAimDirection = this.cachedSableWorldAimDirection;
                                            aimEvaluation = mountFrame.evaluate(worldAimDirection);
                                            if (aimEvaluation.valid()) {
                                                desiredPitch = aimEvaluation.requestedPitchDeg();
                                                desiredYaw = aimEvaluation.requestedControllerYawDeg();
                                                this.cachedSablePitchDeg = desiredPitch;
                                                this.cachedSableYawDeg = desiredYaw;
                                            }
                                        } else if (aimUpdateMode == AimUpdateMode.DIRECT && !rejectCustomDirect) {
                                            worldAimDirection = directionFromTo(cannonMuzzleWorld, solvePos);
                                            aimEvaluation = mountFrame.evaluate(worldAimDirection);
                                            if (aimEvaluation.valid()) {
                                                desiredPitch = aimEvaluation.requestedPitchDeg();
                                                desiredYaw = aimEvaluation.requestedControllerYawDeg();
                                            }
                                        }
                                    } else if (hasNewTargetingSolution) {
                                        desiredPitch = targetingResult.desiredPitchDeg();
                                        desiredYaw = targetingResult.desiredYawDeg() + (double)270.0F;
                                    } else if (hasLeadSolution) {
                                        Vec3 origin = this.getCannonRayStart();
                                        desiredYaw = this.calculateControllerYaw(origin, offsetAim);
                                        List<Double> pitchRoots = CannonTargeting.calculatePitch(this.cannonMount, origin, offsetAim, serverLevel);
                                        if (pitchRoots != null && !pitchRoots.isEmpty()) {
                                            desiredPitch = selectPitchRoot(pitchRoots, this.targetingConfig.preferHighArc());
                                            worldAimDirection = this.worldDirectionFromMountCommand(desiredPitch, desiredYaw);
                                        }
                                    } else if (aimUpdateMode == AimUpdateMode.DIRECT && !rejectCustomDirect) {
                                        worldAimDirection = directionFromTo(cannonMuzzleWorld, solvePos);
                                        aimEvaluation = mountFrame.evaluate(worldAimDirection);
                                        if (aimEvaluation.valid()) {
                                            desiredPitch = aimEvaluation.requestedPitchDeg();
                                            desiredYaw = aimEvaluation.requestedControllerYawDeg();
                                        }
                                    }

                                    if (sourceUsesSublevelFrame
                                            && aimUpdateMode != AimUpdateMode.HOLD
                                            && mountFrame.kind() != MountFrameKind.UNAVAILABLE
                                            && (aimEvaluation == null || !aimEvaluation.valid())
                                            && !hasNewTargetingSolution
                                            && !hasLeadSolution) {
                                        Vec3 boundaryDirection = directionFromTo(cannonMuzzleWorld, solvePos);
                                        AimCommandEvaluation boundaryEvaluation = mountFrame.evaluate(boundaryDirection);
                                        aimEvaluation = boundaryEvaluation;
                                        if (boundaryEvaluation.valid() && boundaryEvaluation.constrained()) {
                                            desiredPitch = boundaryEvaluation.requestedPitchDeg();
                                            desiredYaw = boundaryEvaluation.requestedControllerYawDeg();
                                            worldAimDirection = boundaryDirection;
                                            aimUpdateMode = AimUpdateMode.DIRECT;
                                        }
                                    }
                                    if (sourceUsesSublevelFrame
                                            && aimEvaluation == null
                                            && mountFrame.kind()
                                            == MountFrameKind.UNAVAILABLE) {
                                        aimEvaluation = mountFrame.evaluate(null);
                                    }

                                    boolean binocularSteeringFallback = this.binoMode
                                            && aimUpdateMode == AimUpdateMode.HOLD
                                            && !hasNewTargetingSolution
                                            && !hasLeadSolution
                                            && !canFireWithoutLead;

                                    if (binocularSteeringFallback) {
                                        Vec3 directDirection = directionFromTo(cannonMuzzleWorld, solvePos);
                                        AimCommandEvaluation directEvaluation = mountFrame.evaluate(directDirection);

                                        if (directEvaluation.valid()) {
                                            aimEvaluation = directEvaluation;
                                            worldAimDirection = directDirection;

                                            desiredPitch = directEvaluation.appliedPitchDeg();
                                            desiredYaw = directEvaluation.appliedControllerYawDeg();

                                            offsetAim = solvePos;
                                            aimUpdateMode = AimUpdateMode.DIRECT;
                                        }
                                    }

                                    this.lastAimCommandEvaluation = aimEvaluation;

                                    boolean directAdapterAimReady =
                                            isDirectAdapterAimReady(
                                                    directAimRequired,
                                                    desiredPitch != null
                                                            && Double.isFinite(desiredPitch)
                                                            && desiredYaw != null
                                                            && Double.isFinite(desiredYaw)
                                                            && finite(worldAimDirection)
                                                            && worldAimDirection.lengthSqr()
                                                            >= 1.0E-12);
                                    boolean aimWithinMovementLimits = aimEvaluation != null ? aimEvaluation.fireEligible() : !sourceUsesSublevelFrame;
                                    if (!directAdapterAimReady) {
                                        this.aimStableTicks = 0;
                                        this.lastOffsetAim = null;
                                    } else if (aimUpdateMode != AimUpdateMode.HOLD) {
                                        this.lastAimPoint = offsetAim;
                                        this.aimStableTicks =
                                                nextAimStableTicks(
                                                        this.lastOffsetAim,
                                                        offsetAim,
                                                        this.aimStableTicks,
                                                        motion.aimStableEps());
                                        this.lastOffsetAim = offsetAim;
                                    }

                                    FiringAlignmentTolerance firingTolerance =
                                            firingAlignmentTolerance(
                                                    dist,
                                                    RadarConfig.server().autoFireMaxLateralErrorBlocks.get(),
                                                    this.activeAlignmentAxisCount()
                                            );
                                    this.lastFiringAlignmentTolerance = firingTolerance;

                                    boolean structuralAimAccepted = true;
                                    boolean structuralPitch = usesStructuralPitchForAuthority();
                                    boolean structuralYaw = this.yawController != null && this.yawController.hasStructuralKineticSelectionForTargeting();
                                    boolean issueAimCommand = shouldIssueAimCommand(aimUpdateMode);

                                    if (issueAimCommand) {
                                        if (structuralPitch) {
                                            if (worldAimDirection == null) {
                                                this.pitchController.failClosedRadarAim();
                                                structuralAimAccepted = false;
                                            } else {
                                                structuralAimAccepted = this.pitchController.setRadarAimDirection(worldAimDirection, firingTolerance.maximumDegrees());
                                            }
                                        } else if (desiredPitch != null && this.pitchController != null) {
                                            this.pitchController.setTargetAngle(desiredPitch.floatValue());
                                        }
                                    }

                                    if (issueAimCommand) {
                                        this.fixedMountDesiredYaw = !this.cannonMount.supportsDirectYawControl() && this.yawController == null ? desiredYaw : null;

                                        if (structuralYaw) {
                                            if (worldAimDirection == null) {
                                                this.yawController.failClosedRadarAim();
                                                structuralAimAccepted = false;
                                            } else {
                                                structuralAimAccepted &= this.yawController.setRadarAimDirection(worldAimDirection, firingTolerance.maximumDegrees());
                                            }
                                        } else if (desiredYaw != null && this.yawController != null) {
                                            this.yawController.setTargetAngle(desiredYaw.floatValue());
                                        }
                                    }

                                    if (MotionTraceRecorder.isEnabled()) {
                                        MotionTraceRecorder.record(
                                                "targeting.command",
                                                serverLevel,
                                                this.cannonMount.getBlockPos(),
                                                "targetId", targetMotionId,
                                                "trackCategory",
                                                this.activetrack == null ? null : this.activetrack.trackCategory(),
                                                "trackEntityType",
                                                this.activetrack == null ? null : this.activetrack.entityType(),
                                                "trackSynthetic",
                                                this.activetrack != null && this.activetrack.isSynthetic(),
                                                "resolvedEntityType",
                                                this.targetEntity == null ? null : this.targetEntity.getType().toString(),
                                                this.targetSublevel == null ? null : this.targetSublevel.getUniqueId(),
                                                "rawTargetPos", rawTargetPos,
                                                "worldTargetPos", solvePos,
                                                "targetVelocity", targetVel,
                                                "targetAcceleration", targetAccel,
                                                "motionClass", motion.motionClass(),
                                                "motionReason", motion.reason(),
                                                "muzzleWorld", cannonMuzzleWorld,
                                                "shooterVelocity", shooterVel,
                                                "sourceSable", this.isSableMount(),
                                                "sourceSublevelId", mountFrame.sourceSublevelId(),
                                                "mountFrame", mountFrame.kind(),
                                                "trackingLeadTicks", trackingLeadTicks,
                                                "trackingLeadHeld",
                                                trackingLeadTicks != this.targetTrackingLeadTicks(motion.motionClass()),
                                                "aimUpdateMode", aimUpdateMode,
                                                "issueAimCommand", issueAimCommand,
                                                "structuralAimAccepted", structuralAimAccepted,
                                                "requestedYaw", desiredYaw,
                                                "requestedPitch", desiredPitch,
                                                "controllerYaw",
                                                this.yawController == null ? null : this.yawController.getTargetAngle(),
                                                "controllerPitch",
                                                this.pitchController == null ? null : this.pitchController.getTargetAngle(),
                                                "physicalYaw",
                                                this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().yaw,
                                                "physicalPitch",
                                                this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().pitch,
                                                "worldAimDirection", worldAimDirection,
                                                "aimPoint", offsetAim,

                                                "useNewSolver", useNewSolver,
                                                "solverHasShot",
                                                targetingResult != null && targetingResult.hasShot(),
                                                "solverFreshForFire", targetingResultFreshForFire,
                                                "solverProvisional", targetingResultProvisional,
                                                "freshnessReason", this.lastTargetingFreshnessReason,
                                                "solverMiss",
                                                targetingResult == null ? null : targetingResult.missDistance(),
                                                "solverFlightTicks",
                                                targetingResult == null ? null : targetingResult.predictedFlightTicks(),
                                                "asyncRequestAgeTicks",
                                                this.cachedTargetingRequest == null ? null : serverLevel.getGameTime() - this.cachedTargetingRequest.requestTick(),
                                                "asyncRequestTargetPos",
                                                this.cachedTargetingRequest == null ? null : this.cachedTargetingRequest.snapshot().targetPosition(),
                                                "asyncRequestTargetVelocity",
                                                this.cachedTargetingRequest == null ? null : this.cachedTargetingRequest.snapshot().targetVelocity(),
                                                "asyncRequestTargetAcceleration",
                                                this.cachedTargetingRequest == null ? null : this.cachedTargetingRequest.snapshot().targetAcceleration(),
                                                "asyncRequestLeadTicks",
                                                this.cachedTargetingRequest == null ? null : this.cachedTargetingRequest.leadTicks(),
                                                "solverPath",
                                                motionTraceSolverField(targetingResult, "path"),
                                                "selectedArc",
                                                motionTraceSolverField(targetingResult, "selectedArc"),
                                                "continuityDecision",
                                                motionTraceSolverField(targetingResult, "continuityDecision"),
                                                "continuityJumpDeg",
                                                motionTraceSolverField(targetingResult, "continuityJumpDeg"),
                                                "continuityPreviousMiss",
                                                motionTraceSolverField(targetingResult, "continuityPreviousMiss"),
                                                "continuitySelectedMiss",
                                                motionTraceSolverField(targetingResult, "continuitySelectedMiss"),
                                                "hasLeadSolution", hasLeadSolution,
                                                "hasNewTargetingSolution", hasNewTargetingSolution
                                        );
                                    }

                                    if (issueAimCommand && desiredPitch != null && desiredYaw != null && Double.isFinite(desiredPitch) && Double.isFinite(desiredYaw)) {
                                        Vec3 commandedDirection = mountFrame.worldDirection(desiredPitch, desiredYaw);
                                        AimCommandEvaluation limitEvaluation = mountFrame.evaluate(commandedDirection);

                                        if (limitEvaluation.valid()) {
                                            boolean pitchLimited = pitchController != null && pitchController.isTargetLimitConstrained();
                                            boolean yawLimited = yawController != null && yawController.isTargetLimitConstrained();

                                            recordAutoAimLimitFeedback(!limitEvaluation.constrained() && !pitchLimited && !yawLimited);
                                        }
                                    }

                                    double minimumFiringTolerance = adapterShot == null ? 0.0 : adapterShot.minimumFiringToleranceDegrees();
                                    boolean auto = this.targetingConfig.autoFire();
                                    boolean yawPitchOk = this.hasCorrectYawPitch(lag, minimumFiringTolerance, firingTolerance.maximumDegrees(), worldAimDirection);
                                    boolean safeOk = !this.passesSafeZone();
                                    boolean cannonReady = adapterShot == null ? CannonUtil.isCannonReadyToFire(this.cannonMount) : adapterShot.canTriggerFire();
                                    boolean stableOk = this.aimStableTicks >= motion.stableTicksRequired();

                                    boolean sharedFireGatesWithoutStability = !binocularSteeringFallback
                                                    && this.targetingConfig.autoFire()
                                                    && hasFireEligibleAim(hasLeadSolution, hasFireEligibleNewTargetingSolution, canFireWithoutLead)
                                                    && directAdapterAimReady
                                                    && aimWithinMovementLimits
                                                    && structuralAimAccepted
                                                    && safeOk;
                                    boolean sharedFireGates = sharedFireGatesWithoutStability && stableOk;

                                    if (MotionTraceRecorder.isEnabled()) {
                                        MotionTraceRecorder.record(
                                                "firing.main_gates",
                                                serverLevel,
                                                this.cannonMount.getBlockPos(),
                                                "targetId", targetMotionId,
                                                "targetPosition", solvePos,
                                                "targetVelocity", targetVel,
                                                "targetAcceleration", targetAccel,
                                                "shooterVelocity", shooterVel,
                                                "sourceSable", this.isSableMount(),
                                                "targetSable", this.targetSublevel != null,
                                                "mountFrame", mountFrame.kind(),
                                                "aimUpdateMode", aimUpdateMode,
                                                "worldAimDirection", worldAimDirection,
                                                "desiredYaw", desiredYaw,
                                                "desiredPitch", desiredPitch,
                                                "controllerYaw",
                                                this.yawController == null ? null : this.yawController.getTargetAngle(),
                                                "controllerPitch",
                                                this.pitchController == null ? null : this.pitchController.getTargetAngle(),
                                                "physicalYaw",
                                                this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().yaw,
                                                "physicalPitch",
                                                this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().pitch,
                                                "solverValid",
                                                targetingResult != null && targetingResult.valid(),
                                                "solverHasShot",
                                                targetingResult != null && targetingResult.hasShot(),
                                                "solverFresh", targetingResultFreshForFire,
                                                "solverProvisional", targetingResultProvisional,
                                                "freshnessReason", lastTargetingFreshnessReason,
                                                "solverConfidence",
                                                targetingResult == null ? null : targetingResult.confidence(),
                                                "solverMissDistance",
                                                targetingResult == null ? null : targetingResult.missDistance(),
                                                "predictedFlightTicks",
                                                targetingResult == null ? null : targetingResult.predictedFlightTicks(),
                                                "hasLeadSolution", hasLeadSolution,
                                                "hasNewTargetingSolution", hasNewTargetingSolution,
                                                "hasFireEligibleNewSolution", hasFireEligibleNewTargetingSolution,
                                                "canFireWithoutLead", canFireWithoutLead,
                                                "autoFire", auto,
                                                "binocularSteeringFallback", binocularSteeringFallback,
                                                "directAdapterAimReady", directAdapterAimReady,
                                                "movementLimitsOk", aimWithinMovementLimits,
                                                "structuralAimAccepted", structuralAimAccepted,
                                                "safeZoneAllowed", safeOk,
                                                "stableTicks", this.aimStableTicks,
                                                "stableTicksRequired", motion.stableTicksRequired(),
                                                "stable", stableOk,
                                                "yawPitchAligned", yawPitchOk,
                                                "cannonReady", cannonReady,
                                                "minimumTolerance", minimumFiringTolerance,
                                                "maximumTolerance", firingTolerance.maximumDegrees(),
                                                "sharedFireGates", sharedFireGates
                                        );
                                    }

                                    //LOGGER.debug("[RADAR-TRACE][AIM-AND-FIRE] " + "tick={} mount={} " + "sourceSable={} targetSable={} " + "aimMode={} " + "solverValid={} solverShot={} solverFresh={} " + "freshnessReason={} " + "solverPitch={} solverYaw={} " + "commandPitch={} commandYaw={} " + "worldDirection={} " + "physicalPitch={} physicalYaw={} " + "controllerPitch={} controllerYaw={} " + "mountFrame={} " + "autoFire={} stableTicks={} stableRequired={} " + "yawPitchOk={} cannonReady={} safe={} " + "limitsOk={} structuralOk={} " + "fireGates={} confidence={} miss={} flightTicks={}",serverLevel.getGameTime(), this.cannonMount.getBlockPos(), this.isSableMount(), this.targetSublevel != null, aimUpdateMode, targetingResult != null && targetingResult.valid(), targetingResult != null && targetingResult.hasShot(), targetingResultFreshForFire, this.lastTargetingFreshnessReason, targetingResult == null ? null : targetingResult.desiredPitchDeg(), targetingResult == null ? null : targetingResult.desiredYawDeg(), desiredPitch, desiredYaw, worldAimDirection, this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().pitch, this.cannonMount.getContraption() == null ? null : this.cannonMount.getContraption().yaw, this.pitchController == null ? null : this.pitchController.getTargetAngle(), this.yawController == null ? null : this.yawController.getTargetAngle(), mountFrame.kind(), auto, this.aimStableTicks, motion.stableTicksRequired(), yawPitchOk, cannonReady, safeOk, aimWithinMovementLimits, structuralAimAccepted, sharedFireGates, targetingResult == null ? null : targetingResult.confidence(), targetingResult == null ? null : targetingResult.missDistance(), targetingResult == null ? null : targetingResult.predictedFlightTicks());

                                    boolean physicalAlignmentFallback = !yawPitchOk
                                            && sharedFireGates
                                            && cannonReady
                                            && isSableMount()
                                            && useNewSolver
                                            && currentProjectile != null
                                            && !directAimRequired
                                            && hasFireEligibleNewTargetingSolution
                                            && targetingResult != null
                                            && this.fireController != null
                                            && (this.yawController == null || !this.yawController.isTargetLimitConstrained())
                                            && (this.pitchController == null || !this.pitchController.isTargetLimitConstrained());

                                    boolean stabilityFallback = !stableOk
                                            && sharedFireGatesWithoutStability
                                            && yawPitchOk
                                            && cannonReady
                                            && useNewSolver
                                            && currentProjectile != null
                                            && !directAimRequired
                                            && hasFireEligibleNewTargetingSolution
                                            && targetingResult != null
                                            && targetingResultFreshForFire
                                            && (this.yawController == null || !this.yawController.isTargetLimitConstrained())
                                            && (this.pitchController == null || !this.pitchController.isTargetLimitConstrained());

                                    boolean shouldFire = cannonReady && ((sharedFireGates && yawPitchOk) || physicalAlignmentFallback || stabilityFallback);

                                    if (shouldFire
                                            && CannonUtil.isBigCannon(cannon)
                                            && !this.isSableMount()
                                            && mountFrame.kind() == MountFrameKind.WORLD
                                            && this.yawController != null
                                            && !this.yawController.hasStructuralKineticSelectionForTargeting()
                                            && !usesStructuralPitchForAuthority()
                                            && desiredYaw != null
                                            && desiredPitch != null) {

                                        PitchOrientedContraptionEntity physicalMount = this.cannonMount.getContraption();
                                        double allowedError = Math.min(0.15, firingTolerance.maximumDegrees());

                                        double yawError = physicalMount == null ? Double.POSITIVE_INFINITY : Math.abs(shortestDelta(wrap360(physicalMount.yaw), wrap360(desiredYaw)));

                                        Double physicalPitch = this.currentPitchDeg(cannon);
                                        double pitchError = physicalPitch == null ? Double.POSITIVE_INFINITY : Math.abs(physicalPitch - desiredPitch);
                                        boolean physicallyAligned = yawError <= allowedError && pitchError <= allowedError;

                                        if (MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record("firing.cbc_physical_alignment", serverLevel, this.cannonMount.getBlockPos(), "targetId", targetMotionId, "desiredYaw", desiredYaw, "desiredPitch", desiredPitch, "physicalYaw", physicalMount == null ? null : physicalMount.yaw, "physicalPitch", physicalPitch, "yawError", yawError, "pitchError", pitchError, "allowedError", allowedError, "physicallyAligned", physicallyAligned);
                                        }

                                        //LOGGER.debug("[RADAR-TRACE][CBC-PHYSICAL] " + "tick={} mount={} " + "yawError={} pitchError={} " + "allowed={} aligned={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), yawError, pitchError, allowedError, physicallyAligned);

                                        shouldFire &= physicallyAligned;
                                    }

                                    if (shouldFire && useNewSolver && currentProjectile != null && !directAimRequired && hasFireEligibleNewTargetingSolution && targetingResult != null) {
                                        ImmediateLaunchCheck immediateCheck = checkImmediateLaunchForFire(
                                                serverLevel,
                                                cannon,
                                                currentProjectile,
                                                rawTargetPos,
                                                solvePos,
                                                guidance.positionOffset(),
                                                targetVel,
                                                targetAccel,
                                                shooterVel,
                                                this.targetEntity,
                                                this.targetSublevel,
                                                motion.motionClass(),
                                                targetingResult
                                        );

                                        if (MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record(
                                                    "firing.immediate_launch_validation",
                                                    serverLevel,
                                                    getWeaponMountPos(),
                                                    "targetId", targetMotionId,
                                                    "currentTick", serverLevel.getGameTime(),
                                                    "futureLaunchTick",
                                                    cachedTargetingRequest == null ? null : cachedTargetingRequest.snapshot().gameTime(),
                                                    "futureFlightTicks",
                                                    targetingResult.predictedFlightTicks(),
                                                    "currentTrajectoryHit",
                                                    immediateCheck.allowed(),
                                                    "predictedLaunchReachable",
                                                    cachedBallisticReachableForFire,
                                                    "validatedBallisticSolution",
                                                    cachedBallisticSolutionAccepted,
                                                    "currentImpactTick",
                                                    immediateCheck.impactTick(),
                                                    "reason",
                                                    immediateCheck.reason(),
                                                    "sourceSable", isSableMount(),
                                                    "targetSable", this.targetSublevel != null,
                                                    "projectileFamily", currentProjectile.family());
                                        }

                                        if (physicalAlignmentFallback && MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record("firing.physical_alignment_fallback", serverLevel, getWeaponMountPos(), "targetId", targetMotionId, "controllerAligned", yawPitchOk, "trajectoryHit", immediateCheck.allowed(), "trajectoryReason", immediateCheck.reason(), "impactTick", immediateCheck.impactTick(), "solutionFresh", targetingResultFreshForFire, "stable", stableOk, "cannonReady", cannonReady, "sourceSable", isSableMount());
                                        }

                                        if (stabilityFallback && MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record("firing.stability_fallback", serverLevel, getWeaponMountPos(), "targetId", targetMotionId, "stableTicks", this.aimStableTicks, "requiredStableTicks", motion.stableTicksRequired(), "controllerAligned", yawPitchOk, "solutionFresh", targetingResultFreshForFire, "trajectoryHit", immediateCheck.allowed(), "trajectoryReason", immediateCheck.reason(), "impactTick", immediateCheck.impactTick(), "sourceSable", isSableMount(), "cannonReady", cannonReady);
                                        }

                                        shouldFire &= immediateCheck.allowed();
                                    }

                                    if (shouldFire && adapterShot != null && !this.prepareAdapterShotForFire(adapterShot, adapterContext)) {
                                        if (MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record("firing.rejected", serverLevel, this.cannonMount.getBlockPos(), "targetId", targetMotionId, "reason", "ADAPTER_FIRE_PREPARATION_FAILED", "adapterFingerprint", adapterShot.fingerprint());
                                        }

                                        shouldFire = false;
                                    }

                                    if (shouldFire && CannonUtil.isCBCMWLauncher(cannon) && !CBCMWGuidanceCompat.prepareLaunch(this.cannonMount, serverLevel, this.targetEntity)) {
                                        if (MotionTraceRecorder.isEnabled()) {
                                            MotionTraceRecorder.record("firing.rejected", serverLevel, this.cannonMount.getBlockPos(), "targetId", targetMotionId, "reason", "CBCMW_LAUNCH_PREPARATION_FAILED");
                                        }

                                        shouldFire = false;
                                    }

                                    if (MotionTraceRecorder.isEnabled()) {
                                        MotionTraceRecorder.record(
                                                "firing.single_final",
                                                serverLevel,
                                                this.cannonMount.getBlockPos(),
                                                "targetId", targetMotionId,
                                                "shouldFire", shouldFire,
                                                "willIssueFireRequest",
                                                shouldFire && this.fireController != null,
                                                "hasFireController", this.fireController != null,
                                                "fireControllerPos",
                                                this.fireController == null ? null : this.fireController.getBlockPos(),
                                                "sharedFireGates", sharedFireGates,
                                                "sharedFireGatesWithoutStability", sharedFireGatesWithoutStability,
                                                "stabilityFallback", stabilityFallback,
                                                "physicalAlignmentFallback", physicalAlignmentFallback,
                                                "yawPitchAligned", yawPitchOk,
                                                "cannonReady", cannonReady,
                                                "safeZoneAllowed", safeOk,
                                                "stable", stableOk,
                                                "desiredYaw", desiredYaw,
                                                "desiredPitch", desiredPitch,
                                                "sourceSable", this.isSableMount()
                                        );
                                    }

                                    if (shouldFire && this.fireController != null && MotionTraceRecorder.isEnabled()) {
                                        AsyncTargetingRequest request = this.cachedTargetingRequest;
                                        boolean matchingRequest = request != null
                                                && hasNewTargetingSolution
                                                && targetingResultFreshForFire
                                                && currentProjectile != null
                                                && currentProjectile.solverKind() != SolverKind.CBCMS_SERVER
                                                && Objects.equals(request.targetId(), targetMotionId)
                                                && Objects.equals(
                                                request.shotFingerprint(),
                                                currentProjectile.fingerprint());

                                        long nowTick = serverLevel.getGameTime();
                                        Long predictedLaunchTick = matchingRequest ? request.snapshot().gameTime() : null;

                                        MotionTraceRecorder.record(
                                                "targeting.launch_timing",
                                                serverLevel,
                                                this.cannonMount.getBlockPos(),
                                                "targetId", targetMotionId,
                                                "fireCommandTick", nowTick,
                                                "solverRequestMatched", matchingRequest,
                                                "solverRequestTick",
                                                matchingRequest ? request.requestTick() : null,
                                                "requestAgeTicks",
                                                matchingRequest ? nowTick - request.requestTick() : null,
                                                "predictedLaunchDelayTicks",
                                                matchingRequest ? request.leadTicks() : null,
                                                "predictedLaunchTick", predictedLaunchTick,
                                                "fireCommandMinusPredictedTicks",
                                                predictedLaunchTick == null ? null : nowTick - predictedLaunchTick,
                                                "trackingLeadTicksNow", trackingLeadTicks,
                                                "predictedFlightTicks",
                                                targetingResult == null ? null : targetingResult.predictedFlightTicks(),
                                                "predictedTargetAtLaunch",
                                                matchingRequest ? request.snapshot().targetPosition() : null,
                                                "targetAtFireCommand", solvePos,
                                                "targetVelocityAtFireCommand", targetVel,
                                                "targetAccelerationAtFireCommand", targetAccel,
                                                "aimPoint", offsetAim,
                                                "solverFreshForFire", targetingResultFreshForFire,
                                                "sourceSable", this.isSableMount(),
                                                "fireControllerPos", this.fireController.getBlockPos()
                                        );
                                    }

                                    if (this.fireController != null) {
                                        if (shouldFire) {
                                            //LOGGER.debug("[RADAR-TRACE][FIRE-COMMAND] " + "tick={} mount={} " + "target={} pitch={} yaw={} " + "actualMountPitch={} actualMountYaw={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), solvePos, desiredPitch, desiredYaw, this.cannonMount.getContraption().pitch, this.cannonMount.getContraption().yaw);
                                            this.tryFireCannon();
                                        } else {
                                            this.stopFireCannon();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearAutoAimLimitFeedback() {
        lastAutoAimLimitTargetId = null;
        lastAutoAimLimitTick = Long.MIN_VALUE;
    }

    private void recordAutoAimLimitFeedback(boolean reachable) {
        if (level == null || binoMode || activetrack == null) {
            return;
        }

        lastAutoAimLimitTargetId = activetrack.getId();
        lastAutoAimLimitTick = level.getGameTime();
        lastAutoAimLimitReachable = reachable;
    }


    @Nullable
    public Boolean getAutoAimLimitReachability(@Nullable String selectedTargetId, long gameTime) {
        if (selectedTargetId == null
                || activetrack == null
                || !selectedTargetId.equals(activetrack.getId())
                || !selectedTargetId.equals(lastAutoAimLimitTargetId)
                || lastAutoAimLimitTick == Long.MIN_VALUE
                || gameTime < lastAutoAimLimitTick
                || gameTime - lastAutoAimLimitTick > 20L) {
            return null;
        }

        return lastAutoAimLimitReachable;
    }

    public void resetTarget() {
        this.activetrack = null;
        this.lastJammingSampleToken = Long.MIN_VALUE;
        this.binoMode = false;
        this.binoTargetPos = null;
        this.resetAimForRetry();
    }

    /** Stops output and discards transient state without losing the designation. */
    public void resetAimForRetry() {
        clearAutoAimLimitFeedback();
        this.visCache.clear();
        this.clearSublevelAimCache();
        this.target = null;
        this.targetEntity = null;
        this.targetSublevel = null;
        this.targetShipId = null;
        this.lastAimPoint = null;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.lastFiringAlignmentTolerance = null;
        this.cachedSableAngles = null;
        this.cachedSableAimTarget = null;
        this.cachedSableSolveTick = -1L;
        this.cachedSablePitchDeg = null;
        this.cachedSableYawDeg = null;
        this.cachedSableWorldAimDirection = null;
        this.clearTargetingResultCache();
        this.stopFireCannon();
        this.endStructuralRadarTracking();
    }

    public void setTarget(Vec3 target, TargetingConfig config, RadarTrack track, WeaponNetworkRuntime.WeaponGroupView view) {
        TargetingConfig nextConfig = config == null ? TargetingConfig.DEFAULT : config;
        boolean sameTarget = target != null && sameTargetIdentity(this.activetrack, track);
        long nextJammingSampleToken = jammingSampleToken(track);
        boolean jammingSampleChanged = jammingSampleChanged(sameTarget, this.lastJammingSampleToken, track);
        boolean ballisticConfigChanged = !sameBallisticConfiguration(this.targetingConfig, nextConfig);

        if (!sameTarget || ballisticConfigChanged || jammingSampleChanged) {
            clearAutoAimLimitFeedback();
        }

        //LOGGER.debug("setTarget() -> target={} sameIdentity={} ballisticConfigChanged={} atTick={}", target, sameTarget, ballisticConfigChanged, this.level != null ? this.level.getGameTime() : -1L);
        if (target == null) {
            this.clearSublevelAimCache();
            this.target = null;
            this.activetrack = null;
            this.lastJammingSampleToken = Long.MIN_VALUE;
            this.targetEntity = null;
            this.targetSublevel = null;
            this.targetShipId = null;
            this.lastAimPoint = null;
            this.lastOffsetAim = null;
            this.aimStableTicks = 0;
            this.clearTargetingResultCache();
            this.stopFireCannon();
            this.endStructuralRadarTracking();
        } else {
            this.binoMode = false;
            this.target = target;
            this.targetingConfig = nextConfig;
            this.view = view;
            this.activetrack = track;
            this.lastJammingSampleToken = nextJammingSampleToken;
            if (this.level != null) {
                this.lastTargetTick = this.level.getGameTime();
            }

            if (sameTarget) {
                if (ballisticConfigChanged || jammingSampleChanged) {
                    this.lastAimPoint = null;
                    this.lastOffsetAim = null;
                    this.aimStableTicks = 0;
                    this.cachedSableAngles = null;
                    this.cachedSableAimTarget = null;
                    this.cachedSableSolveTick = -1L;
                    this.cachedSablePitchDeg = null;
                    this.cachedSableYawDeg = null;
                    this.cachedSableWorldAimDirection = null;
                    this.clearTargetingResultCache();
                }
                return;
            }

            this.targetEntity = null;
            this.targetSublevel = null;
            this.targetShipId = null;
            this.clearSublevelAimCache();
            this.lastAimPoint = null;
            this.lastOffsetAim = null;
            this.aimStableTicks = 0;
            this.cachedSableAngles = null;
            this.cachedSableAimTarget = null;
            this.cachedSableSolveTick = -1L;
            this.cachedSablePitchDeg = null;
            this.cachedSableYawDeg = null;
            this.cachedSableWorldAimDirection = null;
            this.clearTargetingResultCache();
        }
    }

    static boolean sameTargetIdentity(@Nullable RadarTrack current,
                                      @Nullable RadarTrack next) {
        return current != null && next != null
                && Objects.equals(current.getId(), next.getId())
                && current.getTrackCategory() == next.getTrackCategory();
    }

    static long jammingSampleToken(@Nullable RadarTrack track) {
        RadarTrack.JammingData jamming = track == null
                ? null : track.getJammingData();
        return jamming == null ? Long.MIN_VALUE : jamming.sampleToken();
    }

    static boolean jammingSampleChanged(boolean sameTarget,
                                        long previousToken,
                                        @Nullable RadarTrack next) {
        return sameTarget && previousToken != jammingSampleToken(next);
    }

    static JammedGuidance applyJammingGuidance(
            Vec3 position, Vec3 velocity, @Nullable RadarTrack track) {
        RadarTrack.JammingData jamming = track == null
                ? null : track.getJammingData();
        Vec3 safePosition = position == null ? Vec3.ZERO : position;
        Vec3 safeVelocity = velocity == null ? Vec3.ZERO : velocity;
        if (jamming == null) {
            return new JammedGuidance(
                    safePosition, safeVelocity, Vec3.ZERO);
        }
        Vec3 positionOffset = jamming.guidancePositionOffset();
        return new JammedGuidance(
                safePosition.add(positionOffset),
                safeVelocity.add(jamming.guidanceVelocityOffset()),
                positionOffset);
    }

    static record JammedGuidance(Vec3 position, Vec3 velocity,
                                 Vec3 positionOffset) {
    }

    private static boolean sameBallisticConfiguration(
            @Nullable TargetingConfig current,
            @Nullable TargetingConfig next) {
        return current != null && next != null
                && current.preferHighArc() == next.preferHighArc();
    }

    public void setBinoTarget(@Nullable BlockPos binoTarget, TargetingConfig config, WeaponNetworkRuntime.WeaponGroupView view, boolean reset) {
        this.view = view;
        this.targetingConfig = config == null ? TargetingConfig.DEFAULT : config;
        this.activetrack = null;
        this.lastJammingSampleToken = Long.MIN_VALUE;
        this.clearSublevelAimCache();
        if (!reset && binoTarget != null) {
            this.binoMode = true;
            this.binoTargetPos = binoTarget.immutable();
            this.resetAimForRetry();
            if (this.level != null) {
                this.lastTargetTick = this.level.getGameTime();
            }

        } else {
            this.binoMode = false;
            this.binoTargetPos = null;
            this.targetSublevel = null;
            this.targetShipId = null;
            this.target = null;
            this.clearTargetingResultCache();
            this.stopFireCannon();
            this.endStructuralRadarTracking();
        }
    }

    private boolean shouldReuseTargetingResult(
            ServerLevel serverLevel, @Nullable UUID targetMotionId,
            AbstractMountedCannonContraption cannonContraption,
            Vec3 solvePos, String shotFingerprint) {
        if (this.cachedTargetingResult == null || !this.cachedTargetingResult.valid() || !this.cachedTargetingResult.hasShot()) {
            return false;
        }

        long age = serverLevel.getGameTime()
                - this.cachedTargetingRequestTick;
        if (age < 0L || age > ASYNC_TARGETING_TASK_TIMEOUT_TICKS) {
            return false;
        }

        if (shotFingerprint == null || !shotFingerprint.equals(this.cachedTargetingShotFingerprint)) {
            return false;
        }

        if (targetMotionId != null || this.cachedTargetingTargetId != null) {
            if (targetMotionId == null || !targetMotionId.equals(this.cachedTargetingTargetId)) {
                return false;
            }
        }

        if (!this.cachedBallisticReachableForFire) {
            return age <= 30L && finite(solvePos) && finite(this.cachedTargetingSolvePos);
        }

        Vec3 currentOrigin = this.cachedTargetingRequest != null
                ? this.predictedAsyncSteeringOrigin(serverLevel, cannonContraption, this.cachedTargetingRequest.leadTicks())
                : this.resolveSteeringOrigin(cannonContraption);
        Vec3 requestDirection = directionFromTo(this.cachedTargetingOriginPos, this.cachedTargetingSolvePos);
        Vec3 currentDirection = directionFromTo(currentOrigin, solvePos);
        return requestDirection != null && currentDirection != null
                && requestDirection.dot(currentDirection)
                >= ASYNC_STEERING_MAX_DIVERGENCE_COS;
    }

    static boolean shouldKeepPendingSolve(
            long ageTicks, boolean sameTarget, boolean sameShot) {
        return ageTicks >= 0L
                && ageTicks <= ASYNC_TARGETING_TASK_TIMEOUT_TICKS
                && sameTarget
                && sameShot;
    }

    static double asyncSableOriginPredictionErrorSquared(
            @Nullable RigidBodyLaunchMath.Motion requestMotion,
            Vec3 observedOriginAtRequest,
            long elapsedTicks, int leadTicks,
            Vec3 currentPredictedOrigin
    ) {
        if (requestMotion == null || !finite(observedOriginAtRequest) || !finite(currentPredictedOrigin) || elapsedTicks < 0L) {
            return Double.POSITIVE_INFINITY;
        }

        double predictionTicks = elapsedTicks + Math.max(0, leadTicks);
        Vec3 expectedOrigin = requestMotion.futurePosition(observedOriginAtRequest, predictionTicks);

        if (!finite(expectedOrigin)) {
            return Double.POSITIVE_INFINITY;
        }

        return expectedOrigin.distanceToSqr(currentPredictedOrigin);
    }

    static double asyncSableLaunchVelocityErrorSquared(
            @Nullable RigidBodyLaunchMath.Motion requestMotion,
            @Nullable RigidBodyLaunchMath.Motion liveMotion,
            Vec3 requestedPivot,
            Vec3 livePivot,
            Vec3 aimDirection,
            long elapsedTicks,
            int leadTicks,
            double muzzleForwardOffset
    ) {
        if (requestMotion == null || liveMotion == null || !finite(requestedPivot) || !finite(livePivot) || !finite(aimDirection) || aimDirection.lengthSqr() < 1.0e-12 || elapsedTicks < 0L) {
            return Double.POSITIVE_INFINITY;
        }

        double requestTime = elapsedTicks + Math.max(0, leadTicks);
        double liveTime = Math.max(0, leadTicks);

        Vec3 expectedVelocity = requestMotion.futureVelocity(requestedPivot, requestTime);
        Vec3 actualVelocity = liveMotion.futureVelocity(livePivot, liveTime);

        if (Double.isFinite(muzzleForwardOffset) && muzzleForwardOffset > 0.0) {
            Vec3 barrelOffset = aimDirection.normalize().scale(muzzleForwardOffset);

            expectedVelocity = expectedVelocity.add(requestMotion.angularVelocity().cross(barrelOffset));
            actualVelocity = actualVelocity.add(liveMotion.angularVelocity().cross(barrelOffset));
        }

        if (!finite(expectedVelocity) || !finite(actualVelocity)) {
            return Double.POSITIVE_INFINITY;
        }

        return expectedVelocity.distanceToSqr(actualVelocity);
    }

    static boolean asyncSableVelocityWithinTolerance(double velocityErrorSquared, int flightTicks) {
        if (!Double.isFinite(velocityErrorSquared) || velocityErrorSquared < 0.0 || flightTicks <= 0) {
            return false;
        }

        double allowedVelocityError = 2.0 / flightTicks;
        return velocityErrorSquared <= allowedVelocityError * allowedVelocityError;
    }

    static boolean isAsyncSolutionFreshForFire(
            long ageTicks, double targetMovementSquared,
            double originMovementSquared) {
        return ageTicks >= 0L
                && ageTicks <= ASYNC_TARGETING_FIRE_FRESH_TICKS
                && targetMovementSquared <= 4.0
                && originMovementSquared <= 1.0;
    }

    private boolean shouldSolveEveryTick(Vec3 targetVel, TargetMotionEstimate motion, double distanceToTarget) {
        if (motion.motionClass() == TargetMotionClass.ERRATIC || distanceToTarget <= CLOSE_TARGET_SOLVE_EVERY_TICK_BLOCKS) {
            return true;
        }

        double fastThreshold = (Double)RadarConfig.server().targetLoosenThreshold.get();
        double extremeSpeedMps = Math.max((double)6.0F, fastThreshold * EXTREME_TARGET_SPEED_MULTIPLIER);
        return targetVel.length() * (double)20.0F >= extremeSpeedMps;
    }

    private void storeTargetingResult(
            ServerLevel serverLevel, @Nullable UUID targetMotionId,
            Vec3 solvePos, String shotFingerprint,
            @Nullable TargetingResult targetingResult) {
        this.storeTargetingResult(
                serverLevel, targetMotionId, solvePos,
                shotFingerprint, targetingResult,
                serverLevel.getGameTime(), null);
    }

    private void storeTargetingResult(
            ServerLevel serverLevel, @Nullable UUID targetMotionId,
            Vec3 solvePos, String shotFingerprint,
            @Nullable TargetingResult targetingResult,
            long requestTick, @Nullable Vec3 originPos) {
        if (targetingResult != null && targetingResult.valid() && targetingResult.hasShot()) {
            this.cachedTargetingResult = targetingResult;
            this.cachedTargetingTargetId = targetMotionId;
            this.cachedTargetingSolvePos = solvePos;
            this.cachedTargetingRequestTick = requestTick;
            this.cachedTargetingShotFingerprint = shotFingerprint;
            this.cachedTargetingOriginPos = originPos;
            this.cachedTargetingRequest = null;
        } else {
            this.clearTargetingResultCache();
        }
    }

    private void clearTargetingResultCache() {
        this.pendingBallisticRefinementFloorTicks = -1;
        this.cachedBallisticReachableForFire = true;
        this.lastSyncBallisticReachableForFire = true;
        this.cachedBallisticSolutionAccepted = false;
        this.cachedTargetingResult = null;
        this.cachedTargetingTargetId = null;
        this.cachedTargetingSolvePos = null;
        this.cachedTargetingRequestTick = Long.MIN_VALUE;
        this.cachedTargetingShotFingerprint = null;
        this.cachedTargetingOriginPos = null;
        this.cachedTargetingRequest = null;
        this.lastTargetingSteeringProvisional = false;
        this.lastTargetingFireFresh = false;
        this.lastTargetingFreshnessReason = "no_solution";
        if (this.pendingTargetingFuture != null) {
            ++this.asyncSolveSuperseded;
            this.pendingTargetingFuture.cancel(true);
        }
        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;
    }

    @Nullable
    private TargetingResult pollCompletedAsyncTargetingResult(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            @Nullable UUID targetMotionId, Vec3 solvePos) {
        CompletableFuture<AsyncTargetingResult> future = this.pendingTargetingFuture;
        if (future == null || !future.isDone()) {
            return null;
        }

        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;
        ++this.asyncSolveCompleted;

        try {
            AsyncTargetingResult asyncResult = future.getNow(null);
            if (asyncResult != null) {
                this.lastAsyncMaxFlightTicks = asyncResult.request().snapshot().maxFlightTicks();
            }
            this.lastAsyncDiagnosticTick = serverLevel.getGameTime();
            this.lastAsyncValidationReason = "not_checked";

            if (asyncResult == null) {
                this.lastAsyncRawResult = "empty_async_result";
            } else if (asyncResult.alternatives() != null) {
                this.lastAsyncRawResult = "obstruction_alternatives=" + asyncResult.alternatives().size();
            } else if (asyncResult.result() == null) {
                this.lastAsyncRawResult = "null_solver_result";
            } else {
                this.lastAsyncRawResult =
                        "shot=" + asyncResult.result().hasShot() + " reason=" + asyncResult.result().reason() + " | " + String.join(" ",
                                asyncResult.result().debugInfo().stream().filter(value -> value.startsWith("miss=")
                                        || value.startsWith("confidence=")
                                        || value.startsWith("score=")
                                        || value.startsWith("bestTime=")
                                        || value.startsWith("selectedArc=")
                                        || value.startsWith("arcFallbackReason=")
                                        || value.startsWith("pitchConstraintRejections=")
                                        || value.startsWith("horizonExpansions=")).toList());
            }
            //LOGGER.debug("[RADAR-TRACE][ASYNC-RECEIVED] " + "tick={} mount={} targetId={} " + "requestTick={} age={} " + "requestTarget={} currentTarget={} " + "requestMuzzle={} " + "result={} alternatives={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), targetMotionId, asyncResult == null ? null : asyncResult.request().requestTick(), asyncResult == null ? null : serverLevel.getGameTime() - asyncResult.request().requestTick(), asyncResult == null ? null : asyncResult.request().solvePos(), solvePos, asyncResult == null ? null : asyncResult.request().snapshot().muzzlePosition(), asyncResult == null || asyncResult.result() == null ? "<none>" : asyncResult.result().debugString(), asyncResult == null || asyncResult.alternatives() == null ? 0 : asyncResult.alternatives().size());
            if (asyncResult == null
                    || !this.isAsyncResultApplicableForSteering(
                    serverLevel, cannonContraption,
                    asyncResult.request(), targetMotionId, solvePos)) {

                this.lastAsyncValidationReason = asyncResult == null ? "empty_async_result" : "request_not_applicable";
                ++this.asyncSolveRejected;
                if (MotionTraceRecorder.isEnabled()) {
                    MotionTraceRecorder.record(
                            "targeting.async_rejected",
                            serverLevel,
                            getWeaponMountPos(),
                            "stage", "applicability",
                            "reason", this.lastAsyncValidationReason,
                            "rawSolverResult", this.lastAsyncRawResult,
                            "requestAgeTicks",
                            asyncResult == null ? null : serverLevel.getGameTime() - asyncResult.request().requestTick(),
                            "requestTarget",
                            asyncResult == null ? null : asyncResult.request().solvePos(),
                            "currentTarget", solvePos
                    );
                }

                return null;
            }

            TargetingResult validated = null;
            if (asyncResult.alternatives() == null) {
                TargetingValidation primary = this.validateAsyncTargetingResult(serverLevel, cannonContraption, asyncResult.request(), asyncResult.result());

                if (primary.obstructed() && !(asyncResult.request().projectileModel() instanceof CBCMWCannonCompat.LauncherProjectileModel)) {
                    this.lastAsyncValidationReason = "blocked_path_fallback_requested";
                    this.cachedBallisticReachableForFire = false;
                    this.cachedBallisticSolutionAccepted = false;
                    this.submitAsyncObstructionFallback(asyncResult.request());
                    return null;
                }

                validated = primary.result();
            } else {
                for (TargetingResult candidate : asyncResult.alternatives()) {
                    TargetingValidation assessment = this.validateAsyncTargetingResult(serverLevel, cannonContraption, asyncResult.request(), candidate);

                    if (assessment.result() != null) {
                        validated = assessment.result();
                        break;
                    }
                }
            }

            if (validated == null) {
                if (asyncResult.alternatives() != null && asyncResult.alternatives().isEmpty()) {
                    this.lastAsyncValidationReason = "no_fallback_candidates";
                }
                ++this.asyncSolveRejected;

                if (MotionTraceRecorder.isEnabled()) {
                    MotionTraceRecorder.record(
                            "targeting.async_rejected",
                            serverLevel,
                            getWeaponMountPos(),
                            "stage", "validation",
                            "reason", this.lastAsyncValidationReason,
                            "rawSolverResult", this.lastAsyncRawResult,
                            "requestAgeTicks",
                            serverLevel.getGameTime() - asyncResult.request().requestTick(),
                            "requestTarget", asyncResult.request().solvePos(),
                            "currentTarget", solvePos,
                            "alternatives",
                            asyncResult.alternatives() == null ? null : asyncResult.alternatives().size()
                    );
                }
                this.cachedBallisticReachableForFire = false;
                this.cachedBallisticSolutionAccepted = false;
                return null;
            }

            AsyncTargetingRequest solvedRequest = asyncResult.request();
            int actualSlewTicks = estimateSlewTicksForSolution(cannonContraption, validated.aimSolution(), validated.desiredYawDeg() + 270.0, validated.desiredPitchDeg());
            int availableSlewTicks = Math.max(0, solvedRequest.leadTicks() - solvedRequest.trackingLeadTicks());
            boolean ballisticReachable = actualSlewTicks <= availableSlewTicks;

            int correctedDelay = LaunchTimeFeasibility.refinedDelay(solvedRequest.leadTicks(), solvedRequest.trackingLeadTicks(), actualSlewTicks, 60);
            boolean externallyTriggered = isExternallyTriggeredYawMount(serverLevel);
            boolean requestCorrection = !externallyTriggered && !ballisticReachable && !solvedRequest.ballisticRefined() && correctedDelay > solvedRequest.leadTicks();
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "targeting.ballistic_refinement_decision",
                        serverLevel,
                        getWeaponMountPos(),
                        "targetId", targetMotionId,
                        "requestTick", solvedRequest.requestTick(),
                        "requestAgeTicks",
                        serverLevel.getGameTime() - solvedRequest.requestTick(),
                        "plannedDelayTicks", solvedRequest.leadTicks(),
                        "trackingLeadTicks", solvedRequest.trackingLeadTicks(),
                        "actualSlewTicks", actualSlewTicks,
                        "availableSlewTicks", availableSlewTicks,
                        "reachable", ballisticReachable,
                        "externallyTriggered", externallyTriggered,
                        "localLaunchScheduleApplicable", !externallyTriggered,
                        "correctedDelayTicks", correctedDelay,
                        "alreadyRefined", solvedRequest.ballisticRefined(),
                        "requestCorrection", requestCorrection,
                        "actualFlightTicks", validated.predictedFlightTicks()
                );
            }

            if (requestCorrection) {
                this.pendingBallisticRefinementFloorTicks = correctedDelay;
                this.lastAsyncValidationReason = "ballistic_refinement_requested";
            }

            this.storeTargetingResult(
                    serverLevel, targetMotionId,
                    asyncResult.request().solvePos(),
                    asyncResult.request().shotFingerprint(),
                    validated, asyncResult.request().requestTick(),
                    asyncResult.request().snapshot().steeringOrigin());

            this.cachedTargetingRequest = asyncResult.request();
            this.cachedBallisticReachableForFire = ballisticReachable;
            this.cachedBallisticSolutionAccepted = true;

            TargetingResult rebased = this.rebaseTargetingResult(
                    cannonContraption,
                    validated,
                    asyncResult.request().snapshot().steeringOrigin(),
                    asyncResult.request().solvePos(),
                    solvePos);

            //LOGGER.debug("[RADAR-TRACE][ASYNC-REBASED] " + "tick={} mount={} age={} " + "originalTarget={} currentTarget={} " + "originalResult={} rebasedResult={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), serverLevel.getGameTime() - asyncResult.request().requestTick(), asyncResult.request().solvePos(), solvePos, validated.debugString(), rebased == null ? "<null>" : rebased.debugString());

            return rebased;
        } catch (Throwable throwable) {
            this.lastAsyncDiagnosticTick = serverLevel.getGameTime();
            this.lastAsyncRawResult = "async_exception";
            this.lastAsyncValidationReason = "async_exception:" + throwable.getClass().getSimpleName();
            ConflictTraceRecorder.mark("targeting", "async_result",
                    "FAILED", this.level, this.cannonMount.getBlockPos(), 0L,
                    Map.of("exception", throwable.getClass().getSimpleName()));
            DiagnosticRecorder.warn("targeting", "async_result",
                    "async_targeting_result_failed", throwable, this.level,
                    this.cannonMount.getBlockPos(), "createbigcannons");
            LOGGER.debug("WFC TargetingComputer async solve failed", throwable);
            return null;
        }
    }

    private boolean isAsyncResultApplicableForSteering(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            AsyncTargetingRequest request,
            @Nullable UUID targetMotionId,
            Vec3 solvePos) {
        long age = serverLevel.getGameTime() - request.requestTick();
        if (age < 0L || age > ASYNC_TARGETING_TASK_TIMEOUT_TICKS) {
            return false;
        }

        if (!this.cannonMount.getBlockPos().equals(request.mountPos())) {
            return false;
        }

        if (targetMotionId != null || request.targetId() != null) {
            if (targetMotionId == null || !targetMotionId.equals(request.targetId())) {
                return false;
            }
        }

        Vec3 requestDirection = directionFromTo(request.snapshot().steeringOrigin(), request.solvePos());
        Vec3 currentDirection = directionFromTo(this.predictedAsyncSteeringOrigin(serverLevel, cannonContraption, request.leadTicks()), solvePos);
        return requestDirection != null && currentDirection != null && requestDirection.dot(currentDirection) >= ASYNC_STEERING_MAX_DIVERGENCE_COS;
    }

    @Nullable
    private TargetingResult rebaseCachedTargetingResult(AbstractMountedCannonContraption cannonContraption, Vec3 liveTarget) {
        return this.rebaseTargetingResult(
                cannonContraption, this.cachedTargetingResult,
                this.cachedTargetingOriginPos,
                this.cachedTargetingSolvePos, liveTarget
        );
    }

    @Nullable
    private TargetingResult rebaseTargetingResult(
            AbstractMountedCannonContraption cannonContraption,
            @Nullable TargetingResult result,
            @Nullable Vec3 requestedOrigin,
            @Nullable Vec3 requestedTarget,
            @Nullable Vec3 liveTarget) {
        if (result == null || result.aimSolution() == null || result.aimSolution().aimPoint() == null) {
            return null;
        }
        Vec3 translatedAimPoint = rebaseAimPoint(result.aimSolution().aimPoint(), requestedTarget, liveTarget);
        AsyncTargetingRequest cachedRequest = this.cachedTargetingRequest;

        if (cachedRequest != null
                && this.level instanceof ServerLevel serverLevel
                && this.targetSublevel != null
                && finite(liveTarget)
                && Objects.equals(
                cachedRequest.snapshot().targetSublevelId(),
                this.targetSublevel.getUniqueId())) {

            RigidBodyTargetMotion originalMotion = cachedRequest.snapshot().rigidBodyTargetMotion();

            if (originalMotion != null) {
                RigidBodyLaunchMath.Motion liveShipMotion = SableLaunchMotion.capture(serverLevel, this.targetSublevel);

                if (liveShipMotion != null) {
                    RigidBodyTargetMotion liveMotion = new RigidBodyTargetMotion(liveShipMotion, liveTarget, 0);

                    translatedAimPoint = rebaseRigidAimPoint(
                            result.aimSolution().aimPoint(),
                            originalMotion,
                            liveMotion,
                            result.predictedFlightTicks(),
                            cachedRequest.leadTicks()
                    );
                }
            }
        }

        Vec3 currentOrigin = this.resolveSteeringOrigin(cannonContraption);

        if (this.cachedTargetingRequest != null && this.level instanceof ServerLevel serverLevel) {
            currentOrigin = this.predictedAsyncSteeringOrigin(serverLevel, cannonContraption, this.cachedTargetingRequest.leadTicks());
        }

        Vec3 direction = rebaseAimDirection(
                result.aimSolution().aimDirection(),
                result.aimSolution().aimPoint(),
                requestedOrigin, currentOrigin,
                translatedAimPoint);
        if (direction == null) {
            return null;
        }

        TargetingMath.YawPitch angles = TargetingMath.yawPitchFromDirection(direction);
        AimSolution original = result.aimSolution();
        AimSolution rebasedAim = new AimSolution(
                angles.yawDeg(), angles.pitchDeg(), direction,
                translatedAimPoint, original.flightTicks(),
                original.missDistance());
        return new TargetingResult(
                result.valid(), result.hasShot(), rebasedAim,
                angles.yawDeg(), angles.pitchDeg(),
                result.predictedFlightTicks(),
                result.missDistance(), result.confidence(),
                result.reason(), result.debugInfo(), result.debugData());
    }

    static Vec3 rebaseRigidAimPoint(
            Vec3 originalAimPoint,
            RigidBodyTargetMotion originalMotion,
            RigidBodyTargetMotion liveMotion,
            double flightTicks,
            int liveLeadTicks
    ) {
        double flight = Math.max(0.0, flightTicks);
        Vec3 originalImpact = originalMotion.positionAt(flight);
        Vec3 liveImpact = liveMotion.motion().futurePosition(liveMotion.observedWorldPoint(), Math.max(0, liveLeadTicks) + flight);
        return originalAimPoint.add(liveImpact.subtract(originalImpact));
    }

    static Vec3 rebaseAimPoint(
            @Nullable Vec3 aimPoint,
            @Nullable Vec3 requestedTarget,
            @Nullable Vec3 liveTarget) {
        if (liveTarget == null) {
            return Vec3.ZERO;
        }
        if (aimPoint == null || requestedTarget == null) {
            return liveTarget;
        }
        return aimPoint.add(liveTarget.subtract(requestedTarget));
    }

    @Nullable
    static Vec3 rebaseAimDirection(
            @Nullable Vec3 originalDirection,
            @Nullable Vec3 originalAimPoint,
            @Nullable Vec3 requestedOrigin,
            @Nullable Vec3 currentOrigin,
            @Nullable Vec3 translatedAimPoint) {
        if (currentOrigin == null || translatedAimPoint == null) {
            return null;
        }
        if (originalDirection == null
                || originalDirection.lengthSqr() < 1.0E-12
                || originalAimPoint == null
                || requestedOrigin == null) {
            return directionFromTo(
                    currentOrigin, translatedAimPoint);
        }

        Vec3 normalized = originalDirection.normalize();
        double rayDistance =
                requestedOrigin.distanceTo(originalAimPoint);
        if (!Double.isFinite(rayDistance)
                || rayDistance < 1.0E-6) {
            return directionFromTo(
                    currentOrigin, translatedAimPoint);
        }
        Vec3 solvedRayPoint = requestedOrigin.add(
                normalized.scale(rayDistance));
        Vec3 ballisticCorrection =
                solvedRayPoint.subtract(originalAimPoint);
        return directionFromTo(
                currentOrigin,
                translatedAimPoint.add(ballisticCorrection));
    }

    private boolean isCachedTargetingResultFreshForFire(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            @Nullable UUID targetMotionId,
            Vec3 solvePos, String shotFingerprint) {
        return "fresh".equals(this.asyncFreshnessReason(
                serverLevel, cannonContraption, targetMotionId,
                solvePos, shotFingerprint));
    }

    private String asyncFreshnessReason(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            @Nullable UUID targetMotionId,
            Vec3 solvePos, String shotFingerprint) {
        if (this.cachedTargetingResult == null
                || this.cachedTargetingRequestTick == Long.MIN_VALUE
                || this.cachedTargetingSolvePos == null
                || this.cachedTargetingOriginPos == null) {
            return "no_authoritative_solution";
        }
        if (shotFingerprint == null
                || !shotFingerprint.equals(
                this.cachedTargetingShotFingerprint)) {
            return "shot_changed";
        }
        if (!Objects.equals(
                targetMotionId, this.cachedTargetingTargetId)) {
            return "target_changed";
        }
        Vec3 observedOrigin = this.resolveSteeringOrigin(cannonContraption);

        Vec3 currentOrigin = this.cachedTargetingRequest != null
                ? this.predictedAsyncSteeringOrigin(serverLevel, cannonContraption, this.cachedTargetingRequest.leadTicks())
                : observedOrigin;
        if (currentOrigin == null || solvePos == null) {
            return "missing_position";
        }
        Vec3 requestDirection = directionFromTo(
                this.cachedTargetingOriginPos,
                this.cachedTargetingSolvePos);
        Vec3 currentDirection =
                directionFromTo(currentOrigin, solvePos);
        if (requestDirection == null || currentDirection == null
                || requestDirection.dot(currentDirection)
                < ASYNC_STEERING_MAX_DIVERGENCE_COS) {
            return "steering_diverged";
        }

        long age = serverLevel.getGameTime() - this.cachedTargetingRequestTick;
        double targetMovementSquared = solvePos.distanceToSqr(this.cachedTargetingSolvePos);
        double originMovementSquared = currentOrigin.distanceToSqr(this.cachedTargetingOriginPos);

        AsyncTargetingRequest request = this.cachedTargetingRequest;

        if (request != null && request.snapshot().rigidBodyTargetMotion() != null) {
            RigidBodyTargetMotion rigidTarget = request.snapshot().rigidBodyTargetMotion();
            Vec3 expectedTargetNow = rigidTarget.motion().futurePosition(rigidTarget.observedWorldPoint(), Math.max(0L, age));
            targetMovementSquared = solvePos.distanceToSqr(expectedTargetNow);
        }

        if (request != null && (request.shooterMotion() != null || this.isSableMount())) {
            if (!this.isSableMount() || request.shooterMotion() == null || this.captureAsyncSableMotion(serverLevel) == null) {
                return "shooter_motion_unavailable";
            }

            originMovementSquared = asyncSableOriginPredictionErrorSquared(request.shooterMotion(), request.observedSteeringOrigin(), age, request.leadTicks(), currentOrigin);
        }

        if (age < 0L || age > ASYNC_TARGETING_FIRE_FRESH_TICKS) {
            return "age";
        }
        if (targetMovementSquared > 4.0) {
            return "target_moved";
        }
        if (originMovementSquared > 1.0) {
            return "origin_moved";
        }

        AsyncTargetingRequest motionRequest = this.cachedTargetingRequest;
        if (motionRequest != null && motionRequest.shooterMotion() != null) {
            RigidBodyLaunchMath.Motion liveMotion = this.captureAsyncSableMotion(serverLevel);

            if (liveMotion == null) {
                return "shooter_motion_unavailable";
            }

            Vec3 livePivot = this.resolveSteeringOrigin(cannonContraption);
            AimSolution originalAim = this.cachedTargetingResult.aimSolution();

            if (originalAim == null || !finite(originalAim.aimDirection())) {
                return "invalid_aim";
            }

            double velocityErrorSquared =
                    asyncSableLaunchVelocityErrorSquared(
                            motionRequest.shooterMotion(),
                            liveMotion,
                            motionRequest.observedSteeringOrigin(),
                            livePivot,
                            originalAim.aimDirection(),
                            age,
                            motionRequest.leadTicks(),
                            motionRequest.snapshot().muzzleForwardOffset()
                    );

            if (!asyncSableVelocityWithinTolerance(velocityErrorSquared, this.cachedTargetingResult.predictedFlightTicks())) {
                return "shooter_velocity_changed";
            }
        }

        return isAsyncSolutionFreshForFire(age, targetMovementSquared, originMovementSquared) ? "fresh" : "invalid";
    }

    private boolean shouldRefreshAsyncTargeting(
            ServerLevel serverLevel, boolean solveEveryTick,
            boolean completedAsyncResult) {
        if (this.pendingTargetingFuture != null && !this.pendingTargetingFuture.isDone()) {
            if (this.pendingTargetingRequest == null) {
                return true;
            }

            long pendingAge = serverLevel.getGameTime() - this.pendingTargetingRequest.requestTick();
            return pendingAge < 0L || pendingAge > ASYNC_TARGETING_TASK_TIMEOUT_TICKS;
        }

        if (this.cachedTargetingResult == null
                || this.cachedTargetingRequestTick == Long.MIN_VALUE) {
            return true;
        }
        if (solveEveryTick && completedAsyncResult) {
            return true;
        }
        return serverLevel.getGameTime()
                - this.cachedTargetingRequestTick
                >= ASYNC_TARGETING_REFRESH_TICKS;
    }

    private void submitAsyncTargetingSolve(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            Vec3 rawTargetPos, Vec3 targetWorldPos,
            Vec3 guidancePositionOffset,
            Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel,
            @Nullable Entity targetEntity,
            @Nullable SubLevelAccess targetSublevel,
            TargetMotionClass targetMotionClass, int trackingLeadTicks,
            @Nullable UUID targetMotionId) {

        if (this.pendingTargetingFuture != null && this.pendingTargetingFuture.isDone()) {
            return;
        }

        ResolvedProjectileState currentProjectile = this.resolveProjectileState(cannonContraption, serverLevel);
        if (currentProjectile == null || currentProjectile.solverKind() == SolverKind.CBCMS_SERVER) {
            return;
        }
        if (this.pendingTargetingFuture != null && !this.pendingTargetingFuture.isDone()) {
            AsyncTargetingRequest pending = this.pendingTargetingRequest;
            long age = pending == null ? 0L : serverLevel.getGameTime() - pending.requestTick();
            boolean differentShot = pending != null
                    && !currentProjectile.fingerprint().equals(pending.shotFingerprint());
            boolean samePendingTarget = pending != null
                    && Objects.equals(targetMotionId, pending.targetId());
            boolean keepPending = pending != null
                    && shouldKeepPendingSolve(
                    age, samePendingTarget, !differentShot);
            if (keepPending) {
                return;
            }
            if (age > ASYNC_TARGETING_TASK_TIMEOUT_TICKS) {
                ++this.asyncSolveTimedOut;
            } else {
                ++this.asyncSolveSuperseded;
            }
            this.pendingTargetingFuture.cancel(true);
            this.pendingTargetingFuture = null;
            this.pendingTargetingRequest = null;
        }

        int refinementFloor = pendingBallisticRefinementFloorTicks;
        pendingBallisticRefinementFloorTicks = -1;

        AsyncTargetingRequest request = this.buildAsyncTargetingRequest(
                serverLevel, cannonContraption, rawTargetPos,
                targetWorldPos, guidancePositionOffset,
                targetVel, targetAccel, shooterVel,
                targetEntity, targetSublevel, targetMotionClass,
                trackingLeadTicks, targetMotionId,
                Math.max(0, refinementFloor),
                refinementFloor >= 0);
        if (request == null) {
            return;
        }

        try {
            this.pendingTargetingRequest = request;

            TargetingComputer computer = this.targetingComputerFor(request.solverKind(), true);
            TargetingSnapshot workerSnapshot = request.snapshot().withoutLiveLevel();
            this.pendingTargetingFuture = submitTargetingTask(() -> new AsyncTargetingResult(request, computer.solve(workerSnapshot, request.projectileModel()), null));
            ++this.asyncSolveSubmitted;
        } catch (RejectedExecutionException rejected) {
            this.pendingTargetingRequest = null;
            this.pendingTargetingFuture = null;
            ++this.asyncSolveRejected;
            LOGGER.debug("WFC TargetingComputer async solve queue is full; skipping request for mount={} target={}", request.mountPos(), request.targetId());
        }
    }

    private void submitAsyncObstructionFallback(AsyncTargetingRequest request) {
        if (this.pendingTargetingFuture != null) {
            return;
        }

        try {
            TargetingComputer computer = this.targetingComputerFor(request.solverKind(), true);
            TargetingSnapshot workerSnapshot = request.snapshot().withoutLiveLevel();
            this.pendingTargetingRequest = request;
            this.pendingTargetingFuture = submitTargetingTask(() -> new AsyncTargetingResult(request, null, computer.solveObstructionAlternatives(workerSnapshot, request.projectileModel())));
            ++this.asyncSolveSubmitted;
        } catch (RejectedExecutionException rejected) {
            this.pendingTargetingRequest = null;
            this.pendingTargetingFuture = null;
            ++this.asyncSolveRejected;

            LOGGER.debug("WFC async obstruction fallback queue full: mount={} target={}", request.mountPos(), request.targetId());
        }
    }

    @Nullable
    private ResolvedProjectileState resolveProjectileState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        return resolveProjectileState(this.cannonMount, cannon, level);
    }

    @Nullable
    private ResolvedProjectileState resolveProjectileState(
            CannonMountContext mount,
            AbstractMountedCannonContraption cannon,
            ServerLevel level
    ) {
        if (cannon == null || level == null) {
            return null;
        }

        WeaponShotProfile adapterShot =
                this.resolveAdapterShot(mount, cannon, level);
        if (adapterShot != null) {
            if (adapterShot.aimMode() != WeaponShotProfile.AimMode.BALLISTIC || adapterShot.projectileModel() == null) {
                return null;
            }

            return new ResolvedProjectileState(
                    ProjectileModelBridge.toInternal(adapterShot.projectileModel()),
                    null,
                    adapterShot.maxFlightTicks(),
                    adapterShot.diagnosticReason(),
                    SolverKind.STANDARD_ASYNC,
                    adapterShot.fingerprint(),
                    false,
                    "weapon_adapter",
                    adapterShot.diagnosticReason(),
                    0
            );
        }

        CBCMSCannonCompat.ShotState cbcmsShot = CannonUtil.resolveCBCMSShotState(cannon, level);
        if (cbcmsShot != null) {
            SolverKind solverKind = cbcmsShot.solverMode() == CBCMSCannonCompat.SolverMode.CBCMS_SERVER
                    ? SolverKind.CBCMS_SERVER
                    : SolverKind.STANDARD_ASYNC;
            return new ResolvedProjectileState(
                    cbcmsShot.projectileModel(),
                    cbcmsShot.ballistics(),
                    cbcmsShot.lifetimeCapTicks(),
                    cbcmsShot.diagnosticReason(),
                    solverKind,
                    cbcmsShot.fingerprint(),
                    cbcmsShot.legacyEligible(),
                    cbcmsShot.family().name().toLowerCase(),
                    cbcmsShot.ammunitionId(),
                    0
            );
        }
        if (CannonUtil.isCBCMSCannon(cannon)) {
            return null;
        }

        CBCATCannonCompat.ShotState cbcAtShot = CannonUtil.resolveCBCATShotState(cannon, level);
        if (CannonUtil.isCBCATCannon(cannon)) {
            if (cbcAtShot == null) {
                return null;
            }
            return new ResolvedProjectileState(
                    cbcAtShot.projectileModel(),
                    cbcAtShot.ballistics(),
                    cbcAtShot.maxFlightTicks(),
                    cbcAtShot.reason(),
                    cbcAtShot.poweredRocket() ? SolverKind.CBCAT_ASYNC : SolverKind.STANDARD_ASYNC,
                    cbcAtShot.fingerprint(),
                    !cbcAtShot.poweredRocket(),
                    cbcAtShot.kind().name().toLowerCase(),
                    cbcAtShot.ammunition(),
                    cbcAtShot.fuelTicks()
            );
        }

        CBCMWCannonCompat.ShotState cbcmwShot = CannonUtil.resolveCBCMWShotState(cannon, level);
        if (CannonUtil.isCBCMWCannon(cannon)) {
            if (cbcmwShot == null || !Float.isFinite(cbcmwShot.speed()) || cbcmwShot.speed() <= 0.0F) {
                return null;
            }
            BallisticPropertiesComponent ballistics = cbcmwShot.ballistics();
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            ProjectileModel model = ProjectileModel.cbc(
                    cbcmwShot.speed(),
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    dimension.dragMultiplier(),
                    ballistics.isQuadraticDrag()
            );
            String family = CannonUtil.isMediumCannon(cannon) ? "cbcmw_medium" : "cbcmw_rotary";
            String fingerprint = Integer.toHexString(java.util.Objects.hash(
                    family, cbcmwShot.speed(), ballistics, cbcmwShot.lifetimeTicks(), cbcmwShot.ammunition()));
            return new ResolvedProjectileState(
                    model,
                    ballistics,
                    cbcmwShot.lifetimeTicks(),
                    "cbcmodernwarfare_projectile",
                    SolverKind.STANDARD_ASYNC,
                    fingerprint,
                    true,
                    family,
                    cbcmwShot.ammunition(),
                    0
            );
        }

        if (CannonUtil.isBigCannon(cannon)) {
            CannonUtil.BigCannonShotState bigShot = CannonUtil.resolveBigCannonShotState(cannon, level);
            if (bigShot == null || !bigShot.hasProjectile()
                    || !Double.isFinite(bigShot.speed())
                    || bigShot.speed() <= 0.0F) {
                return null;
            }
            BallisticPropertiesComponent ballistics = bigShot.ballistics();
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            ProjectileModel model = ProjectileModel.cbc(
                    bigShot.speed(),
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    dimension.dragMultiplier(),
                    ballistics.isQuadraticDrag()
            );
            String fingerprint = bigCannonShotFingerprint(model, bigShot);
            return new ResolvedProjectileState(model, ballistics, 0, bigShot.reason(),
                    SolverKind.STANDARD_ASYNC, fingerprint, true, "big_cannon",
                    bigShot.projectileClass() == null ? "<unresolved>" : bigShot.projectileClass(), 0);
        }

        double speed = CannonUtil.getInitialVelocity(cannon, level);
        if (!Double.isFinite(speed) || speed <= 0.0) {
            return null;
        }
        BallisticPropertiesComponent ballistics =
                CannonUtil.isAutocannonFamily(cannon)
                        ? CannonUtil.resolveLoadedAutocannonBallistics(
                        cannon, level)
                        : CannonUtil.getBallistics(cannon, level);
        if (CannonUtil.isAutocannonFamily(cannon)
                && ballistics == null) {
            return null;
        }
        double gravity = ballistics == null ? CannonUtil.getProjectileGravity(cannon, level) : ballistics.gravity();
        double drag = ballistics == null ? CannonUtil.getProjectileDrag(cannon, level) : ballistics.drag();
        if (!Double.isFinite(gravity)) {
            gravity = -0.05;
        }
        if (!Double.isFinite(drag)) {
            drag = 0.01;
        }
        ProjectileModel model = ProjectileModel.simple(
                speed,
                gravity,
                drag,
                ballistics != null && ballistics.isQuadraticDrag()
        );
        int lifetime = CannonUtil.isAutocannonFamily(cannon)
                ? CannonUtil.getAutocannonLifetimeTicks(cannon, level)
                : 0;
        String fingerprint = Integer.toHexString(java.util.Objects.hash(
                cannon.getClass().getName(), model, ballistics, lifetime));
        return new ResolvedProjectileState(model, ballistics, lifetime, "standard_projectile",
                SolverKind.STANDARD_ASYNC, fingerprint, true, "standard",
                CannonUtil.isAutocannonFamily(cannon) ? "<autocannon ammo>" : "<standard ammo>", 0);
    }

    static String bigCannonShotFingerprint(
            ProjectileModel model,
            CannonUtil.BigCannonShotState shot
    ) {
        return Integer.toHexString(java.util.Objects.hash(
                "big_cannon", model, shot.ballistics(), shot.projectileClass(),
                shot.projectileLocalPos(), shot.propellantCharges(),
                shot.propellantPower(), shot.projectileAddedPower()));
    }

    @Nullable
    private WeaponShotProfile resolveAdapterShot(
            @Nullable WeaponShotContext context
    ) {
        if (context == null) {
            return null;
        }

        return WeaponShotAdapterRegistry.resolve(context);
    }

    @Nullable
    private WeaponShotProfile resolveAdapterShot(RadarWeaponAdapter weapon, ServerLevel serverLevel) {
        if (weapon == null || serverLevel == null || !weapon.isValid()) {
            return null;
        }

        WeaponShotContext context = new WeaponShotContext(serverLevel, weapon.createContext(serverLevel));

        return resolveAdapterShot(context);
    }

    @Nullable
    private WeaponShotProfile resolveAdapterShot(CannonMountContext mount, AbstractMountedCannonContraption cannon, ServerLevel serverLevel) {
        if (mount == null || cannon == null || serverLevel == null) {
            return null;
        }

        if (mount.getContraption() == null) {
            return null;
        }

        return resolveAdapterShot(createNativeShotContext(serverLevel, mount));
    }

    private int solverFlightTicks(ResolvedProjectileState projectile, Vec3 muzzlePosition, Vec3 targetPosition) {
        int computed = this.computeNewSolverMaxFlightTicks(projectile.model().muzzleSpeed(), muzzlePosition, targetPosition);
        return projectile.lifetimeCapTicks() > 0
                ? Math.max(1, Math.min(computed, projectile.lifetimeCapTicks()))
                : computed;
    }

    @Nullable
    private AsyncTargetingRequest buildAsyncTargetingRequest(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            Vec3 rawTargetPos, Vec3 targetWorldPos,
            Vec3 guidancePositionOffset,
            Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel,
            @Nullable Entity targetEntity,
            @Nullable SubLevelAccess targetSublevel,
            TargetMotionClass targetMotionClass, int trackingLeadTicks,
            @Nullable UUID targetMotionId,
            int minimumLaunchDelayTicks,
            boolean ballisticRefined) {
        if (serverLevel == null || cannonContraption == null || targetWorldPos == null) {
            return null;
        }

        AimGeometry aimGeometry =
                this.resolveAimGeometry(cannonContraption);
        Vec3 rawMuzzlePos = aimGeometry.steeringOrigin();
        Vec3 muzzleWorldPos = aimGeometry.muzzlePosition();
        if (muzzleWorldPos == null) {
            return null;
        }

        ResolvedProjectileState projectile = this.resolveProjectileState(cannonContraption, serverLevel);
        if (projectile == null) {
            return null;
        }
        if (projectile.solverKind() == SolverKind.CBCMS_SERVER) {
            return null;
        }
        ProjectileModel model = projectile.model();
        BallisticPropertiesComponent ballistics = projectile.ballistics();
        double projectileSpeed = model.muzzleSpeed();
        double gravity = model.gravity();
        double drag = model.drag();
        double dragDensity = model.dragDensity();
        boolean cbcPhysics = model.cbcPhysics();

        Vec3 safeTargetVel = finiteOrZero(targetVel);
        Vec3 safeTargetAccel = clampAcceleration(finiteOrZero(targetAccel));
        Vec3 safeShooterVel = finiteOrZero(shooterVel);
        Vec3 effectiveTargetAccel = TargetingMotion.effectiveAcceleration(safeTargetAccel, targetMotionClass);
        boolean movingSableMount = this.isSableMount();

        RigidBodyLaunchMath.Motion sableMotion = movingSableMount ? this.captureAsyncSableMotion(serverLevel) : null;
        RigidBodyTargetMotion observedRigidTarget = this.captureRigidBodyTargetMotion(serverLevel, targetSublevel, rawTargetPos, targetWorldPos, safeTargetVel, guidancePositionOffset, 0);
        int predictedTicks;

        if (isExternallyTriggeredYawMount(serverLevel)) {
            predictedTicks = Math.max(0, Math.min(1, trackingLeadTicks));
        } else if (ballisticRefined) {
            predictedTicks = Math.max(0, Math.min(60, minimumLaunchDelayTicks));
        } else {
            predictedTicks = this.chooseReachableLaunchDelayTicks(serverLevel, cannonContraption, model, muzzleWorldPos, safeShooterVel, sableMotion, targetWorldPos, safeTargetVel, effectiveTargetAccel, observedRigidTarget, trackingLeadTicks, targetMotionId);
        }

        boolean inheritLaunchVelocity = inheritsCbcSableLaunchVelocity(this.cannonMount, cannonContraption);
        Vec3 predictedMuzzlePos = sableMotion != null ? sableMotion.futurePosition(muzzleWorldPos, predictedTicks) : movingSableMount ? TargetingMotion.futurePosition(muzzleWorldPos, safeShooterVel, Vec3.ZERO, predictedTicks) : muzzleWorldPos;
        Vec3 predictedPivotPos = null;

        if (aimGeometry.coupled()) {
            Vec3 pivot = aimGeometry.steeringOrigin();

            if (sableMotion != null) {
                predictedPivotPos = sableMotion.futurePosition(pivot, predictedTicks);
            } else if (movingSableMount) {
                Vec3 pivotVelocity = this.getPlatformVelocityAtMuzzle(serverLevel, this.cannonMount, pivot);
                predictedPivotPos = TargetingMotion.futurePosition(pivot, pivotVelocity, Vec3.ZERO, predictedTicks);
            } else {
                predictedPivotPos = pivot;
            }
        }

        Vec3 predictedShooterVel = sableMotion != null ? sableMotion.futureVelocity(muzzleWorldPos, predictedTicks) : safeShooterVel;
        Vec3 predictedAngularVelocity = sableMotion != null && aimGeometry.coupled() ? sableMotion.angularVelocityAt(predictedTicks) : Vec3.ZERO;
        RigidBodyTargetMotion rigidTarget = observedRigidTarget == null ? null : new RigidBodyTargetMotion(observedRigidTarget.motion(), observedRigidTarget.observedWorldPoint(), predictedTicks);

        Vec3 delayedTargetPos = rigidTarget != null
                ? rigidTarget.launchPosition()
                : TargetingMotion.futureTargetPosition(targetWorldPos, safeTargetVel, effectiveTargetAccel, predictedTicks, TargetingMotion.TARGET_ACCEL_HOLD_TICKS);

        Vec3 delayedTargetVel = rigidTarget != null
                ? rigidTarget.launchVelocity()
                : TargetingMotion.futureTargetVelocity(safeTargetVel, effectiveTargetAccel, predictedTicks, TargetingMotion.TARGET_ACCEL_HOLD_TICKS);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "targeting.async_prediction_displacement",
                    serverLevel,
                    getWeaponMountPos(),
                    "predictedLaunchDelayTicks", predictedTicks,
                    "observedTargetPosition", targetWorldPos,
                    "predictedTargetPosition", delayedTargetPos,
                    "targetDisplacementBlocks",
                    targetWorldPos.distanceTo(delayedTargetPos),
                    "observedTargetVelocity", safeTargetVel,
                    "predictedTargetVelocity", delayedTargetVel,
                    "observedMuzzlePosition", muzzleWorldPos,
                    "predictedMuzzlePosition", predictedMuzzlePos,
                    "muzzleDisplacementBlocks",
                    muzzleWorldPos.distanceTo(predictedMuzzlePos),
                    "sourceSable", movingSableMount,
                    "targetSable", targetSublevel != null,
                    "rigidTargetPrediction", rigidTarget != null
            );
        }

        AABB targetAabb = targetEntity != null
                ? this.resolveEntityWorldAabb(serverLevel, targetEntity)
                : this.resolveSublevelWorldAabb(targetSublevel);
        targetAabb = rigidTarget != null
                ? null
                : translateTargetAabb(targetAabb, guidancePositionOffset, delayedTargetPos.subtract(targetWorldPos));

        if (this.shouldLogTargetingDebug(serverLevel)) {
            this.logTargetingCoordinateResolution(serverLevel, rawTargetPos, targetWorldPos, rawMuzzlePos, muzzleWorldPos, safeShooterVel);
            LOGGER.debug("WFC async projectile state: speed={} gravity={} drag={} quadratic={} cbcPhysics={} lifetimeCap={} reason={}",
                    projectileSpeed, gravity, drag, model.quadraticDrag(), cbcPhysics,
                    projectile.lifetimeCapTicks(), projectile.reason());
        }

        TargetingResult previous = this.cachedTargetingResult;
        boolean previousShot = previous != null && previous.valid() && previous.hasShot();
        Double previousYaw = null;
        Double previousPitch = null;
        if (previousShot) {
            previousYaw = Double.valueOf(previous.desiredYawDeg());
            previousPitch = Double.valueOf(previous.desiredPitchDeg());
        }
        Double preferredYaw = this.preferredYaw(previousYaw);
        Double preferredPitch = this.preferredPitch(previousPitch);
        TargetingSnapshot snapshot = TargetingSnapshot.builder(serverLevel)
                .muzzlePosition(predictedMuzzlePos)
                .swivelLaunchGeometry(resolveSwivelLaunchGeometry(this.cannonMount, muzzleWorldPos, sableMotion, predictedTicks))
                .launchPivotPosition(predictedPivotPos)
                .muzzleForwardOffset(aimGeometry.muzzleForwardOffset())
                .inheritedVelocity(inheritLaunchVelocity ? predictedShooterVel : Vec3.ZERO)
                .launchAngularVelocity(inheritLaunchVelocity ? predictedAngularVelocity : Vec3.ZERO)
                .targetPosition(delayedTargetPos)
                .targetVelocity(delayedTargetVel)
                .targetAcceleration(rigidTarget != null ? Vec3.ZERO : safeTargetAccel)
                .targetAccelerationRemainingTicks(rigidTarget != null ? 0 : Math.max(0, TargetingMotion.TARGET_ACCEL_HOLD_TICKS - predictedTicks))
                .targetAabb(targetAabb)
                .rigidBodyTargetMotion(rigidTarget)
                .projectileSpeed(projectileSpeed)
                .gravity(gravity)
                .drag(drag)
                .quadraticDrag(model.quadraticDrag())
                .cbcPhysics(cbcPhysics)
                .dragDensity(dragDensity)
                .maxFlightTicks(this.solverFlightTicks(
                        projectile, predictedMuzzlePos,
                        delayedTargetPos))
                .gameTime(serverLevel.getGameTime()
                        + (long)predictedTicks)
                .preferredYawDeg(preferredYaw)
                .preferredPitchDeg(preferredPitch)
                .currentYawDeg(this.currentSolverYawDeg())
                .currentPitchDeg(
                        this.currentPitchDeg(cannonContraption))
                .targetSublevelId(targetSublevel != null
                        ? targetSublevel.getUniqueId() : null)
                .targetMotionClass(targetMotionClass)
                .pitchConstraint(this.effectivePitchConstraint(cannonContraption))
                .preferHighArc(this.targetingConfig.preferHighArc())
                .build();
        LOGGER.debug("[RADAR-TRACE][ASYNC-SNAPSHOT] " + "tick={} mount={} targetId={} " + "family={} ammo={} solver={} fingerprint={} " + "highArc={} flightCap={} maxFlight={} " + "speed={} gravity={} drag={} quadratic={} " + "cbcPhysics={} " + "leadTicks={} trackingLeadTicks={} ballisticRefined={} sourceSable={} rigidTarget={} " + "observedMuzzle={} predictedMuzzle={} " + "predictedPivot={} inheritedVelocity={} " + "angularVelocity={} " + "observedTarget={} predictedTarget={} " + "targetVelocity={} targetAcceleration={} " + "pitchLimits={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), targetMotionId, projectile.family(), projectile.ammunition(), projectile.solverKind(), projectile.fingerprint(), snapshot.preferHighArc(), projectile.lifetimeCapTicks(), snapshot.maxFlightTicks(), snapshot.projectileSpeed(), snapshot.gravity(), snapshot.drag(), snapshot.quadraticDrag(), snapshot.cbcPhysics(), predictedTicks, trackingLeadTicks, ballisticRefined, sableMotion != null, rigidTarget != null, muzzleWorldPos, predictedMuzzlePos, predictedPivotPos, snapshot.inheritedVelocity(), snapshot.launchAngularVelocity(), targetWorldPos, snapshot.targetPosition(), snapshot.targetVelocity(), snapshot.targetAcceleration(), snapshot.pitchConstraint().summary());
        return new AsyncTargetingRequest(this.cannonMount.getBlockPos().immutable(), targetMotionId, targetWorldPos, serverLevel.getGameTime(), snapshot, model, projectile.solverKind(), projectile.fingerprint(), aimGeometry.steeringOrigin(), predictedTicks, trackingLeadTicks, ballisticRefined, sableMotion);
    }

    private void logRigidTargetDiagnostic(
            ServerLevel serverLevel,
            SubLevelAccess targetSublevel,
            String state,
            int leadTicks,
            double velocityDelta
    ) {
        if (!RadarConfig.DEBUG_BEAMS) {
            return;
        }

        long now = serverLevel.getGameTime();
        String stateKey = targetSublevel.getUniqueId() + ":" + state;
        long interval = Objects.equals(stateKey, this.lastRigidTargetDiagnosticState) ? 100L : 20L;

        if (this.lastRigidTargetDiagnosticTick != Long.MIN_VALUE && now >= this.lastRigidTargetDiagnosticTick && now - this.lastRigidTargetDiagnosticTick < interval) {
            return;
        }

        this.lastRigidTargetDiagnosticTick = now;
        this.lastRigidTargetDiagnosticState = stateKey;

        LOGGER.info(
                "WFC rigid Sable target: ship={} state={} leadTicks={} velocityDeltaBlocksPerTick={}",
                targetSublevel.getUniqueId(),
                state,
                leadTicks,
                velocityDelta
        );
    }

    @Nullable
    private RigidBodyTargetMotion captureRigidBodyTargetMotion(
            ServerLevel serverLevel,
            @Nullable SubLevelAccess targetSublevel,
            Vec3 observedTargetPoint,
            Vec3 solvePosition,
            Vec3 targetVelocity,
            @Nullable Vec3 guidanceOffset,
            int leadTicks
    ) {
        if (targetSublevel == null) {
            return null;
        }

        if (!Mods.SABLE.isLoaded()) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:sable_unavailable",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        if (!finite(observedTargetPoint) || !finite(solvePosition) || !finite(targetVelocity)) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:invalid_observation",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        if (this.activetrack != null
                && this.activetrack.getJammingData() != null) {

            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:jammed",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        if (finiteOrZero(guidanceOffset).lengthSqr() > 1.0e-12) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:guidance_offset",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        if (observedTargetPoint.distanceToSqr(solvePosition) > 1.0e-8) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:aim_point_override",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        if (this.rigidTargetExternalMotionOverride) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:api_motion_override",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        RigidBodyLaunchMath.Motion motion = SableLaunchMotion.capture(serverLevel, targetSublevel);

        if (motion == null) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:motion_unavailable",
                    leadTicks,
                    Double.NaN
            );
            return null;
        }

        Vec3 physicalVelocity = motion.futureVelocity(observedTargetPoint, 0.0);
        double velocityDelta = finite(physicalVelocity) ? physicalVelocity.distanceTo(targetVelocity) : Double.NaN;

        if (!Double.isFinite(velocityDelta)) {
            logRigidTargetDiagnostic(
                    serverLevel,
                    targetSublevel,
                    "fallback:invalid_physical_velocity",
                    leadTicks,
                    velocityDelta
            );
            return null;
        }

        logRigidTargetDiagnostic(
                serverLevel,
                targetSublevel,
                "active",
                leadTicks,
                velocityDelta
        );

        return new RigidBodyTargetMotion(motion, observedTargetPoint, Math.max(0, leadTicks));
    }

    @Nullable
    private RigidBodyLaunchMath.Motion captureAsyncSableMotion(ServerLevel serverLevel) {
        if (!this.isSableMount() || this.cannonMount == null) {
            return null;
        }

        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(
                serverLevel, this.cannonMount.getBlockPos()
        );

        UUID shipId = ship == null ? null : ship.getUniqueId();
        long tick = serverLevel.getGameTime();

        if (this.cachedAsyncSableMotionTick == tick && Objects.equals(this.cachedAsyncSableMotionShipId, shipId)) {
            return this.cachedAsyncSableMotion;
        }

        this.cachedAsyncSableMotionTick = tick;
        this.cachedAsyncSableMotionShipId = shipId;
        this.cachedAsyncSableMotion = ship == null ? null : SableLaunchMotion.capture(serverLevel, ship);

        return this.cachedAsyncSableMotion;
    }

    @Nullable
    private Vec3 predictedAsyncSteeringOrigin(ServerLevel serverLevel, AbstractMountedCannonContraption cannon, int leadTicks) {
        Vec3 observed = this.resolveSteeringOrigin(cannon);

        if (!finite(observed) || leadTicks <= 0 || !this.isSableMount()) {
            return observed;
        }

        RigidBodyLaunchMath.Motion motion = this.captureAsyncSableMotion(serverLevel);
        if (motion != null) {
            return motion.futurePosition(observed, leadTicks);
        }

        return observed.add(this.getPlatformVelocityAtMuzzle(serverLevel, this.cannonMount, observed).scale(leadTicks));
    }

    private int chooseReachableLaunchDelayTicks(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannonContraption,
            ProjectileModel model,
            Vec3 observedMuzzle,
            Vec3 observedShooterVelocity,
            @Nullable RigidBodyLaunchMath.Motion shooterMotion,
            Vec3 observedTarget,
            Vec3 targetVelocity,
            Vec3 effectiveTargetAcceleration,
            @Nullable RigidBodyTargetMotion observedRigidTarget,
            int trackingLeadTicks,
            @Nullable UUID targetId) {

        final int maxPrediction = 60;

        final int predictedFlight = Math.max(1, Math.min(MAX_NEW_SOLVER_FLIGHT_TICKS, (int) Math.ceil(observedMuzzle.distanceTo(observedTarget) / Math.max(1.0e-6, model.muzzleSpeed()))));
        final boolean movingSableMount = isSableMount();

        LaunchTimeFeasibility.Result timing;
        inLaunchFeasibilityProbe = true;

        try {
            timing = LaunchTimeFeasibility.earliest(trackingLeadTicks, maxPrediction, delay -> {
            if ((yawController != null
                    && Math.abs(yawController.getAvailableInputSpeed()) < 1.0e-6) || (pitchController != null
                    && Math.abs(pitchController.getAvailableInputSpeed()) < 1.0e-6)) {
                return 61;
            }

            Vec3 futureMuzzle = shooterMotion != null
                    ? shooterMotion.futurePosition(observedMuzzle, delay) : movingSableMount
                    ? observedMuzzle.add(observedShooterVelocity.scale(delay)) : observedMuzzle;

            double impactTime = delay + predictedFlight;

            Vec3 futureTarget = observedRigidTarget != null ? observedRigidTarget.positionAt(impactTime) : TargetingMotion.futureTargetPosition(
                    observedTarget,
                    targetVelocity,
                    effectiveTargetAcceleration,
                    impactTime,
                    TargetingMotion.TARGET_ACCEL_HOLD_TICKS);
                Vec3 displacement = futureTarget.subtract(futureMuzzle);

                if (!Double.isFinite(displacement.x) || !Double.isFinite(displacement.y) || !Double.isFinite(displacement.z) || displacement.lengthSqr() <= 1.0e-12) {
                    return 61;
                }

                Vec3 direction = displacement.normalize();
                MountAimFrame frame = resolveMountAimFrame(cannonContraption);

                AimCommandEvaluation evaluation = frame.evaluate(direction);
                if (!evaluation.valid()) {
                    return 61;
                }

                TargetingMath.YawPitch angles = TargetingMath.yawPitchFromDirection(direction);
                AimSolution approximateSolution = new AimSolution(angles.yawDeg(), angles.pitchDeg(), direction, futureTarget, predictedFlight, 0.0);

                return estimateSlewTicksForSolution(cannonContraption, approximateSolution, angles.yawDeg() + 270.0, angles.pitchDeg());
            });
        } finally {
            inLaunchFeasibilityProbe = false;
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "targeting.launch_feasibility",
                    serverLevel,
                    getWeaponMountPos(),
                    "targetId", targetId,
                    "feasible", timing.feasible(),
                    "selectedDelayTicks", timing.launchDelayTicks(),
                    "requiredSlewTicks", timing.requiredSlewTicks(),
                    "trackingLeadTicks", trackingLeadTicks,
                    "estimatedFlightTicks", predictedFlight,
                    "flightEstimateSource", "geometric",
                    "candidatesChecked", timing.candidatesChecked(),
                    "measuredYawVelocity", measuredPhysicalYawRate,
                    "previousDelayTicks",
                    cachedTargetingRequest == null ? null : cachedTargetingRequest.leadTicks(),
                    "observedTarget", observedTarget,
                    "observedMuzzle", observedMuzzle,
                    "sourceSable", movingSableMount,
                    "targetRigid", observedRigidTarget != null
            );
        }

        return timing.launchDelayTicks();
    }

    private TargetingComputer targetingComputerFor(SolverKind solverKind, boolean async) {
        return switch (solverKind) {
            case CBCAT_ASYNC -> async ? this.asyncCBCAtRocketTargetingComputer : this.cbcAtRocketTargetingComputer;
            case CBCMS_SERVER -> async
                    ? this.asyncTargetingComputer
                    : this.cbcmsTargetingComputer;
            case STANDARD_ASYNC -> async ? this.asyncTargetingComputer : this.targetingComputer;
        };
    }

    @Nullable
    private Double preferredYaw(@Nullable Double previousAngle) {
        if (previousAngle != null && Double.isFinite(previousAngle)) {
            return previousAngle;
        }
        if (this.yawController == null) {
            return null;
        }
        if (this.yawController.hasStructuralKineticSelectionForTargeting()) {
            Vec3 physical = this.yawController.getStructuralPhysicalWorldDirection();
            return physical == null ? null
                    : TargetingMath.yawPitchFromDirection(physical).yawDeg();
        }
        if (this.isSableMount()) {
            return null;
        }
        double angle = this.yawController.getTargetAngle() - 270.0D;
        return Double.isFinite(angle) ? angle : null;
    }

    @Nullable
    private Double preferredPitch(@Nullable Double previousAngle) {
        if (previousAngle != null && Double.isFinite(previousAngle)) {
            return previousAngle;
        }

        if (this.pitchController == null) {
            return null;
        }

        if (usesStructuralPitchForAuthority()) {
            Vec3 physical = this.pitchController.getStructuralPhysicalWorldDirection();
            return physical == null ? null : TargetingMath.yawPitchFromDirection(physical).pitchDeg();
        }

        if (this.isSableMount()) {
            return null;
        }

        double angle = this.pitchController.getTargetAngle();
        return Double.isFinite(angle) ? angle : null;
    }

    private ImmediateLaunchCheck checkImmediateLaunchForFire(
            ServerLevel serverLevel,
            AbstractMountedCannonContraption cannon,
            ResolvedProjectileState projectile,
            Vec3 rawTargetPos,
            Vec3 targetWorldPos,
            Vec3 guidanceOffset,
            Vec3 targetVel,
            Vec3 targetAccel,
            Vec3 shooterVel,
            @Nullable Entity targetEntity,
            @Nullable SubLevelAccess targetSublevel,
            TargetMotionClass motionClass,
            TargetingResult futureResult) {

        if (cannonMount == null || cannonMount.getContraption() == null || projectile == null || futureResult == null || !finite(targetWorldPos)) {
            return ImmediateLaunchCheck.reject("missing_launch_state");
        }

        Vec3 physicalDirection = CBCMuzzleUtil.getForwardWorld(cannonMount.getContraption());

        if (isSableMount()) {
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(serverLevel, cannonMount.getBlockPos());

            if (ship == null) {
                return ImmediateLaunchCheck.reject("source_sublevel_unavailable");
            }

            physicalDirection = SableUtils.getWorldVecDirectionTransform(physicalDirection, ship);
        }

        if (!finite(physicalDirection) || physicalDirection.lengthSqr() < 1.0e-12) {
            return ImmediateLaunchCheck.reject("invalid_physical_direction");
        }

        physicalDirection = physicalDirection.normalize();

        AimGeometry geometry = resolveAimGeometry(cannon);
        Vec3 muzzle = geometry.muzzlePosition();

        if (!finite(muzzle) || !finite(geometry.steeringOrigin())) {
            return ImmediateLaunchCheck.reject("invalid_muzzle");
        }

        ProjectileModel model = projectile.model();
        RigidBodyLaunchMath.Motion sourceMotion = isSableMount() ? captureAsyncSableMotion(serverLevel) : null;
        boolean inheritVelocity = inheritsCbcSableLaunchVelocity(cannonMount, cannon);
        Vec3 launchVelocity = sourceMotion != null ? sourceMotion.futureVelocity(muzzle, 0.0) : finiteOrZero(shooterVel);

        if (!inheritVelocity) {
            launchVelocity = Vec3.ZERO;
        }

        Vec3 launchAngularVelocity = inheritVelocity && sourceMotion != null && geometry.coupled() ? sourceMotion.angularVelocityAt(0) : Vec3.ZERO;

        Vec3 safeTargetVelocity = finiteOrZero(targetVel);
        Vec3 safeTargetAcceleration = clampAcceleration(finiteOrZero(targetAccel));

        RigidBodyTargetMotion rigidTarget =
                captureRigidBodyTargetMotion(
                        serverLevel,
                        targetSublevel,
                        rawTargetPos,
                        targetWorldPos,
                        safeTargetVelocity,
                        guidanceOffset,
                        0);

        AABB targetBox = targetEntity != null ? resolveEntityWorldAabb(serverLevel, targetEntity) : resolveSublevelWorldAabb(targetSublevel);

        targetBox = translateTargetAabb(targetBox, guidanceOffset, Vec3.ZERO);

        int flightCap = solverFlightTicks(projectile, muzzle, targetWorldPos);
        int simulationTicks = Math.max(2, Math.min(flightCap, Math.max(16, futureResult.predictedFlightTicks() + 8)));

        TargetingSnapshot snapshot =
                TargetingSnapshot.builder(serverLevel)
                        .muzzlePosition(muzzle)
                        .launchPivotPosition(geometry.coupled() ? geometry.steeringOrigin() : null)
                        .muzzleForwardOffset(geometry.muzzleForwardOffset())
                        .swivelLaunchGeometry(resolveSwivelLaunchGeometry(cannonMount, muzzle, sourceMotion, 0))
                        .inheritedVelocity(launchVelocity)
                        .launchAngularVelocity(launchAngularVelocity)
                        .targetPosition(targetWorldPos)
                        .targetVelocity(safeTargetVelocity)
                        .targetAcceleration(rigidTarget != null ? Vec3.ZERO : safeTargetAcceleration)
                        .targetAccelerationRemainingTicks(rigidTarget != null ? 0 : TargetingMotion.TARGET_ACCEL_HOLD_TICKS)
                        .targetAabb(targetBox)
                        .rigidBodyTargetMotion(rigidTarget)
                        .projectileSpeed(model.muzzleSpeed())
                        .gravity(model.gravity())
                        .drag(model.drag())
                        .quadraticDrag(model.quadraticDrag())
                        .cbcPhysics(model.cbcPhysics())
                        .dragDensity(model.dragDensity())
                        .maxFlightTicks(simulationTicks)
                        .gameTime(serverLevel.getGameTime())
                        .targetSublevelId(targetSublevel == null ? null : targetSublevel.getUniqueId())
                        .targetMotionClass(motionClass)
                        .build();

        double configuredMiss = Math.max(0.0, RadarConfig.server().autoFireMaxLateralErrorBlocks.get());
        double tolerance = targetBox == null ? Math.max(0.75, configuredMiss) : configuredMiss;

        final Vec3[] contactPoint = {null};
        final int[] contactTick = {-1};
        final Vec3 firingDirection = physicalDirection;

        ProjectileSimulator.SimulationResult trajectory;

        try {
            trajectory = validationProjectileSimulator.simulate(
                    snapshot.launchPosition(firingDirection),
                    firingDirection,
                    snapshot.inheritedVelocityAt(firingDirection),
                    model,
                    simulationTicks,
                    serverLevel,
                    (from, to) -> {
                        Vec3 targetFrom = predictSnapshotTarget(snapshot, from.tick());
                        Vec3 targetTo = predictSnapshotTarget(snapshot, to.tick());

                        double fraction = immediateTargetContactFraction(from.position(), to.position(), targetFrom, targetTo, snapshot.targetPosition(), snapshot.targetAabb(), tolerance);
                        if (!Double.isFinite(fraction)) {
                            return false;
                        }

                        contactPoint[0] = from.position().lerp(to.position(), fraction);
                        contactTick[0] = to.tick();
                        return true;
                    });

        } catch (LoadedFluidDrag.Unavailable unavailable) {
            return ImmediateLaunchCheck.reject("fluid_chunk_unavailable");
        }

        if (contactPoint[0] == null || contactTick[0] < 0) {
            return ImmediateLaunchCheck.reject("current_trajectory_misses");
        }

        List<Trajectory.Sample> samples = trajectory.samples();

        for (int i = 0; i + 1 < samples.size(); i++) {
            Trajectory.Sample from = samples.get(i);
            Trajectory.Sample to = samples.get(i + 1);

            if (from.tick() >= contactTick[0]) {
                break;
            }

            Vec3 segmentEnd = to.tick() == contactTick[0] ? contactPoint[0] : to.position();

            if (pathTouchesSafeZone(from.position(), segmentEnd)) {
                return ImmediateLaunchCheck.reject("safe_zone_intersection");
            }
        }

        if (!(binoMode && !binocularTargetsSable(serverLevel))) {
            boolean cbcSweep = model.cbcPhysics() && !model.usesCustomDynamics();
            ObstructionResult obstruction = mainThreadObstructionChecker.check(serverLevel, trajectory, contactTick[0], contactPoint[0], cbcSweep);

            if (!obstruction.clear() && !isValidatedObstructionAtTarget(snapshot, obstruction)) {
                return ImmediateLaunchCheck.reject("path_obstructed");
            }
        }

        return ImmediateLaunchCheck.accept(contactTick[0]);
    }

    static double immediateTargetContactFraction(Vec3 projectileFrom, Vec3 projectileTo, Vec3 targetFrom, Vec3 targetTo, Vec3 observedTarget, @Nullable AABB observedBox, double tolerance) {
        if (!finite(projectileFrom) || !finite(projectileTo) || !finite(targetFrom) || !finite(targetTo) || !finite(observedTarget)) {
            return Double.NaN;
        }

        double margin = Math.max(0.0, tolerance);

        if (observedBox != null) {
            AABB expanded = observedBox.inflate(margin);

            Vec3 relativeFrom = projectileFrom.subtract(targetFrom.subtract(observedTarget));
            Vec3 relativeTo = projectileTo.subtract(targetTo.subtract(observedTarget));

            if (expanded.contains(relativeFrom)) {
                return 0.0;
            }

            Vec3 intersection = expanded.clip(relativeFrom, relativeTo).orElse(null);

            if (intersection == null) {
                return Double.NaN;
            }

            Vec3 movement = relativeTo.subtract(relativeFrom);
            double lengthSquared = movement.lengthSqr();

            return lengthSquared < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, intersection.subtract(relativeFrom).dot(movement) / lengthSquared));
        }

        Vec3 relativeFrom = projectileFrom.subtract(targetFrom);
        Vec3 relativeTo = projectileTo.subtract(targetTo);
        Vec3 movement = relativeTo.subtract(relativeFrom);

        double lengthSquared = movement.lengthSqr();
        double fraction = lengthSquared < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, -relativeFrom.dot(movement) / lengthSquared));

        Vec3 closest = relativeFrom.add(movement.scale(fraction));

        return closest.lengthSqr() <= margin * margin ? fraction : Double.NaN;
    }

    private TargetingValidation validateAsyncTargetingResult(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, AsyncTargetingRequest request, @Nullable TargetingResult result) {
        if (result == null) {
            return rejectAsyncValidation("solver_result_null");
        }

        if (!result.valid() || !result.hasShot() || result.aimSolution() == null) {
            return rejectAsyncValidation("solver_no_valid_shot:" + result.reason());
        }

        AimSolution aim = result.aimSolution();
        Vec3 direction = aim.aimDirection();
        if (direction == null || direction.lengthSqr() < 1.0E-12) {
            return rejectAsyncValidation("invalid_aim_direction");
        }

        TargetingSnapshot snapshot = request.snapshot();
        ProjectileModel model = request.projectileModel();
        ResolvedProjectileState currentProjectile = this.resolveProjectileState(cannonContraption, serverLevel);
        if (currentProjectile == null
                || !request.shotFingerprint().equals(currentProjectile.fingerprint())
                || request.solverKind() != currentProjectile.solverKind()) {
            return rejectAsyncValidation("projectile_profile_changed");
        }

        int validationTicks = Math.max(1, Math.min(snapshot.maxFlightTicks(), result.predictedFlightTicks() + 1));
        ProjectileSimulator.SimulationResult trajectory;

        try {
            trajectory = this.validationProjectileSimulator.simulate(snapshot.launchPosition(direction), direction, snapshot.inheritedVelocityAt(direction), model, validationTicks, snapshot.level());
        } catch (LoadedFluidDrag.Unavailable ignored) {
            return TargetingValidation.invalid();
        }

        if (model instanceof CBCMWCannonCompat.LauncherProjectileModel launcherModel) {
            double expectedFluidDrag = launcherModel.sampledFluidDrag();
            List<Trajectory.Sample> samples = trajectory.samples();

            for (int i = 0; i + 1 < samples.size(); i++) {
                Vec3 position = samples.get(i).position();
                BlockPos pos = BlockPos.containing(position);
                if (serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                    return TargetingValidation.invalid();
                }

                double actualFluidDrag = LoadedFluidDrag.sample(serverLevel, pos);
                if (!Double.isFinite(actualFluidDrag) || Math.abs(actualFluidDrag - expectedFluidDrag) > 1.0E-6) {
                    return TargetingValidation.invalid();
                }
            }
        }

        int originalCheckTick = Math.max(0, result.predictedFlightTicks());

        Vec3 segmentImpact = null;
        int segmentImpactTick = -1;

        if (snapshot.targetAabb() != null) {
            List<Trajectory.Sample> samples = trajectory.samples();
            AABB targetBox = snapshot.targetAabb();

            for (int i = 0; i + 1 < samples.size(); i++) {
                Trajectory.Sample from = samples.get(i);
                Trajectory.Sample to = samples.get(i + 1);

                Vec3 targetFrom = predictSnapshotTarget(snapshot, from.tick());
                Vec3 targetTo = predictSnapshotTarget(snapshot, to.tick());

                Vec3 relativeFrom = from.position().subtract(targetFrom.subtract(snapshot.targetPosition()));
                Vec3 relativeTo = to.position().subtract(targetTo.subtract(snapshot.targetPosition()));
                Vec3 intersection = targetBox.contains(relativeFrom)
                        ? relativeFrom
                        : targetBox.clip(
                        relativeFrom,
                        relativeTo
                ).orElse(null);

                if (intersection == null) {
                    continue;
                }

                Vec3 travel = relativeTo.subtract(relativeFrom);
                double lengthSquared = travel.lengthSqr();
                double fraction = lengthSquared < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, intersection.subtract(relativeFrom).dot(travel) / lengthSquared));

                segmentImpact = from.position().lerp(to.position(), fraction);
                segmentImpactTick = to.tick();
                break;
            }

            if (segmentImpact != null) {
                for (int i = 0; i + 1 < samples.size(); i++) {
                    Trajectory.Sample from = samples.get(i);
                    Trajectory.Sample to = samples.get(i + 1);

                    if (from.tick() >= segmentImpactTick) {
                        break;
                    }

                    Vec3 segmentEnd = to.tick() == segmentImpactTick ? segmentImpact : to.position();
                    if (this.pathTouchesSafeZone(from.position(), segmentEnd)) {
                        return TargetingValidation.invalid();
                    }
                }
            }
        }

        if (this.binoMode && snapshot.targetAabb() == null && snapshot.targetSublevelId() == null) {
            Vec3 pointTarget = snapshot.targetPosition();
            double hitTolerance = this.directTargetHitTolerance(snapshot);
            double bestDistanceSqr = hitTolerance * hitTolerance;
            List<Trajectory.Sample> samples = trajectory.samples();

            for (int i = 0; i + 1 < samples.size(); i++) {
                Trajectory.Sample from = samples.get(i);
                Trajectory.Sample to = samples.get(i + 1);

                if (from.tick() >= originalCheckTick) {
                    break;
                }

                Vec3 delta = to.position().subtract(from.position());
                double segmentLengthSqr = delta.lengthSqr();
                double fraction = segmentLengthSqr < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, pointTarget.subtract(from.position()).dot(delta) / segmentLengthSqr));
                Vec3 closestPoint = from.position().add(delta.scale(fraction));
                double distanceSqr = closestPoint.distanceToSqr(pointTarget);

                if (distanceSqr <= bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    segmentImpact = closestPoint;
                    segmentImpactTick = to.tick();
                }
            }
        }

        int pathEndTick = segmentImpact != null ? segmentImpactTick : originalCheckTick;
        List<Trajectory.Sample> safeZoneSamples = trajectory.samples();

        for (int i = 0; i + 1 < safeZoneSamples.size(); i++) {
            Trajectory.Sample from = safeZoneSamples.get(i);
            Trajectory.Sample to = safeZoneSamples.get(i + 1);

            if (from.tick() >= pathEndTick) {
                break;
            }

            Vec3 segmentEnd = segmentImpact != null && to.tick() == pathEndTick ? segmentImpact : to.position();
            if (this.pathTouchesSafeZone(from.position(), segmentEnd)) {
                return TargetingValidation.invalid();
            }
        }


        if (this.binoMode && !binocularTargetsSable(serverLevel)) {
            this.lastAsyncValidationReason = "accepted_world_bino";
            return TargetingValidation.accepted(result);
        }

        boolean cbcSweep = model.cbcPhysics() && !model.usesCustomDynamics();
        ObstructionResult obstruction = this.mainThreadObstructionChecker.check(serverLevel, trajectory, pathEndTick, segmentImpact, cbcSweep);

        if (obstruction.clear() || this.isValidatedObstructionAtTarget(snapshot, obstruction)) {
            this.lastAsyncValidationReason = "accepted_path_clear";
            return TargetingValidation.accepted(result);
        }

        //LOGGER.debug("[RADAR-TRACE][LIVE-OBSTRUCTION] " + "tick={} mount={} highRequested={} " + "pitch={} flightTicks={} " + "blockedTick={} blockedBlock={} blockedPosition={} " + "muzzle={} target={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), snapshot.preferHighArc(), result.desiredPitchDeg(), result.predictedFlightTicks(), obstruction.blockedTick(), obstruction.blockPosition(), obstruction.blockedPosition(), snapshot.muzzlePosition(), snapshot.targetPosition());

        this.lastAsyncValidationReason = "blocked_path";
        return TargetingValidation.blockedPath();
    }

    private TargetingValidation rejectAsyncValidation(String reason) {
        this.lastAsyncValidationReason = reason;
        return TargetingValidation.invalid();
    }

    private boolean isValidatedObstructionAtTarget(TargetingSnapshot snapshot, ObstructionResult obstruction) {
        if (!obstruction.blocked() || obstruction.blockedPosition() == null) {
            return false;
        }

        UUID obstructingShip = obstruction.obstructingSublevelId();

        if (obstructingShip != null && !obstructingShip.equals(snapshot.targetSublevelId())) {
            return false;
        }

        int blockedTick = Math.max(0, obstruction.blockedTick());
        Vec3 targetAtBlock = predictSnapshotTarget(snapshot, blockedTick);
        AABB targetAabb = snapshot.targetAabb() == null ? null : snapshot.targetAabb().move(targetAtBlock.subtract(snapshot.targetPosition()));
        double distance = targetAabb == null ? obstruction.blockedPosition().distanceTo(targetAtBlock) : TargetingMath.distancePointToAabb(obstruction.blockedPosition(), targetAabb);
        if (distance <= this.directTargetHitTolerance(snapshot)) {
            return true;
        }

        if (Mods.SABLE.isLoaded() && snapshot.targetSublevelId() != null && obstruction.blockPosition() != null) {
            SubLevelAccess hitSublevel = SableCompanion.INSTANCE.getContaining(snapshot.level(), obstruction.blockPosition());
            return hitSublevel != null && snapshot.targetSublevelId().equals(hitSublevel.getUniqueId());
        }

        return false;
    }

    private double directTargetHitTolerance(TargetingSnapshot snapshot) {
        double speedMargin = snapshot.targetVelocity().length() * (double)2.0F;
        if (snapshot.targetAabb() == null) {
            return Math.max((double)0.75F, Math.min((double)2.5F, (double)0.75F + speedMargin));
        }

        AABB box = snapshot.targetAabb();
        double sizeMargin = Math.max((double)0.25F, Math.min((double)1.5F, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * (double)0.25F));
        return Math.max((double)0.35F, Math.min((double)3.0F, sizeMargin + speedMargin));
    }

    @Nullable
    private TargetingResult solveWithTargetingComputer(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, Vec3 rawTargetPos, Vec3 targetWorldPos, Vec3 guidancePositionOffset, Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel, @Nullable Entity targetEntity, @Nullable SubLevelAccess targetSublevel, TargetMotionClass targetMotionClass, int trackingLeadTicks) {
        if (serverLevel == null || cannonContraption == null || targetWorldPos == null) {
            return null;
        }

        AimGeometry aimGeometry = this.resolveAimGeometry(cannonContraption);
        Vec3 rawMuzzlePos = aimGeometry.steeringOrigin();
        Vec3 muzzleWorldPos = aimGeometry.muzzlePosition();
        ResolvedProjectileState projectile = this.resolveProjectileState(cannonContraption, serverLevel);

        if (muzzleWorldPos == null || projectile == null) {
            return null;
        }

        ProjectileModel model = projectile.model();
        Vec3 safeTargetVel = finiteOrZero(targetVel);
        Vec3 safeTargetAccel = clampAcceleration(finiteOrZero(targetAccel));
        Vec3 safeShooterVel = finiteOrZero(shooterVel);
        Vec3 effectiveTargetAccel = TargetingMotion.effectiveAcceleration(safeTargetAccel, targetMotionClass);
        boolean movingSableMount = this.isSableMount();
        Vec3 pivotVelocity = movingSableMount && aimGeometry.coupled() ? this.getPlatformVelocityAtMuzzle(serverLevel, this.cannonMount, aimGeometry.steeringOrigin()) : Vec3.ZERO;
        RigidBodyLaunchMath.Motion sableMotion = null;

        if (movingSableMount && this.cannonMount != null) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, this.cannonMount.getBlockPos());
            sableMotion = SableLaunchMotion.capture(serverLevel, mountShip);
        }

        boolean inheritLaunchVelocity = inheritsCbcSableLaunchVelocity(this.cannonMount, cannonContraption);

        if (this.shouldLogTargetingDebug(serverLevel)) {
            this.logTargetingCoordinateResolution(serverLevel, rawTargetPos, targetWorldPos, rawMuzzlePos, muzzleWorldPos, safeShooterVel);
            LOGGER.debug("WFC projectile state: speed={} gravity={} drag={} quadratic={} cbcPhysics={} lifetimeCap={} reason={}",
                    model.muzzleSpeed(), model.gravity(), model.drag(), model.quadraticDrag(), model.cbcPhysics(),
                    projectile.lifetimeCapTicks(), projectile.reason());
        }

        AABB baseTargetAabb = targetEntity != null
                ? this.resolveEntityWorldAabb(serverLevel, targetEntity)
                : this.resolveSublevelWorldAabb(targetSublevel);

        baseTargetAabb = translateTargetAabb(baseTargetAabb, guidancePositionOffset, Vec3.ZERO);
        RigidBodyTargetMotion sampledTargetMotion = this.captureRigidBodyTargetMotion(serverLevel, targetSublevel, rawTargetPos, targetWorldPos, safeTargetVel, guidancePositionOffset, 0);
        int predictedTicks = this.chooseReachableLaunchDelayTicks(serverLevel, cannonContraption, model, muzzleWorldPos, safeShooterVel, sableMotion, targetWorldPos, safeTargetVel, effectiveTargetAccel, sampledTargetMotion, trackingLeadTicks, null);
        TargetingResult result = null;
        TargetingComputer computer = this.targetingComputerFor(projectile.solverKind(), false);
        this.lastSyncBallisticReachableForFire = false;

        for (int i = 0; i < 2; ++i) {
            RigidBodyTargetMotion rigidTarget = sampledTargetMotion == null
                    ? null
                    : new RigidBodyTargetMotion(sampledTargetMotion.motion(), sampledTargetMotion.observedWorldPoint(), predictedTicks);
            Vec3 delayedTargetPos = rigidTarget != null
                    ? rigidTarget.launchPosition()
                    : TargetingMotion.futureTargetPosition(targetWorldPos, safeTargetVel, effectiveTargetAccel, predictedTicks, TargetingMotion.TARGET_ACCEL_HOLD_TICKS);
            Vec3 delayedTargetVel = rigidTarget != null
                    ? rigidTarget.launchVelocity()
                    : TargetingMotion.futureTargetVelocity(safeTargetVel, effectiveTargetAccel, predictedTicks, TargetingMotion.TARGET_ACCEL_HOLD_TICKS);

            Vec3 predictedMuzzlePos = sableMotion != null
                    ? sableMotion.futurePosition(
                    muzzleWorldPos,
                    predictedTicks
            )
                    : movingSableMount
                    ? TargetingMotion.futurePosition(
                    muzzleWorldPos,
                    safeShooterVel,
                    Vec3.ZERO,
                    predictedTicks
            )
                    : muzzleWorldPos;

            Vec3 predictedPivotPos = !aimGeometry.coupled() ? null : sableMotion != null ? sableMotion.futurePosition(aimGeometry.steeringOrigin(), predictedTicks)
                    : movingSableMount ? TargetingMotion.futurePosition(aimGeometry.steeringOrigin(), pivotVelocity, Vec3.ZERO, predictedTicks) : aimGeometry.steeringOrigin();

            Vec3 predictedShooterVel = sableMotion != null ? sableMotion.futureVelocity(muzzleWorldPos, predictedTicks) : safeShooterVel;

            AABB targetAabb = rigidTarget != null ? null : translateTargetAabb(baseTargetAabb, Vec3.ZERO, delayedTargetPos.subtract(targetWorldPos));
            TargetingSnapshot snapshot =
                    TargetingSnapshot.builder(serverLevel)
                            .muzzlePosition(predictedMuzzlePos)
                            .swivelLaunchGeometry(resolveSwivelLaunchGeometry(this.cannonMount, muzzleWorldPos, sableMotion, predictedTicks))
                            .launchPivotPosition(predictedPivotPos)
                            .muzzleForwardOffset(aimGeometry.muzzleForwardOffset())
                            .inheritedVelocity(inheritLaunchVelocity ? predictedShooterVel : Vec3.ZERO)
                            .launchAngularVelocity(inheritLaunchVelocity && sableMotion != null && aimGeometry.coupled() ? sableMotion.angularVelocityAt(predictedTicks) : Vec3.ZERO)
                            .targetPosition(delayedTargetPos)
                            .targetVelocity(delayedTargetVel)
                            .targetAcceleration(rigidTarget != null ? Vec3.ZERO : safeTargetAccel)
                            .targetAccelerationRemainingTicks(rigidTarget != null ? 0 : Math.max(0, TargetingMotion.TARGET_ACCEL_HOLD_TICKS - predictedTicks))
                            .targetAabb(targetAabb)
                            .rigidBodyTargetMotion(rigidTarget)
                            .projectileSpeed(model.muzzleSpeed())
                            .gravity(model.gravity())
                            .drag(model.drag())
                            .quadraticDrag(model.quadraticDrag())
                            .cbcPhysics(model.cbcPhysics())
                            .dragDensity(model.dragDensity())
                            .maxFlightTicks(this.solverFlightTicks(projectile, predictedMuzzlePos, delayedTargetPos))
                            .gameTime(serverLevel.getGameTime() + predictedTicks)
                            .preferredYawDeg(this.preferredYaw(null))
                            .preferredPitchDeg(this.preferredPitch(null))
                            .currentYawDeg(this.currentSolverYawDeg())
                            .currentPitchDeg(this.currentPitchDeg(cannonContraption))
                            .targetSublevelId(targetSublevel != null ? targetSublevel.getUniqueId() : null)
                            .targetMotionClass(targetMotionClass)
                            .pitchConstraint(this.effectivePitchConstraint(cannonContraption))
                            .preferHighArc(this.targetingConfig.preferHighArc())
                            .build();
            //LOGGER.debug("[RADAR-TRACE][SYNC-SNAPSHOT] " + "tick={} mount={} family={} ammo={} " + "solver={} highArc={} " + "lifetimeCap={} maxFlight={} " + "leadTicks={} " + "speed={} gravity={} drag={} " + "cbcPhysics={} " + "muzzle={} inheritedVelocity={} " + "target={} targetVelocity={} " + "targetAcceleration={} targetShip={} " + "rigidTarget={} pitchLimits={}", serverLevel.getGameTime(), this.cannonMount.getBlockPos(), projectile.family(), projectile.ammunition(), projectile.solverKind(), snapshot.preferHighArc(), projectile.lifetimeCapTicks(), snapshot.maxFlightTicks(), predictedTicks, model.muzzleSpeed(), model.gravity(), model.drag(), model.cbcPhysics(), snapshot.muzzlePosition(), snapshot.inheritedVelocity(), snapshot.targetPosition(), snapshot.targetVelocity(), snapshot.targetAcceleration(), snapshot.targetSublevelId(), rigidTarget != null, snapshot.pitchConstraint().summary());
            result = computer.solve(snapshot, model);

            if (result == null || !result.valid()
                    || !result.hasShot()
                    || result.aimSolution() == null) {
                break;
            }

            int actualSlewTicks = estimateSlewTicksForSolution(cannonContraption, result.aimSolution(), result.desiredYawDeg() + 270.0, result.desiredPitchDeg());
            int availableSlewTicks = Math.max(0, predictedTicks - trackingLeadTicks);
            this.lastSyncBallisticReachableForFire = actualSlewTicks <= availableSlewTicks;
            int correctedDelay = LaunchTimeFeasibility.refinedDelay(predictedTicks, trackingLeadTicks, actualSlewTicks, 60);
            boolean retry = i == 0 && !lastSyncBallisticReachableForFire && correctedDelay > predictedTicks;

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "targeting.ballistic_refinement_decision",
                        serverLevel,
                        getWeaponMountPos(),
                        "mode", "sync",
                        "iteration", i,
                        "plannedDelayTicks", predictedTicks,
                        "trackingLeadTicks", trackingLeadTicks,
                        "actualSlewTicks", actualSlewTicks,
                        "availableSlewTicks", availableSlewTicks,
                        "reachable", lastSyncBallisticReachableForFire,
                        "correctedDelayTicks", correctedDelay,
                        "retry", retry,
                        "actualFlightTicks", result.predictedFlightTicks()
                );
            }

            if (retry) {
                predictedTicks = correctedDelay;
                continue;
            }

            break;
        }

        if (this.level.getGameTime() % 20L == 1L && result != null) {
            LOGGER.debug("WFC TargetingComputer trackingLead={} {}", predictedTicks, result.debugString());
        }
        return result;
    }

    @Nullable
    private Vec3 resolveMuzzleWorldPosition(AbstractMountedCannonContraption cannon) {
        return resolveMuzzleWorldPosition(this.cannonMount, cannon);
    }

    @Nullable
    private Vec3 resolveMuzzleWorldPosition(CannonMountContext mount, AbstractMountedCannonContraption cannon) {
        if (this.level instanceof ServerLevel serverLevel) {
            WeaponShotProfile adapterShot = this.resolveAdapterShot(mount, cannon, serverLevel);
            if (adapterShot != null && finite(adapterShot.muzzlePosition())) {
                return adapterShot.muzzlePosition();
            }
        }
        if ((CannonUtil.isBigCannon(cannon) || CannonUtil.isAutocannonFamily(cannon)
                || CannonUtil.isCBCMSCannon(cannon))
                && mount != null && mount.getContraption() != null) {
            Vec3 anchor = CBCMuzzleUtil.getCBCSpawnAnchorWorld(mount.getContraption());
            if (this.isSableMount(mount)) {
                SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(this.level, mount.getBlockPos());
                return SableUtils.getWorldVec(anchor, mountShip);
            }
            return anchor;
        }
        return this.getCannonRayStart(mount);
    }

    @Nullable
    private SwivelLaunchGeometry resolveSwivelLaunchGeometry(@Nullable CannonMountContext mount, Vec3 muzzleWorld, @Nullable RigidBodyLaunchMath.Motion motion, int predictedTicks) {
        if (!Mods.SIMULATED.isLoaded() || !(this.level instanceof ServerLevel serverLevel) || mount == null || mount.getContraption() == null || !finite(muzzleWorld)) {
            return null;
        }

        BlockPos mountPos = mount.getBlockPos();
        AutoYawControllerBlockEntity yaw = Objects.equals(getWeaponMountPos(), mountPos) ? this.yawController : null;

        boolean structuralYaw = yaw != null && yaw.hasStructuralKineticSelectionForTargeting();
        boolean structuralPitch = usesStructuralPitchForMount(mountPos);

        if (!structuralYaw && !structuralPitch) {
            return null;
        }

        SimulatedSwivelMountAdapter.RotationGeometry yawRotation = null;
        if (structuralYaw) {
            if (!SimulatedSwivelMountAdapter.isMountOnControllerSwivel(yaw, Direction.Axis.Y, mountPos)) {
                return null;
            }

            var resolution = SimulatedSwivelMountAdapter.resolve(yaw, Direction.Axis.Y);
            if (!(resolution.adapter() instanceof SimulatedSwivelMountAdapter swivel)) {
                return null;
            }

            yawRotation = swivel.rotationGeometryWorld();

            if (yawRotation == null) {
                return null;
            }
        }

        SimulatedSwivelMountAdapter.RotationGeometry pitchRotation = null;

        if (structuralPitch) {
            if (pitchController != null && pitchController.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) {
                Direction.Axis pitchAxis = pitchController.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getAxis();

                if (SimulatedSwivelMountAdapter.isMountOnControllerSwivel(pitchController, pitchAxis, mountPos)) {
                    var resolution = SimulatedSwivelMountAdapter.resolve(pitchController, pitchAxis);
                    if (resolution.adapter() instanceof SimulatedSwivelMountAdapter swivel) {
                        pitchRotation = swivel.rotationGeometryWorld();
                    }
                }
            }

            if (pitchRotation == null) {
                return null;
            }
        }

        if (yawRotation != null && pitchRotation != null && !isPitchBearingUnderYaw(serverLevel, yawRotation, pitchRotation)) {
            return null;
        }

        Vec3 forward = CBCMuzzleUtil.getForwardWorld(mount.getContraption());
        SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, mountPos);
        forward = SableUtils.getWorldVecDirectionTransform(forward, mountShip);

        if (!finite(forward) || forward.lengthSqr() < 1.0e-12) {
            return null;
        }

        if (motion != null && predictedTicks > 0) {
            Vec3 futureMuzzle = motion.futurePosition(muzzleWorld, predictedTicks);
            Vec3 futureForwardTip = motion.futurePosition(muzzleWorld.add(forward), predictedTicks);
            forward = futureForwardTip.subtract(futureMuzzle);
        }

        if (!finite(forward) || forward.lengthSqr() < 1.0e-12) {
            return null;
        }

        if (yawRotation != null) {
            yawRotation = predictSwivelRotation(serverLevel, yawRotation, predictedTicks);

            if (yawRotation == null) {
                return null;
            }
        }

        if (pitchRotation != null) {
            pitchRotation = predictSwivelRotation(serverLevel, pitchRotation, predictedTicks);

            if (pitchRotation == null) {
                return null;
            }
        }

        if (yawRotation != null && pitchRotation != null) {
            return SwivelLaunchGeometry.nested(yawRotation.pivot(), yawRotation.axis(), pitchRotation.pivot(), pitchRotation.axis(), forward);
        }

        // Yaw only.
        if (yawRotation != null) {
            return new SwivelLaunchGeometry(yawRotation.pivot(), yawRotation.axis(), forward);
        }

        return new SwivelLaunchGeometry(pitchRotation.pivot(), pitchRotation.axis(), forward);
    }

    /**
     * Verify that the pitch swivel belongs to the yaw assembly.
     */
    private static boolean isPitchBearingUnderYaw(ServerLevel level, SimulatedSwivelMountAdapter.RotationGeometry yaw, SimulatedSwivelMountAdapter.RotationGeometry pitch) {
        UUID attachedId = yaw.assemblyId();
        UUID pitchParentId = pitch.parentSublevelId();

        if (attachedId == null || pitchParentId == null) {
            return false;
        }

        if (attachedId.equals(pitchParentId)) {
            return true;
        }

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);

        if (container == null) {
            return false;
        }

        var attached = container.getSubLevel(attachedId);
        var pitchParent = container.getSubLevel(pitchParentId);

        return attached != null
                && !attached.isRemoved()
                && pitchParent != null
                && !pitchParent.isRemoved()
                && dev.ryanhcode.sable.api.SubLevelHelper
                .getConnectedChain(attached)
                .contains(pitchParent);
    }

    @Nullable
    private static SimulatedSwivelMountAdapter.RotationGeometry
    predictSwivelRotation(ServerLevel serverLevel, SimulatedSwivelMountAdapter.RotationGeometry rotation, int predictedTicks) {
        if (rotation == null || predictedTicks <= 0 || rotation.parentSublevelId() == null) {
            return rotation;
        }

        SubLevelAccess parent = SableWeaponFiringAccess.findSubLevel(serverLevel, rotation.parentSublevelId());

        if (parent == null) {
            return null;
        }

        RigidBodyLaunchMath.Motion parentMotion = SableLaunchMotion.capture(serverLevel, parent);

        if (parentMotion == null) {
            return null;
        }

        Vec3 pivot = rotation.pivot();
        Vec3 futurePivot = parentMotion.futurePosition(pivot, predictedTicks);
        Vec3 futureAxis = parentMotion.futurePosition(pivot.add(rotation.axis()), predictedTicks).subtract(futurePivot);

        if (!finite(futurePivot) || !finite(futureAxis) || futureAxis.lengthSqr() < 1.0e-12) {
            return null;
        }

        return new SimulatedSwivelMountAdapter.RotationGeometry(futurePivot, futureAxis.normalize(), rotation.parentSublevelId(), rotation.assemblyId());
    }

    private AimGeometry resolveAimGeometry(AbstractMountedCannonContraption cannon) {
        return resolveAimGeometry(this.cannonMount, cannon);
    }

    private AimGeometry resolveAimGeometry(CannonMountContext mount, AbstractMountedCannonContraption cannon) {
        Vec3 muzzle = this.resolveMuzzleWorldPosition(mount, cannon);
        Vec3 pivot = this.getCannonRayStart(mount);
        if (!finite(muzzle)) {
            muzzle = finite(pivot) ? pivot : Vec3.ZERO;
        }

        boolean builtInCannon = CannonUtil.isBigCannon(cannon) || CannonUtil.isAutocannonFamily(cannon) || CannonUtil.isCBCMSCannon(cannon);
        boolean adapterOwned = false;
        if (this.level instanceof ServerLevel serverLevel) {
            adapterOwned = this.resolveAdapterShot(mount, cannon, serverLevel) != null;
        }

        if (!adapterOwned && builtInCannon && finite(pivot)) {
            double offset = pivot.distanceTo(muzzle);
            if (Double.isFinite(offset) && offset > 1.0E-6) {
                return new AimGeometry(pivot, muzzle, offset, true);
            }
        }
        return new AimGeometry(muzzle, muzzle, 0.0, false);
    }

    private MountAimFrame unavailableMountAimFrame(String reason) {
        return new MountAimFrame(
                MountFrameKind.UNAVAILABLE,
                null,
                PitchConstraint.intersect(
                        PitchConstraint.SOLVER_MIN_PITCH_DEG,
                        PitchConstraint.SOLVER_MAX_PITCH_DEG,
                        PitchConstraint.SOLVER_MIN_PITCH_DEG,
                        PitchConstraint.SOLVER_MAX_PITCH_DEG,
                        new Vec3(1.0, 0.0, 0.0),
                        new Vec3(0.0, 1.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0)
                ),
                ControllerMovementLimits.defaults(CannonAxis.YAW),
                0.0,
                reason,
                null
        );
    }

    @Nullable
    private Vec3 resolveSteeringOrigin(AbstractMountedCannonContraption cannon) {
        return cannon == null ? this.getCannonRayStart() : this.resolveAimGeometry(cannon).steeringOrigin();
    }

    public boolean canYawAimAtTarget(@Nullable Vec3 targetWorld) {
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;

        Vec3 direction = directionFromTo(this.resolveSteeringOrigin(cannon), targetWorld);
        AimCommandEvaluation evaluation = this.resolveMountAimFrame(cannon).evaluate(direction);
        return evaluation.valid() && !evaluation.yawConstrained();
    }

    public boolean usesSublevelAimFrame() {
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;
        MountAimFrame frame = this.resolveMountAimFrame(cannon);
        return frame.kind() == MountFrameKind.SUBLEVEL || frame.sourceSublevelId() != null;
    }

    public boolean canApplyMountCommand(double pitchDeg, double controllerYawDeg) {
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;
        MountAimFrame frame = this.resolveMountAimFrame(cannon);
        Vec3 worldDirection = frame.worldDirection(pitchDeg, controllerYawDeg);
        AimCommandEvaluation evaluation = frame.evaluate(worldDirection);
        return evaluation.fireEligible();
    }

    public SolverDebugReport buildSolverDebugReport(ServerLevel serverLevel, int arcTicks) {
        List<String> lines = new ArrayList<>();
        ProjectileSimulator.SimulationResult trajectory = null;
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;
        Vec3 fallbackOrigin = this.getCannonRayStart();
        if (!finite(fallbackOrigin)) {
            fallbackOrigin = Vec3.ZERO;
        }
        AimGeometry aimGeometry = cannon == null
                ? new AimGeometry(fallbackOrigin, fallbackOrigin, 0.0, false)
                : this.resolveAimGeometry(cannon);
        Vec3 rawMuzzlePos = aimGeometry.steeringOrigin();
        Vec3 muzzleWorldPos = aimGeometry.muzzlePosition();
        Vec3 targetPos = this.target;
        Vec3 aimPoint = this.lastAimPoint;
        TargetingResult result = this.cachedTargetingResult;

        lines.add("=== Create Radar Cannon Solver Debug ===");
        lines.add("mount=" + (this.cannonMount == null ? "<null>" : this.cannonMount.getBlockPos().toShortString())
                + " dim=" + (serverLevel == null ? "<null>" : serverLevel.dimension().location()));
        lines.add("detected cannon type=" + describeCannonType(cannon));
        lines.add("raw mount ray origin=" + fmtVec(rawMuzzlePos) + " resolved muzzle=" + fmtVec(muzzleWorldPos));
        lines.add("aim geometry coupled="
                + aimGeometry.coupled()
                + " muzzleForwardOffset="
                + fmt(aimGeometry.muzzleForwardOffset()));
        long pendingAge = this.pendingTargetingRequest == null
                || serverLevel == null ? -1L
                : serverLevel.getGameTime() - this.pendingTargetingRequest.requestTick();
        long cachedAge = this.cachedTargetingRequestTick == Long.MIN_VALUE
                || serverLevel == null ? -1L
                : serverLevel.getGameTime() - this.cachedTargetingRequestTick;
        lines.add("async pending="
                + (this.pendingTargetingFuture != null
                && !this.pendingTargetingFuture.isDone())
                + " pendingAge=" + pendingAge
                + " cachedAge=" + cachedAge
                + " submitted=" + this.asyncSolveSubmitted
                + " completed=" + this.asyncSolveCompleted
                + " superseded=" + this.asyncSolveSuperseded
                + " timedOut=" + this.asyncSolveTimedOut
                + " rejected=" + this.asyncSolveRejected);
        lines.add("async steeringProvisional="
                + this.lastTargetingSteeringProvisional
                + " fireFresh=" + this.lastTargetingFireFresh
                + " freshnessReason="
                + this.lastTargetingFreshnessReason);
        MountAimFrame mountFrame = cannon == null ? null
                : this.resolveMountAimFrame(cannon);
        PitchConstraint effectivePitch = mountFrame == null
                ? PitchConstraint.unconstrained()
                : mountFrame.pitchConstraint();
        lines.add("effective pitch limits=" + effectivePitch.summary());
        if (mountFrame != null) {
            lines.add("mount aim frame=" + mountFrame.kind()
                    + " sourceSublevel=" + mountFrame.sourceSublevelId()
                    + " yawNeutral=" + fmt(mountFrame.yawNeutralDeg())
                    + " yawLimits=[" + fmt(mountFrame.yawLimits().minDegrees())
                    + "," + fmt(mountFrame.yawLimits().maxDegrees()) + "]"
                    + " unavailableReason=" + mountFrame.unavailableReason());
        }
        if (this.lastAimCommandEvaluation != null) {
            AimCommandEvaluation evaluation = this.lastAimCommandEvaluation;
            lines.add("mount aim evaluation reason=" + evaluation.reason()
                    + " valid=" + evaluation.valid()
                    + " constrained=" + evaluation.constrained()
                    + " requestedPitch=" + fmt(evaluation.requestedPitchDeg())
                    + " appliedPitch=" + fmt(evaluation.appliedPitchDeg())
                    + " requestedYaw=" + fmt(evaluation.requestedControllerYawDeg())
                    + " appliedYaw=" + fmt(evaluation.appliedControllerYawDeg()));
        }
        lines.add("target=" + fmtVec(targetPos)
                + " targetDistanceFromMount=" + fmt(rawMuzzlePos != null && targetPos != null ? rawMuzzlePos.distanceTo(targetPos) : Double.NaN)
                + " targetDistanceFromMuzzle=" + fmt(muzzleWorldPos != null && targetPos != null ? muzzleWorldPos.distanceTo(targetPos) : Double.NaN));
        lines.add("current aim point=" + fmtVec(aimPoint)
                + " aimDistanceFromMuzzle=" + fmt(muzzleWorldPos != null && aimPoint != null ? muzzleWorldPos.distanceTo(aimPoint) : Double.NaN));

        double projectileSpeed = Double.NaN;
        double gravity = Double.NaN;
        double drag = Double.NaN;
        double dragDensity = 1.0;
        boolean quadraticDrag = false;
        boolean cbcPhysics = cannon != null && CannonUtil.isBigCannon(cannon);
        ProjectileModel debugModel = null;
        String shellType = "<unknown>";
        String propellant = "<unknown>";
        String ballisticsReason = "<none>";

        if (cannon != null && serverLevel != null) {
            ResolvedProjectileState projectile = this.resolveProjectileState(cannon, serverLevel);
            CannonUtil.BigCannonShotState bigShot = CannonUtil.isBigCannon(cannon) ? CannonUtil.resolveBigCannonShotState(cannon, serverLevel) : null;
            if (projectile != null) {
                debugModel = projectile.model();
                projectileSpeed = debugModel.muzzleSpeed();
                gravity = debugModel.gravity();
                drag = debugModel.drag();
                quadraticDrag = debugModel.quadraticDrag();
                cbcPhysics = debugModel.cbcPhysics();
                dragDensity = debugModel.dragDensity();
                ballisticsReason = projectile.reason();
            }

            if (bigShot != null) {
                shellType = bigShot.projectileClass() == null ? "<none detected; using HE fallback ballistics>" : bigShot.projectileClass();
                propellant = bigShot.propellantCharges() + " charges, propellantPower=" + fmt(bigShot.propellantPower())
                        + ", projectileAddedPower=" + fmt(bigShot.projectileAddedPower());
                ballisticsReason = bigShot.reason();
            } else if (projectile != null) {
                shellType = projectile.ammunition() + " (" + projectile.family() + ")";
                propellant = projectile.fuelTicks() > 0 ? "fuelTicks=" + projectile.fuelTicks() : "n/a";
            } else {
                shellType = CannonUtil.isAutocannonFamily(cannon) ? "<autocannon ammo>" : "<unknown non-big-cannon>";
                propellant = "n/a";
            }
        }

        lines.add("detected shell type=" + shellType);
        lines.add("detected ballistic properties: gravity=" + fmt(gravity)
                + " drag=" + fmt(drag)
                + " quadraticDrag=" + quadraticDrag
                + " cbcPhysics=" + cbcPhysics
                + " dragDensity=" + fmt(dragDensity)
                + " reason=" + ballisticsReason);
        lines.add("propellant=" + propellant);
        lines.add("exit muzzle velocity=" + fmt(projectileSpeed) + " blocks/tick, " + fmt(projectileSpeed * 20.0) + " m/s");

        Double expectedPitch = result != null && result.valid() && result.hasShot() ? result.desiredPitchDeg() : null;
        Double expectedYaw = result != null && result.valid() && result.hasShot() ? result.desiredYawDeg() : null;
        if (expectedPitch == null && this.pitchController != null) {
            expectedPitch = this.pitchController.getTargetAngle();
        }
        if (expectedYaw == null && this.yawController != null) {
            expectedYaw = this.yawController.getTargetAngle() - 270.0;
        }
        lines.add("expected pitch=" + fmt(expectedPitch == null ? Double.NaN : expectedPitch) + " expected solver yaw=" + fmt(expectedYaw == null ? Double.NaN : expectedYaw) + " controller yaw=" + (this.yawController == null ? "<none>" : fmt(this.yawController.getTargetAngle())) + " controller pitch=" + (this.pitchController == null ? "<none>" : fmt(this.pitchController.getTargetAngle())));

        if (result != null) {
            lines.add("cached solver result: valid=" + result.valid() + " hasShot=" + result.hasShot() + " flightTicks=" + result.predictedFlightTicks() + " miss=" + fmt(result.missDistance()) + " confidence=" + fmt(result.confidence()) + " reason=" + result.reason());
            if (result.debugInfo() != null && !result.debugInfo().isEmpty()) {
                lines.add("cached solver debug=" + result.debugInfo());
            }
        } else {
            lines.add("cached solver result=<none>");
        }

        Vec3 direction = result != null && result.aimSolution() != null ? result.aimSolution().aimDirection() : null;
        if ((direction == null || direction.lengthSqr() < 1.0E-12) && expectedYaw != null && expectedPitch != null) {
            direction = TargetingMath.directionFromYawPitch(expectedYaw, expectedPitch);
        }
        if (serverLevel != null && muzzleWorldPos != null && direction != null && direction.lengthSqr() >= 1.0E-12
                && Double.isFinite(projectileSpeed) && projectileSpeed > 0.0
                && Double.isFinite(gravity) && Double.isFinite(drag)) {
            ProjectileModel model = debugModel != null ? debugModel : (cbcPhysics
                    ? ProjectileModel.cbc(projectileSpeed, gravity, drag, dragDensity, quadraticDrag)
                    : ProjectileModel.simple(projectileSpeed, gravity, drag, quadraticDrag));
            int ticks = Math.max(20, Math.min(400, arcTicks));
            Vec3 launchPosition = aimGeometry.launchPosition(direction);
            trajectory = this.validationProjectileSimulator.simulate(launchPosition, direction, Vec3.ZERO, model, ticks, serverLevel);
            lines.add("arc samples=" + trajectory.samples().size() + " ticks=" + ticks);
            lines.add("prospective launch=" + fmtVec(launchPosition));
        } else {
            lines.add("arc unavailable: missing muzzle/direction/valid projectile model");
        }

        String joined = String.join(" | ", lines);
        LOGGER.debug(joined);
        return new SolverDebugReport(lines, trajectory);
    }

    private static String describeCannonType(@Nullable AbstractMountedCannonContraption cannon) {
        if (cannon == null) {
            return "<none>";
        }
        String family = CannonUtil.isBigCannon(cannon) ? "big_cannon"
                : CannonUtil.isRocketPod(cannon) ? "cbc_at_rocket_pod"
                : CannonUtil.isMediumRocketRail(cannon) ? "cbc_at_medium_rocket_rail"
                : CannonUtil.isHeavyAutocannon(cannon) ? "cbc_at_heavy_autocannon"
                : CannonUtil.isTwinAutocannon(cannon) ? "cbc_at_twin_autocannon"
                : CannonUtil.isMediumCannon(cannon) ? "cbcmw_medium"
                : CannonUtil.isRotaryCannon(cannon) ? "cbcmw_rotary"
                : CannonUtil.isAutocannonFamily(cannon) ? "autocannon"
                : CannonUtil.isEnergyCannon(cannon) ? "energy"
                : CannonUtil.isLaserCannon(cannon) ? "laser"
                : "unknown";
        return family + " (" + cannon.getClass().getName() + ")";
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.5f", value) : "<nan>";
    }

    private static String fmtVec(@Nullable Vec3 vec) {
        if (vec == null) {
            return "<null>";
        }
        return "(" + fmt(vec.x) + ", " + fmt(vec.y) + ", " + fmt(vec.z) + ")";
    }

    private Double calculateControllerYaw(Vec3 origin, Vec3 aimPoint) {
        return calculateControllerYawForFrame(
                origin, aimPoint,
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, 1.0));
    }

    @Nullable
    private Double calculateControllerYaw(CannonMountContext mount, Vec3 aimPoint) {
        Vec3 origin = getCannonRayStart(mount);
        Vec3 right = new Vec3(1.0, 0.0, 0.0);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 forward = new Vec3(0.0, 0.0, 1.0);

        if (Mods.SABLE.isLoaded()) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(this.level, mount.getBlockPos());
            if (mountShip != null) {
                right = SableUtils.getWorldVecDirectionTransform(right, mountShip);
                up = SableUtils.getWorldVecDirectionTransform(up, mountShip);
                forward = SableUtils.getWorldVecDirectionTransform(forward, mountShip);
            }
        }
        return calculateControllerYawForFrame(origin, aimPoint, right, up, forward);
    }

    @Nullable
    static Double calculateControllerYawForFrame(
            @Nullable Vec3 origin,
            @Nullable Vec3 aimPoint,
            Vec3 right,
            Vec3 up,
            Vec3 forward
    ) {
        if (origin == null || aimPoint == null) {
            return null;
        }

        Vec3 direction = aimPoint.subtract(origin);
        if (direction.lengthSqr() < 1.0e-12) {
            return null;
        }

        PitchConstraint frame = new PitchConstraint(
                PitchConstraint.SOLVER_MIN_PITCH_DEG,
                PitchConstraint.SOLVER_MAX_PITCH_DEG,
                right, up, forward);

        double controllerYaw = wrap360(frame.mountYawPitch(direction).yawDeg() + 270.0);
        return controllerYaw < 0.02 || controllerYaw > 359.98 ? (double)0.0F : controllerYaw;
    }

    private boolean binocularTargetsSable(ServerLevel serverLevel) {
        return this.binoMode && this.binoTargetPos != null && Mods.SABLE.isLoaded() && SableCompanion.INSTANCE.getContaining(serverLevel, this.binoTargetPos) != null;
    }

    private boolean pathTouchesSafeZone(Vec3 start, Vec3 end) {
        return pathTouchesSafeZone(this.safeZones, this.level, start, end);
    }

    static boolean pathTouchesSafeZone(
            @Nullable List<SafeZone> safeZones,
            @Nullable Level level,
            Vec3 start,
            Vec3 end) {
        if (safeZones == null) {
            return false;
        }

        for (SafeZone zone : safeZones) {
            if (zone != null && zone.intersects(level, start, end)) {
                return true;
            }
        }
        return false;
    }

    private Vec3 toWorldPosition(ServerLevel serverLevel, Vec3 rawPosition, @Nullable Entity entity) {
        if (rawPosition == null) {
            return Vec3.ZERO;
        } else {
            return entity != null && entity.level() == serverLevel ? rawPosition : rawPosition;
        }
    }

    private Vec3 toWorldPosition(ServerLevel serverLevel, BlockPos rawBlockPos) {
        if (rawBlockPos == null) {
            return Vec3.ZERO;
        } else {
            return Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(serverLevel, rawBlockPos) ? SableUtils.getWorldVec(serverLevel, rawBlockPos) : rawBlockPos.getCenter();
        }
    }

    private Vec3 toWorldPosition(ServerLevel serverLevel, Vec3 rawSublevelPosition, @Nullable SubLevelAccess subLevel) {
        return rawSublevelPosition == null ? Vec3.ZERO : rawSublevelPosition;
    }

    private Vec3 toWorldVelocity(ServerLevel serverLevel, @Nullable SubLevelAccess subLevel, Vec3 worldSamplePosition) {
        return Mods.SABLE.isLoaded() && subLevel != null && worldSamplePosition != null ? VS2ShipVelocityTracker.getShipVelocityPerTick(subLevel, serverLevel, worldSamplePosition) : Vec3.ZERO;
    }

    private Vec3 getSublevelTargetVelocityPerTick(
            SubLevelAccess subLevel, Vec3 targetWorldPosition,
            long gameTime, Vec3 apiVelocity) {
        Vec3 fallbackVelocity = this.estimateSublevelTargetVelocityFromPosition(subLevel, targetWorldPosition, gameTime);
        return apiVelocity.lengthSqr() > 1.0E-8 ? apiVelocity : fallbackVelocity;
    }

    private UUID sublevelMotionSampleId(UUID sublevelId) {
        String dimension = this.level == null ? "unknown" : this.level.dimension().location().toString();
        String key = sublevelId + "|" + dimension + "|" + this.cannonMount.getBlockPos().asLong();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private void invalidateSublevelAimDependentState(UUID sublevelId) {
        this.targetMotionStates.remove(sublevelId);
        if (sublevelId.equals(this.lastSableVelocityTargetId)) {
            this.lastSableVelocityTargetId = null;
            this.lastSableVelocityTargetPos = null;
            this.lastSableVelocityTargetTick = Long.MIN_VALUE;
            this.lastSableVelocityPerTick = Vec3.ZERO;
        }
        AccelerationTracker.clear(this.sublevelMotionSampleId(sublevelId));
        this.lastAimPoint = null;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.cachedSableAngles = null;
        this.cachedSableAimTarget = null;
        this.cachedSableSolveTick = -1L;
        this.cachedSableWorldAimDirection = null;
        this.clearTargetingResultCache();
    }

    private void clearSublevelAimCache() {
        if (this.cachedSublevelAim != null) {
            AccelerationTracker.clear(this.sublevelMotionSampleId(this.cachedSublevelAim.sublevelId()));
        }

        this.cachedSublevelAim = null;
        this.currentSublevelAim = null;
    }

    private Vec3 estimateSublevelTargetVelocityFromPosition(SubLevelAccess subLevel, Vec3 targetWorldPosition, long gameTime) {
        UUID id = subLevel.getUniqueId();
        if (targetWorldPosition != null && id != null) {
            Vec3 velocity = Vec3.ZERO;
            boolean sameTarget = id.equals(this.lastSableVelocityTargetId);

            if (sameTarget && this.lastSableVelocityTargetPos != null && gameTime == this.lastSableVelocityTargetTick) {
                return this.lastSableVelocityPerTick;
            }
            if (sameTarget && this.lastSableVelocityTargetPos != null
                    && this.lastSableVelocityTargetTick != Long.MIN_VALUE
                    && gameTime > this.lastSableVelocityTargetTick) {
                long dt = gameTime - this.lastSableVelocityTargetTick;

                if (dt <= TARGET_MOTION_HISTORY_MAX_GAP_TICKS) {
                    velocity = targetWorldPosition.subtract(this.lastSableVelocityTargetPos).scale(1.0 / (double)dt);
                    if (!finite(velocity) || velocity.lengthSqr() > 25.0) {
                        velocity = Vec3.ZERO;
                    }
                }
            }

            this.lastSableVelocityTargetId = id;
            this.lastSableVelocityTargetPos = targetWorldPosition;
            this.lastSableVelocityTargetTick = gameTime;
            this.lastSableVelocityPerTick = velocity;
            return velocity;
        } else {
            return Vec3.ZERO;
        }
    }

    private TargetMotionEstimate estimateTargetMotion(
            ServerLevel serverLevel, @Nullable UUID targetId,
            @Nullable Entity targetEntity, Vec3 targetWorldPosition,
            Vec3 targetVel, Vec3 targetAccel) {
        Vec3 rawVelocity = finiteOrZero(targetVel);
        Vec3 rawAcceleration = clampAcceleration(finiteOrZero(targetAccel));
        if (targetId == null || serverLevel == null) {
            return this.motionEstimate(
                    TargetMotionClass.UNKNOWN, rawVelocity,
                    rawAcceleration, 0.0, false, "no_history");
        }

        long tick = serverLevel.getGameTime();
        TargetMotionState previous = this.targetMotionStates.get(targetId);
        if (previous != null) {
            long sampleGap = tick - previous.tick;
            if (sampleGap <= 0L || sampleGap > TARGET_MOTION_HISTORY_MAX_GAP_TICKS) {
                previous = null;
            }
        }
        long dt = previous == null ? 1L : Math.max(1L, tick - previous.tick);
        Vec3 sampledPosition = targetWorldPosition == null ? Vec3.ZERO : targetWorldPosition;
        int stationarySamples = previous != null
                && sampledPosition.distanceToSqr(previous.position) <= 1.0E-8
                ? previous.stationarySamples + 1 : 1;
        if (stationarySamples >= 3) {
            rawVelocity = Vec3.ZERO;
            rawAcceleration = Vec3.ZERO;
        }
        Vec3 smoothedVelocity = previous == null ? rawVelocity : previous.smoothedVelocity.scale(0.45).add(rawVelocity.scale(0.55));
        Vec3 smoothedAcceleration = previous == null ? rawAcceleration : previous.smoothedAcceleration.scale(0.55).add(rawAcceleration.scale(0.45));
        if (stationarySamples >= 3) {
            smoothedVelocity = Vec3.ZERO;
            smoothedAcceleration = Vec3.ZERO;
        }

        Vec3 jerkVec = previous == null ? Vec3.ZERO : rawAcceleration.subtract(previous.rawAcceleration).scale((double)1.0F / (double)dt);
        double jerk = jerkVec.length();
        boolean onGround = targetEntity != null && targetEntity.onGround();
        double rawHorizontalSpeed = horizontalSpeed(rawVelocity);
        double verticalSpeed = rawVelocity.y;
        double directionChange = previous == null ? (double)0.0F : horizontalDirectionChange(previous.smoothedVelocity, rawVelocity);
        boolean sprinting = false;
        boolean fallFlying = false;
        if (targetEntity instanceof Player player) {
            sprinting = player.isSprinting();
            fallFlying = player.isFallFlying();
        }

        double sprintJumpMinSpeed = (Double)RadarConfig.server().sprintJumpMinHorizontalSpeed.get();
        double sprintJumpVerticalSpeed = (Double)RadarConfig.server().sprintJumpVerticalSpeedThreshold.get();
        boolean jumpTransition = previous != null && previous.onGround && !onGround && verticalSpeed > sprintJumpVerticalSpeed;
        boolean playerSprintJump = sprinting && jumpTransition && rawHorizontalSpeed >= sprintJumpMinSpeed;
        boolean inferredSprintJump = jumpTransition && rawHorizontalSpeed >= sprintJumpMinSpeed * (double)1.15F;
        boolean erratic = previous != null && (fallFlying || playerSprintJump || inferredSprintJump || jerk > 0.08 || directionChange > 0.55);
        boolean fastMoving = rawVelocity.length() * (double)20.0F >= (Double)RadarConfig.server().targetLoosenThreshold.get();
        TargetMotionClass motionClass = previous == null ? TargetMotionClass.UNKNOWN : (erratic ? TargetMotionClass.ERRATIC : TargetMotionClass.STEADY);
        String reason = previous == null ? "warming_up" : (fallFlying ? "fall_flying" : (playerSprintJump ? "sprint_jump" : (inferredSprintJump ? "inferred_sprint_jump" : (jerk > 0.08 ? "jerk" : (directionChange > 0.55 ? "direction_change" : (fastMoving ? "fast_moving" : "steady"))))));

        this.targetMotionStates.put(targetId, new TargetMotionState(sampledPosition,
                rawVelocity, rawAcceleration, smoothedVelocity, smoothedAcceleration,
                onGround, tick, stationarySamples));
        if (this.targetMotionStates.size() > 256) {
            this.targetMotionStates.entrySet().removeIf(entry -> tick - entry.getValue().tick > 200L);
        }

        return this.motionEstimate(motionClass, smoothedVelocity, smoothedAcceleration, jerk, fastMoving || erratic, reason);
    }

    private TargetMotionEstimate motionEstimate(
            TargetMotionClass motionClass, Vec3 velocity,
            Vec3 acceleration, double jerk, boolean looseAim,
            String reason) {
        int stableTicks = motionClass == TargetMotionClass.ERRATIC || looseAim ? Math.min(AIM_STABLE_REQUIRED, (Integer)RadarConfig.server().erraticTargetStableTicks.get()) : AIM_STABLE_REQUIRED;
        double minConfidence = NEW_SOLVER_MIN_CONFIDENCE;
        double aimStableEps = looseAim ? Math.max(AIM_STABLE_EPS, (Double)RadarConfig.server().targetLoosenThreshold.get()) : AIM_STABLE_EPS;

        return new TargetMotionEstimate(
                motionClass == null ? TargetMotionClass.UNKNOWN : motionClass,
                finiteOrZero(velocity),
                clampAcceleration(finiteOrZero(acceleration)), jerk,
                stableTicks, minConfidence, aimStableEps, looseAim, reason);
    }

    private int targetTrackingLeadTicks(TargetMotionClass motionClass) {
        int configured = (Integer)RadarConfig.server().targetTrackingLeadTicks.get();
        if (configured <= 0 || motionClass == TargetMotionClass.UNKNOWN) {
            return 0;
        }

        return motionClass == TargetMotionClass.ERRATIC ? configured : Math.max(0, configured / 2);
    }

    private int stableTargetTrackingLeadTicks(@Nullable UUID targetId, TargetMotionClass motionClass, long gameTime) {
        if (targetId == null) {
            return targetTrackingLeadTicks(motionClass);
        }

        if (!Objects.equals(trackingLeadTargetId, targetId)) {
            trackingLeadTargetId = targetId;
            erraticLeadHoldUntilTick = Long.MIN_VALUE;
        }

        if (motionClass == TargetMotionClass.ERRATIC) {
            erraticLeadHoldUntilTick = gameTime + ERRATIC_LEAD_RELEASE_HOLD_TICKS;
        }

        if (motionClass == TargetMotionClass.STEADY && gameTime <= erraticLeadHoldUntilTick) {
            return targetTrackingLeadTicks(TargetMotionClass.ERRATIC);
        }

        return targetTrackingLeadTicks(motionClass);
    }

    private static double horizontalSpeed(Vec3 vec) {
        return vec == null ? (double)0.0F : Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private static double horizontalDirectionChange(Vec3 previous, Vec3 current) {
        double prevSpeed = horizontalSpeed(previous);
        double currentSpeed = horizontalSpeed(current);
        if (prevSpeed < 1.0E-6 || currentSpeed < 1.0E-6) {
            return (double)0.0F;
        }

        double dot = previous.x * current.x + previous.z * current.z;
        double cos = Math.max((double)-1.0F, Math.min((double)1.0F, dot / (prevSpeed * currentSpeed)));
        return (double)1.0F - cos;
    }

    @Nullable
    private AABB resolveEntityWorldAabb(ServerLevel serverLevel, @Nullable Entity entity) {
        return entity != null && entity.level() == serverLevel && entity.isAlive() ? entity.getBoundingBox() : null;
    }

    private Vec3 getEntityAimPoint(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        if (bounds != null && Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ) && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)) {
            return bounds.getCenter();
        }

        return entity.position().add((double)0.0F, (double)entity.getBbHeight() * (double)0.5F, (double)0.0F);
    }

    @Nullable
    private AABB resolveSublevelWorldAabb(@Nullable SubLevelAccess subLevel) {
        if (subLevel != null && this.currentSublevelAim != null) {
            return this.currentSublevelAim.worldAabb();
        }
        return this.resolveWholeSublevelWorldAabb(subLevel);
    }

    @Nullable
    private AABB resolveWholeSublevelWorldAabb(
            @Nullable SubLevelAccess subLevel) {
        if (Mods.SABLE.isLoaded() && subLevel != null && subLevel.boundingBox() != null) {
            BoundingBox3dc box = subLevel.boundingBox();
            double minX = box.minX();
            double minY = box.minY();
            double minZ = box.minZ();
            double maxX = box.maxX();
            double maxY = box.maxY();
            double maxZ = box.maxZ();
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ) && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
        } else {
            return null;
        }
    }

    private Vec3 getPlatformVelocityAtMuzzle(ServerLevel serverLevel, @Nullable Vec3 muzzleWorldPosition) {
        return this.getPlatformVelocityAtMuzzle(
                serverLevel, this.cannonMount, muzzleWorldPosition);
    }

    private Vec3 getPlatformVelocityAtMuzzle(
            ServerLevel serverLevel,
            CannonMountContext mount,
            @Nullable Vec3 muzzleWorldPosition) {
        if (Mods.SABLE.isLoaded() && muzzleWorldPosition != null
                && SableUtils.isBlockInShipyard(serverLevel, mount.getBlockPos())) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, mount.getBlockPos());
            return mountShip == null ? Vec3.ZERO : VS2ShipVelocityTracker.getShipVelocityPerTick(mountShip, serverLevel, muzzleWorldPosition);
        } else {
            return Vec3.ZERO;
        }
    }

    private Vec3 getPlatformAcceleration(ServerLevel serverLevel, Vec3 platformVelocity) {
        if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(serverLevel, this.cannonMount.getBlockPos())) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, this.cannonMount.getBlockPos());
            return mountShip == null ? Vec3.ZERO
                    : AccelerationTracker.getAccelerationPerTick2(
                    mountShip.getUniqueId(), platformVelocity,
                    serverLevel.getGameTime());
        } else {
            return Vec3.ZERO;
        }
    }

    private void logTargetingCoordinateResolution(ServerLevel serverLevel, Vec3 rawTargetPos, Vec3 resolvedTargetPos, @Nullable Vec3 rawMuzzlePos, @Nullable Vec3 resolvedMuzzlePos, Vec3 inheritedVelocity) {
        if (RadarConfig.DEBUG_BEAMS) {
            LOGGER.debug("WFC TargetingComputer coords dim={} rawTarget={} resolvedTarget={} rawMuzzle={} resolvedMuzzle={} inheritedVel={}", new Object[]{serverLevel.dimension().location(), rawTargetPos, resolvedTargetPos, rawMuzzlePos, resolvedMuzzlePos, inheritedVelocity});
        }
    }

    private boolean shouldLogTargetingDebug(ServerLevel serverLevel) {
        if (RadarConfig.DEBUG_BEAMS && serverLevel != null) {
            long now = serverLevel.getGameTime();
            if (now - this.lastTargetingDebugLogTick < 20L) {
                return false;
            } else {
                this.lastTargetingDebugLogTick = now;
                return true;
            }
        } else {
            return false;
        }
    }

    @Nullable
    private Double currentSolverYawDeg() {
        if (this.yawController != null && this.yawController.hasStructuralKineticSelectionForTargeting()) {
            Vec3 physical = this.yawController.getStructuralPhysicalWorldDirection();
            return physical == null ? null : TargetingMath.yawPitchFromDirection(physical).yawDeg();
        }
        PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
        return contraption == null ? null : wrap360((double)contraption.yaw) - (double)270.0F;
    }

    @Nullable
    private Double currentPitchDeg(AbstractMountedCannonContraption cannonContraption) {
        if (usesStructuralPitchForAuthority()) {
            Vec3 physical = this.pitchController.getStructuralPhysicalWorldDirection();
            return physical == null ? null : TargetingMath.yawPitchFromDirection(physical).pitchDeg();
        }

        PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
        if (contraption != null && cannonContraption != null) {
            int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
            return (double)contraption.pitch * (double)(-invert);
        } else {
            return null;
        }
    }

    private boolean inheritsCbcSableLaunchVelocity(@Nullable CannonMountContext mount, @Nullable AbstractMountedCannonContraption cannon) {
        if (mount == null || cannon == null || !this.isSableMount(mount)) {
            return true;
        }


        if (!CannonUtil.isBigCannon(cannon) && !CannonUtil.isAutoCannon(cannon)) {
            return true;
        }

        return CBCConfigs.server() == null || CBCConfigs.server().compat.sableProjectilesInheritPhysicsObjectVelocity.get();
    }

    private boolean isSableMount() {
        return this.isSableMount(this.cannonMount);
    }

    private boolean isSableMount(@Nullable CannonMountContext mount) {
        return Mods.SABLE.isLoaded() && this.level != null && mount != null && PhysicsHandler.isBlockInPlotyard(this.level, mount.getBlockPos());
    }

    private void samplePhysicalYawRate() {
        if (level == null || cannonMount == null || cannonMount.getContraption() == null) {
            measuredPhysicalYawRate = 0.0;
            lastPhysicalYawSampleTick = Long.MIN_VALUE;
            return;
        }

        long tick = level.getGameTime();
        if (tick == lastPhysicalYawSampleTick) {
            return;
        }

        double current = wrap360(cannonMount.getContraption().yaw);
        double rate = 0.0;

        if (lastPhysicalYawSampleTick == tick - 1 && Double.isFinite(lastPhysicalYawSample)) {
            double delta = shortestDelta(lastPhysicalYawSample, current);

            if (Double.isFinite(delta) && Math.abs(delta) <= 20.0) {
                rate = delta;
            }
        }

        measuredPhysicalYawRate = rate;
        lastPhysicalYawSample = current;
        lastPhysicalYawSampleTick = tick;
    }

    private int estimateSlewTicksForSolution(AbstractMountedCannonContraption cannonContraption, @Nullable AimSolution solution, double fallbackControllerYaw, double fallbackPitch) {
        if (MotionTraceRecorder.isEnabled() && !inLaunchFeasibilityProbe && solution != null && solution.aimDirection() != null) {
            MountAimFrame diagnosticFrame = this.resolveMountAimFrame(cannonContraption);
            AimCommandEvaluation diagnosticEvaluation = diagnosticFrame.evaluate(solution.aimDirection());
            boolean valid = diagnosticEvaluation.valid();
            AimCommand applied = valid ? diagnosticEvaluation.appliedCommand() : null;

            MotionTraceRecorder.record(
                    "targeting.slew_frame",
                    this.level,
                    getWeaponMountPos(),
                    "mountFrame", diagnosticFrame.kind(),
                    "sourceSublevel", diagnosticFrame.sourceSublevelId(),
                    "commandValid", valid,
                    "worldDirection", solution.aimDirection(),
                    "fallbackControllerYaw", fallbackControllerYaw,
                    "fallbackPitch", fallbackPitch,
                    "appliedControllerYaw",
                    applied == null ? null : applied.controllerYawDeg(),
                    "appliedPitch",
                    applied == null ? null : applied.pitchDeg(),
                    "structuralYaw",
                    this.yawController != null && this.yawController.hasStructuralKineticSelectionForTargeting(),
                    "structuralPitch", usesStructuralPitchForAuthority()
            );
        }

        if (solution != null && solution.aimDirection() != null
                && ((this.yawController != null
                && this.yawController.hasStructuralKineticSelectionForTargeting()) || usesStructuralPitchForAuthority())) {
            return this.estimateStructuralSlewTicks(cannonContraption, solution.aimDirection(), fallbackControllerYaw, fallbackPitch);
        }
        if (solution != null) {
            MountAimFrame frame = this.resolveMountAimFrame(cannonContraption);
            AimCommandEvaluation evaluation = frame.evaluate(solution.aimDirection());
            if ((frame.kind() == MountFrameKind.SUBLEVEL || frame.sourceSublevelId() != null) && evaluation.valid()) {
                AimCommand command = evaluation.appliedCommand();
                return this.estimateSlewTicks(cannonContraption, command.controllerYawDeg(), command.pitchDeg());
            }
        }

        return this.estimateSlewTicks(cannonContraption, fallbackControllerYaw, fallbackPitch);
    }

    private int estimateStructuralSlewTicks(
            AbstractMountedCannonContraption cannonContraption,
            Vec3 worldAimDirection,
            double fallbackControllerYaw,
            double fallbackPitch) {
        double yawTicks = 0.0;
        if (this.yawController != null
                && this.yawController.hasStructuralKineticSelectionForTargeting()) {
            KineticAimFrame frame = this.yawController.getStructuralAimFrame();
            Double physical = this.yawController.getStructuralPhysicalControllerAngle();
            if (frame == null || physical == null) {
                return 61;
            }
            double target = frame.controllerTargetDegrees(CannonAxis.YAW, worldAimDirection);

            double error = Math.abs(shortestDelta(physical, target));
            double speed = this.yawController.getStructuralEffectiveDegreesPerTick();

            if (error > 1.0e-4 && (!Double.isFinite(speed) || speed <= 1.0e-6)) {
                return 61;
            }

            yawTicks = ticksForAngle(error, speed);
        } else if (this.yawController != null) {
            PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
            if (contraption != null) {
                yawTicks = ticksForAngle(
                        Math.abs(shortestDelta(wrap360(contraption.yaw),
                                wrap360(fallbackControllerYaw))),
                        Math.abs(this.yawController.getAvailableInputSpeed()) / 24.0);
            }
        }

        double pitchTicks = 0.0;
        if (usesStructuralPitchForAuthority()) {
            KineticAimFrame frame = this.pitchController.getStructuralAimFrame();
            Double physical = this.pitchController.getStructuralPhysicalControllerAngle();
            if (frame == null || physical == null) {
                return 61;
            }

            double target = frame.controllerTargetDegrees(CannonAxis.PITCH, worldAimDirection);
            double error = Math.abs(target - physical);
            double speed = this.pitchController.getStructuralEffectiveDegreesPerTick();

            if (error > 1.0e-4 && (!Double.isFinite(speed) || speed <= 1.0e-6)) {
                return 61;
            }

            pitchTicks = ticksForAngle(error, speed);
        } else if (this.pitchController != null) {
            PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
            if (contraption != null) {
                double currentPitch = contraption.pitch;
                int invert = -cannonContraption.initialOrientation().getStepX()
                        + cannonContraption.initialOrientation().getStepZ();
                currentPitch *= -invert;
                pitchTicks = ticksForAngle(
                        Math.abs(fallbackPitch - currentPitch),
                        Math.abs(this.pitchController.getAvailableInputSpeed()) / 24.0);
            }
        }


        if (MotionTraceRecorder.isEnabled() && !inLaunchFeasibilityProbe) {
            boolean structuralYaw = yawController != null && yawController.hasStructuralKineticSelectionForTargeting();
            boolean structuralPitch = usesStructuralPitchForAuthority();
            KineticAimFrame yawFrame = structuralYaw ? yawController.getStructuralAimFrame() : null;
            KineticAimFrame pitchFrame = structuralPitch ? pitchController.getStructuralAimFrame() : null;
            Double physicalYaw = structuralYaw ? yawController.getStructuralPhysicalControllerAngle() : this.cannonMount.getContraption() == null ? null : (double) this.cannonMount.getContraption().yaw;
            Double targetYaw = yawFrame == null ? fallbackControllerYaw : yawFrame.controllerTargetDegrees(CannonAxis.YAW, worldAimDirection);
            Double physicalPitch = structuralPitch ? pitchController.getStructuralPhysicalControllerAngle() : this.cannonMount.getContraption() == null ? null : (double) this.cannonMount.getContraption().pitch * (cannonContraption.initialOrientation().getStepX() - cannonContraption.initialOrientation().getStepZ());
            Double targetPitch = pitchFrame == null ? fallbackPitch : pitchFrame.controllerTargetDegrees(CannonAxis.PITCH, worldAimDirection);

            MotionTraceRecorder.record(
                    "targeting.slew_axes",
                    this.level,
                    getWeaponMountPos(),
                    "mode", "structural",
                    "structuralYaw", structuralYaw,
                    "structuralPitch", structuralPitch,
                    "yawFrameValid", !structuralYaw || yawFrame != null,
                    "pitchFrameValid", !structuralPitch || pitchFrame != null,
                    "physicalYaw", physicalYaw,
                    "targetYaw", targetYaw,
                    "yawErrorDegrees",
                    physicalYaw == null || targetYaw == null ? null : Math.abs(shortestDelta(physicalYaw, targetYaw)),
                    "yawInputRpm",
                    yawController == null ? null : yawController.getAvailableInputSpeed(),
                    "yawDegreesPerTick",
                    structuralYaw ? yawController.getStructuralEffectiveDegreesPerTick() : yawController == null ? 0.0 : Math.abs(yawController.getAvailableInputSpeed()) / 24.0,
                    "yawEstimatedTicks", yawTicks,
                    "physicalPitch", physicalPitch,
                    "targetPitch", targetPitch,
                    "pitchErrorDegrees",
                    physicalPitch == null || targetPitch == null ? null : Math.abs(targetPitch - physicalPitch),
                    "pitchInputRpm",
                    pitchController == null ? null : pitchController.getAvailableInputSpeed(),
                    "pitchDegreesPerTick",
                    structuralPitch ? pitchController.getStructuralEffectiveDegreesPerTick() : pitchController == null ? 0.0 : Math.abs(pitchController.getAvailableInputSpeed()) / 24.0,
                    "pitchEstimatedTicks", pitchTicks,
                    "totalEstimatedTicks",
                    Math.min(61.0, Math.ceil(Math.max(yawTicks, pitchTicks)))
            );
        }


        double ticks = Math.max(yawTicks, pitchTicks);
        return !Double.isFinite(ticks) ? 61 : (int) Math.max(0.0, Math.min(61.0, Math.ceil(ticks)));
    }

    private MountAimFrame resolveMountAimFrame(
            AbstractMountedCannonContraption cannonContraption) {
        long tick = this.level == null ? Long.MIN_VALUE
                : this.level.getGameTime();
        if (this.cachedMountAimFrame != null
                && this.cachedMountAimFrameTick == tick) {
            return this.cachedMountAimFrame;
        }
        MountAimFrame frame = this.buildMountAimFrame(
                this.cannonMount, cannonContraption, this.yawController);
        this.cachedMountAimFrameTick = tick;
        this.cachedMountAimFrame = frame;
        return frame;
    }

    private MountAimFrame resolveMountAimFrame(
            CannonMountContext mount,
            AbstractMountedCannonContraption cannonContraption,
            @Nullable AutoYawControllerBlockEntity yawController) {
        if (mount.sameMount(this.cannonMount)
                && yawController == this.yawController) {
            return this.resolveMountAimFrame(cannonContraption);
        }
        return this.buildMountAimFrame(mount, cannonContraption,
                yawController);
    }

    private MountAimFrame buildMountAimFrame(
            CannonMountContext mount,
            AbstractMountedCannonContraption cannonContraption,
            @Nullable AutoYawControllerBlockEntity yawController) {

        double controllerMin = this.pitchController == null
                ? PitchConstraint.SOLVER_MIN_PITCH_DEG
                : this.pitchController.getMinAngleDeg();
        double controllerMax = this.pitchController == null
                ? PitchConstraint.SOLVER_MAX_PITCH_DEG
                : this.pitchController.getMaxAngleDeg();
        double cannonMin = cannonContraption == null
                ? PitchConstraint.SOLVER_MIN_PITCH_DEG
                : -cannonContraption.maximumDepression(mount.controller());
        double cannonMax = cannonContraption == null
                ? PitchConstraint.SOLVER_MAX_PITCH_DEG
                : cannonContraption.maximumElevation(mount.controller());

        Vec3 rightAxis = new Vec3(1.0, 0.0, 0.0);
        Vec3 upAxis = new Vec3(0.0, 1.0, 0.0);
        Vec3 forwardAxis = new Vec3(0.0, 0.0, 1.0);
        MountFrameKind frameKind = MountFrameKind.WORLD;
        UUID sourceSublevelId = null;
        String unavailableReason = null;
        SubLevelAccess mountShip = null;
        boolean authorityMount = mount.sameMount(this.cannonMount);
        if (Mods.SABLE.isLoaded() && this.level != null) {
            mountShip = SableCompanion.INSTANCE.getContaining(
                    this.level, mount.getBlockPos());
            if (mountShip != null) {
                sourceSublevelId = mountShip.getUniqueId();
                if (authorityMount) {
                    this.lastResolvedSourceSublevelId = sourceSublevelId;
                }
            } else if (authorityMount
                    && this.lastResolvedSourceSublevelId != null) {
                sourceSublevelId = this.lastResolvedSourceSublevelId;
            }
        }

        boolean structuralPitch = usesStructuralPitchForMount(mount.getBlockPos());
        if (structuralPitch) {
            KineticAimFrame aimFrame = this.pitchController.getStructuralAimFrame();
            if (mountShip == null && sourceSublevelId != null) {
                unavailableReason = "source_frame_unavailable";
                frameKind = MountFrameKind.UNAVAILABLE;
            } else if (aimFrame == null) {
                unavailableReason = "structural_frame_unavailable";
                frameKind = MountFrameKind.UNAVAILABLE;
            } else {
                rightAxis = aimFrame.rightAxis();
                upAxis = aimFrame.upAxis();
                forwardAxis = aimFrame.forwardAxis();
                frameKind = MountFrameKind.STRUCTURAL;
            }
        } else if (mountShip != null) {
            rightAxis = SableUtils.getWorldVecDirectionTransform(rightAxis, mountShip);
            upAxis = SableUtils.getWorldVecDirectionTransform(upAxis, mountShip);
            forwardAxis = SableUtils.getWorldVecDirectionTransform(forwardAxis, mountShip);
            frameKind = MountFrameKind.SUBLEVEL;
        } else if (sourceSublevelId != null) {
            frameKind = MountFrameKind.UNAVAILABLE;
            unavailableReason = "source_frame_unavailable";
        }

        PitchConstraint pitchConstraint = frameKind == MountFrameKind.UNAVAILABLE
                ? new PitchConstraint(1.0, -1.0,
                rightAxis, upAxis, forwardAxis)
                : mountPitchConstraint(
                structuralPitch, controllerMin, controllerMax, cannonMin, cannonMax,
                rightAxis, upAxis, forwardAxis);
        ControllerMovementLimits yawLimits = yawController == null
                ? ControllerMovementLimits.defaults(CannonAxis.YAW)
                : yawController.getMovementLimits();

        double yawNeutral = yawController == null ? 0.0 : yawController.getLimitNeutralAngleDeg();
        KineticAimFrame yawLimitFrame = null;
        if (yawController != null && yawController.hasStructuralKineticSelectionForTargeting()) {
            yawLimitFrame = yawController.getStructuralAimFrame();
            if (yawLimitFrame == null) {
                frameKind = MountFrameKind.UNAVAILABLE;
                unavailableReason = "structural_yaw_frame_unavailable";
            }
        }

        return new MountAimFrame(
                frameKind, sourceSublevelId, pitchConstraint,
                yawLimits, yawNeutral, unavailableReason, yawLimitFrame);
    }

    public boolean matchesWeapon(@Nullable RadarWeaponAdapter other) {
        if (other == null || this.weapon == null) {
            return false;
        }

        if (!this.weapon.isValid() || !other.isValid()) {
            return false;
        }

        return this.weapon.getMountPos().equals(other.getMountPos()) && Objects.equals(this.weapon.getWeaponIdentity(), other.getWeaponIdentity());
    }

    public void stopWeaponOutput() {
        if (this.weapon != null) {
            this.weapon.setFiring(false);
        }
    }

    static PitchConstraint mountPitchConstraint(
            boolean structuralPitch,
            double controllerMin, double controllerMax,
            double cannonMin, double cannonMax,
            Vec3 rightAxis, Vec3 upAxis, Vec3 forwardAxis) {
        // A swivel rotates the entire assembly; CBC's internal joint is idle.
        return PitchConstraint.intersect(controllerMin, controllerMax,
                structuralPitch ? PitchConstraint.SOLVER_MIN_PITCH_DEG : cannonMin,
                structuralPitch ? PitchConstraint.SOLVER_MAX_PITCH_DEG : cannonMax,
                rightAxis, upAxis, forwardAxis);
    }

    private PitchConstraint effectivePitchConstraint(AbstractMountedCannonContraption cannonContraption) {
        return this.resolveMountAimFrame(cannonContraption).pitchConstraint();
    }

    @Nullable
    private Vec3 worldDirectionFromMountCommand(@Nullable Double pitchDeg, @Nullable Double controllerYawDeg) {
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null
                && mounted.getContraption()
                instanceof AbstractMountedCannonContraption c ? c : null;

        return this.worldDirectionFromMountCommand(this.resolveMountAimFrame(cannon), pitchDeg, controllerYawDeg);
    }

    @Nullable
    private Vec3 worldDirectionFromMountCommand(
            MountAimFrame frame, @Nullable Double pitchDeg,
            @Nullable Double controllerYawDeg) {
        if (pitchDeg == null || controllerYawDeg == null
                || !Double.isFinite(pitchDeg) || !Double.isFinite(controllerYawDeg)) {
            return null;
        }
        return frame.worldDirection(pitchDeg, controllerYawDeg);
    }

    @Nullable
    private static Vec3 directionFromTo(@Nullable Vec3 origin, @Nullable Vec3 target) {
        if (origin == null || target == null) {
            return null;
        }
        Vec3 direction = target.subtract(origin);
        return direction.lengthSqr() < 1.0e-12 ? null : direction.normalize();
    }

    @Nullable
    private AimCommand directAimCommand(AbstractMountedCannonContraption cannonContraption, @Nullable Vec3 origin, @Nullable Vec3 target) {
        if (origin == null || target == null) {
            return null;
        }

        Vec3 direction = target.subtract(origin);
        if (direction.lengthSqr() < 1.0E-12) {
            return null;
        }

        AimCommandEvaluation evaluation = this.resolveMountAimFrame(
                cannonContraption).evaluate(direction);
        return evaluation.valid() ? evaluation.command() : null;
    }

    private int estimateSlewTicks(AbstractMountedCannonContraption cannonContraption, double desiredControllerYaw, double desiredPitch) {
        PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
        if (contraption == null) {
            return 0;
        } else {
            double yawTicks = (double)0.0F;
            if (this.yawController != null) {
                double currentYaw = wrap360((double)contraption.yaw);
                double targetYaw = wrap360(desiredControllerYaw);
                double yawError = shortestDelta(currentYaw, targetYaw);
                yawTicks = ControllerMotionProfile.estimateTicksFromVelocity(yawError, Math.abs(this.yawController.getAvailableInputSpeed()), measuredPhysicalYawRate);
            }

            double pitchTicks = (double)0.0F;
            if (this.pitchController != null) {
                double currentPitch = (double)contraption.pitch;
                int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
                currentPitch *= (double)(-invert);
                double pitchError = Math.abs(desiredPitch - currentPitch);
                pitchTicks = ControllerMotionProfile.estimateTicks(
                        pitchError,
                        Math.abs(this.pitchController.getAvailableInputSpeed()));
            }

            if (MotionTraceRecorder.isEnabled() && !inLaunchFeasibilityProbe) {
                int pitchInvert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();

                double physicalPitch = contraption.pitch * -pitchInvert;

                MotionTraceRecorder.record(
                        "targeting.slew_axes",
                        this.level,
                        getWeaponMountPos(),
                        "mode", "direct",
                        "physicalYaw", contraption.yaw,
                        "requestedControllerYaw", desiredControllerYaw,
                        "yawErrorDegrees",
                        Math.abs(shortestDelta(wrap360(contraption.yaw), wrap360(desiredControllerYaw))),
                        "yawInputRpm",
                        yawController == null ? null : yawController.getAvailableInputSpeed(),
                        "yawEstimatedTicks", yawTicks,
                        "physicalPitch", physicalPitch,
                        "requestedPitch", desiredPitch,
                        "pitchErrorDegrees",
                        Math.abs(desiredPitch - physicalPitch),
                        "pitchInputRpm",
                        pitchController == null ? null : pitchController.getAvailableInputSpeed(),
                        "pitchEstimatedTicks", pitchTicks,
                        "totalEstimatedTicks",
                        Math.min(61.0, Math.ceil(Math.max(yawTicks, pitchTicks)))
                );
            }

            double ticks = Math.max(yawTicks, pitchTicks);
            return !Double.isFinite(ticks) ? 61 : (int) Math.max(0.0, Math.min(61.0, Math.ceil(ticks)));
        }
    }

    private static double ticksForAngle(double angleDeg, double stepDegPerTick) {
        return !(angleDeg <= (double)0.0F) && !(stepDegPerTick <= 1.0E-6) ? angleDeg / stepDegPerTick : (double)0.0F;
    }

    private int computeNewSolverMaxFlightTicks(double projectileSpeed, @Nullable Vec3 muzzlePos, @Nullable Vec3 targetPos) {
        double speed = Math.max(1.0E-6, projectileSpeed);
        double targetDistance = muzzlePos != null && targetPos != null ? muzzlePos.distanceTo(targetPos) : 0.0;
        double solveDistance = Math.max(Math.max((double)1.0F, this.maxSimDistanceBlocks), targetDistance);
        int ticks = (int)Math.ceil(solveDistance / speed) + 80;
        return Math.max(40, Math.min(MAX_NEW_SOLVER_FLIGHT_TICKS, ticks));
    }

    private static double selectPitchRoot(List<Double> pitchRoots, boolean preferHighArc) {
        return preferHighArc && pitchRoots.size() > 1 ? pitchRoots.get(pitchRoots.size() - 1) : pitchRoots.get(0);
    }

    private static Vec3 predictTarget(Vec3 pos, Vec3 vel, Vec3 accel, double ticks) {
        return predictTarget(pos, vel, accel, ticks, TargetMotionClass.UNKNOWN, (double)-0.08F);
    }

    private static Vec3 predictTarget(Vec3 pos, Vec3 vel, Vec3 accel, double ticks, TargetMotionClass motionClass, double gravity) {
        double safeTicks = Double.isFinite(ticks) ? Math.max((double)0.0F, ticks) : (double)0.0F;
        return pos.add(vel.scale(safeTicks)).add(accel.scale((double)0.5F * safeTicks * safeTicks));
    }

    private static Vec3 predictSnapshotTarget(TargetingSnapshot snapshot, double ticks) {
        if (snapshot.rigidBodyTargetMotion() != null) {
            return snapshot.rigidBodyTargetMotion().positionAt(ticks);
        }

        Vec3 effectiveAcceleration = TargetingMotion.effectiveAcceleration(snapshot.targetAcceleration(), snapshot.targetMotionClass());
        return TargetingMotion.futureTargetPosition(snapshot.targetPosition(), snapshot.targetVelocity(), effectiveAcceleration, ticks, snapshot.targetAccelerationRemainingTicks());
    }

    @Nullable
    static AABB translateTargetAabb(@Nullable AABB targetAabb,
                                    @Nullable Vec3 guidanceOffset,
                                    @Nullable Vec3 predictionOffset) {
        if (targetAabb == null) {
            return null;
        }
        return targetAabb.move(finiteOrZero(guidanceOffset)
                .add(finiteOrZero(predictionOffset)));
    }

    private static Vec3 finiteOrZero(Vec3 vec) {
        return finite(vec) ? vec : Vec3.ZERO;
    }

    private static boolean finite(@Nullable Vec3 vec) {
        return vec != null
                && Double.isFinite(vec.x)
                && Double.isFinite(vec.y)
                && Double.isFinite(vec.z);
    }

    private static Vec3 clampAcceleration(Vec3 accel) {
        double max = (double)0.25F;
        double lenSqr = accel.lengthSqr();
        return !(lenSqr <= max * max) && !(lenSqr < 1.0E-12) ? accel.normalize().scale(max) : accel;
    }

    private static double wrap360(double deg) {
        deg %= (double)360.0F;
        if (deg < (double)0.0F) {
            deg += (double)360.0F;
        }

        return deg;
    }

    private static double shortestDelta(double from, double to) {
        return (to - from + (double)540.0F) % (double)360.0F - (double)180.0F;
    }

    private boolean passesSafeZone() {
        if (this.safeZones == null || this.safeZones.isEmpty()) {
            return false;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return true;
        }

        Vec3 start = this.getCannonRayStart();
        if (!finite(start)) {
            return true;
        }

        Vec3 observed = null;
        AABB entityBounds = null;

        if (this.binoMode && this.binoTargetPos != null) {
            observed = this.toWorldPosition(serverLevel, this.binoTargetPos);
        } else if (this.targetEntity != null && this.targetEntity.isAlive()) {
            entityBounds = this.targetEntity.getBoundingBox();
            observed = entityBounds.getCenter();
        } else if (this.targetSublevel != null && this.currentSublevelAim != null) {
            observed = this.currentSublevelAim.worldPoint();
        } else if (this.activetrack != null) {
            observed = this.activetrack.position();
        }

        Vec3 compensated = this.target;
        Vec3 aim = this.lastAimPoint;

        if (!finite(observed) && !finite(compensated) && !finite(aim)) {
            return true;
        }

        for (SafeZone zone : this.safeZones) {
            if (zone == null) {
                continue;
            }

            if (entityBounds != null && zone.subLevelId() == null && zone.bounds().intersects(entityBounds)) {
                return true;
            }

            if (finite(observed) && (zone.contains(this.level, observed) || zone.intersects(this.level, start, observed))) {
                return true;
            }

            if (finite(compensated) && zone.intersects(this.level, start, compensated)) {
                return true;
            }

            if (finite(aim) && zone.intersects(this.level, start, aim)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCorrectYawPitch(boolean lag) {
        return this.hasCorrectYawPitch(lag, 0.0);
    }

    private boolean hasCorrectYawPitch(boolean lag, double minimumToleranceDegrees) {
        return this.hasCorrectYawPitch(lag, minimumToleranceDegrees, Double.POSITIVE_INFINITY, null);
    }

    private boolean hasCorrectYawPitch(boolean lag, double minimumToleranceDegrees, double maximumToleranceDegrees, @Nullable Vec3 requestedWorldDirection) {
        if (this.yawController == null && this.pitchController == null) {
            return false;
        }

        boolean yaw;
        if (this.yawController != null) {
            yaw = this.yawController.isAlignedForFiring(lag, minimumToleranceDegrees, maximumToleranceDegrees);
        } else {
            yaw = !this.cannonMount.supportsDirectYawControl()
                    && this.isFixedMountYawAligned(
                    this.fixedMountDesiredYaw,
                    lag,
                    minimumToleranceDegrees,
                    maximumToleranceDegrees);
        }

        boolean pitch;

        if (this.pitchController != null) {
            pitch = this.pitchController.isAlignedForFiring(lag, minimumToleranceDegrees, maximumToleranceDegrees);
        } else {
            PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
            AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;
            Double physicalPitch = cannon == null ? null : currentPitchDeg(cannon);
            AimCommandEvaluation expected = cannon == null || !finite(requestedWorldDirection) ? null : resolveMountAimFrame(cannon).evaluate(requestedWorldDirection);

            double tolerance = AutoYawControllerBlockEntity.getToleranceDeg();
            if (!lag) {
                tolerance += RadarConfig.server().targetLoosenAmount.get();
            }

            tolerance = Math.max(tolerance, Math.max(0.0, minimumToleranceDegrees));
            if (Double.isFinite(maximumToleranceDegrees)) {
                tolerance = Math.min(tolerance, Math.max(0.0, maximumToleranceDegrees));
            }

            pitch = expected != null
                    && expected.fireEligible()
                    && physicalPitch != null
                    && Double.isFinite(physicalPitch)
                    && Double.isFinite(expected.requestedPitchDeg())
                    && Math.abs(physicalPitch - expected.requestedPitchDeg()) < tolerance;
        }

        return yaw && pitch;
    }

    public boolean canAimAtFixedYaw(@Nullable Vec3 aimPoint) {
        if (this.yawController != null || this.cannonMount.supportsDirectYawControl()) {
            return true;
        }
        PitchOrientedContraptionEntity mounted = this.cannonMount.getContraption();
        if (mounted == null
                || !(mounted.getContraption() instanceof AbstractMountedCannonContraption cannon)
                || aimPoint == null) {
            return false;
        }
        AimCommand command = this.directAimCommand(cannon, this.getCannonRayStart(), aimPoint);
        return command != null && this.isFixedMountYawAligned(command.controllerYawDeg(), false);
    }

    private boolean isFixedMountYawAligned(@Nullable Double desiredYaw, boolean lag) {
        return this.isFixedMountYawAligned(desiredYaw, lag, 0.0);
    }

    private boolean isFixedMountYawAligned(
            @Nullable Double desiredYaw,
            boolean lag,
            double minimumToleranceDegrees
    ) {
        return this.isFixedMountYawAligned(desiredYaw, lag, minimumToleranceDegrees,
                Double.POSITIVE_INFINITY);
    }

    private boolean isFixedMountYawAligned(
            @Nullable Double desiredYaw,
            boolean lag,
            double minimumToleranceDegrees,
            double maximumToleranceDegrees
    ) {
        PitchOrientedContraptionEntity mounted = this.cannonMount.getContraption();
        if (desiredYaw == null || mounted == null) {
            return false;
        }
        double tolerance = AutoYawControllerBlockEntity.getToleranceDeg();
        if (!lag) {
            tolerance += RadarConfig.server().targetLoosenAmount.get();
        }
        if (Double.isFinite(minimumToleranceDegrees)) {
            tolerance = Math.max(tolerance, Math.max(0.0, minimumToleranceDegrees));
        }
        if (Double.isFinite(maximumToleranceDegrees)) {
            tolerance = Math.min(tolerance,
                    Math.max(0.0, maximumToleranceDegrees));
        }
        return Math.abs(shortestDelta(wrap360(mounted.yaw), wrap360(desiredYaw))) < tolerance;
    }

    private int activeAlignmentAxisCount() {
        int axes = this.pitchController == null ? 0 : 1;

        boolean yawAxisActive;

        if (this.weapon != null) {
            RadarMountAdapter mount = this.weapon.getMount();

            yawAxisActive = this.yawController != null || mount != null && mount.isValid() && mount.supportsYaw();
        } else {
            yawAxisActive = this.yawController != null || this.cannonMount != null && !this.cannonMount.supportsDirectYawControl();
        }

        if (yawAxisActive) {
            axes++;
        }

        return Math.max(1, axes);
    }

    static FiringAlignmentTolerance firingAlignmentTolerance(
            double distanceBlocks,
            double lateralErrorBudgetBlocks,
            int axisCount
    ) {
        boolean validDistance = Double.isFinite(distanceBlocks)
                && distanceBlocks >= 0.0;
        double distance = validDistance ? distanceBlocks : 0.0;
        double budget = Double.isFinite(lateralErrorBudgetBlocks)
                ? Math.max(0.0, lateralErrorBudgetBlocks) : 0.0;
        int axes = Math.max(1, axisCount);
        double perAxisBudget = budget / Math.sqrt(axes);
        double maximumDegrees = validDistance
                ? Math.toDegrees(Math.atan2(perAxisBudget, distance)) : 0.0;
        return new FiringAlignmentTolerance(
                maximumDegrees, distance, budget, axes);
    }

    private boolean prepareAdapterShotForFire(WeaponShotProfile expected, WeaponShotContext context) {
        BlockPos mountPos = context.mountPos();

        if (mountPos == null) {
            return false;
        }

        return prepareAdapterShotForFire(expected, context, mountPos);
    }

    private boolean prepareAdapterShotForFire(WeaponShotProfile expected, WeaponShotContext context, BlockPos mountPos) {
        WeaponShotProfile current = WeaponShotAdapterRegistry.resolve(context);

        if (current == null || current.aimMode() != expected.aimMode() || !current.canTriggerFire() || !current.fingerprint().equals(expected.fingerprint())) {
            return false;
        }

        try {
            return current.firePreparation().prepare(context);
        } catch (RuntimeException exception) {
            ConflictTraceRecorder.mark(
                    "weapon_adapter",
                    "prepare_fire",
                    "FAILED",
                    this.level,
                    mountPos,
                    0L,
                    Map.of("fingerprint", expected.fingerprint(), "exception", exception.getClass().getSimpleName())
            );

            DiagnosticRecorder.error(
                    "weapon_adapter",
                    "prepare_fire",
                    "adapter_preparation_failed",
                    exception,
                    this.level,
                    mountPos,
                    "createbigcannons"
            );

            LOGGER.error("Weapon adapter preparation failed for mount={} fingerprint={}", mountPos, expected.fingerprint(), exception);

            return false;
        }
    }

    private void stopFireCannon() {
        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "firing.stop_request",
                    this.level,
                    getWeaponMountPos(),
                    "targetPresent", this.target != null,
                    "activeTrackPresent", this.activetrack != null,
                    "binoMode", this.binoMode,
                    "fireControllerPresent", this.fireController != null,
                    "weaponPresent", this.weapon != null
            );
        }
        for (FireControllerBlockEntity fire : currentFireControllers()) {
            fire.setPowered(false);
        }

        stopWeaponOutput();
    }

    private void endStructuralRadarTracking() {
        if (this.pitchController != null) {
            this.pitchController.endRadarTracking();
        }

        if (this.yawController != null) {
            this.yawController.endRadarTracking();
        }
    }

    private void tryFireCannon() {
        if (this.passesSafeZone()) {
            this.stopFireCannon();
            return;
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "firing.fire_request",
                    this.level,
                    getWeaponMountPos(),
                    "mode", "SINGLE",
                    "fireControllerPresent", this.fireController != null,
                    "weaponPresent", this.weapon != null,
                    "safeZoneAllowed", true
            );
        }

        BlockPos mountPos = getWeaponMountPos();
        setMountFireControllersPowered(mountPos, true);

        if (this.weapon != null && this.weapon.isValid() && this.weapon.isAssembled()) {
            this.weapon.setFiring(true);
        }

        //LOGGER.debug("firing!");
    }

    private void setMountFireControllersPowered(BlockPos mountPos, boolean powered) {
        if (!(this.level instanceof ServerLevel serverLevel) || mountPos == null) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "firing.routing_unavailable",
                        this.level,
                        mountPos,
                        "requestedPowered", powered,
                        "serverLevelValid", this.level instanceof ServerLevel,
                        "mountPositionPresent", mountPos != null
                );
            }

            return;
        }

        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(serverLevel);
        List<BlockPos> firePositions = runtime.getFiringControllersForMount(mountPos);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "firing.routing",
                    serverLevel,
                    mountPos,
                    "requestedPowered", powered,
                    "fireControllerCount", firePositions.size(),
                    "fireControllerPositions", firePositions
            );
        }

        for (BlockPos firePos : firePositions) {
            if (!(serverLevel.getBlockEntity(firePos) instanceof FireControllerBlockEntity fire)) {
                continue;
            }

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "firing.controller_power_request",
                        serverLevel,
                        firePos,
                        "mount", mountPos,
                        "requestedPowered", powered,
                        "previousPowered", fire.isPowered()
                );
            }

            if (powered) {
                AdvancedProximityFuze.pushLaunchContext(serverLevel, mountPos);
                try {
                    fire.setPowered(true);
                } finally {
                    AdvancedProximityFuze.popLaunchContext();
                }
            } else {
                fire.setPowered(false);
            }
        }
    }

    private boolean isMountStateOk() {
        if (this.level != null && this.cannonMount != null) {
            if (this.cannonMount.isRemoved()) {
                return false;
            } else {
                PitchOrientedContraptionEntity ce = this.cannonMount.getContraption();
                if (ce == null) {
                    return false;
                } else if (!ce.isAlive()) {
                    return false;
                } else {
                    return ce.getContraption() instanceof AbstractMountedCannonContraption;
                }
            }
        } else {
            return false;
        }
    }

    private static final class LosCache {
        boolean ok;
        long tick;
    }

    private static record TargetMotionState(
            Vec3 position, Vec3 rawVelocity, Vec3 rawAcceleration,
            Vec3 smoothedVelocity, Vec3 smoothedAcceleration,
            boolean onGround, long tick, int stationarySamples) {
    }

    private static record TargetMotionEstimate(
            TargetMotionClass motionClass, Vec3 velocity,
            Vec3 acceleration, double jerk, int stableTicksRequired,
            double minConfidence, double aimStableEps,
            boolean looseAim, String reason) {
    }

    private static record CachedSublevelAim(
            UUID sublevelId, SubLevelAimResolver.LocalAim localAim,
            int revision, boolean requireLos, long acquiredTick) {
    }

    private static record ResolvedSublevelAim(
            Vec3 worldPoint, @Nullable AABB worldAabb,
            @Nullable SubLevelAimResolver.LocalAim localAim,
            int revision, boolean fallback) {
    }

    private static record AimGeometry(
            Vec3 steeringOrigin, Vec3 muzzlePosition,
            double muzzleForwardOffset, boolean coupled) {
        Vec3 launchPosition(@Nullable Vec3 direction) {
            if (!this.coupled || direction == null
                    || direction.lengthSqr() < 1.0E-12) {
                return this.muzzlePosition;
            }
            return this.steeringOrigin.add(
                    direction.normalize()
                            .scale(this.muzzleForwardOffset));
        }
    }

    /**
     * Resolves the point this weapon would actually use to engage a track.
     * Sublevel tracks are evaluated without mutating the live firing cache so
     * auto-target candidate probing cannot disturb the current target.
     */
    @Nullable
    public Vec3 resolveEngagementAimPoint(ServerLevel serverLevel,
                                          @Nullable RadarTrack track,
                                          boolean requireLos) {
        if (track == null || track.isSynthetic() || !isSableTrack(track)) {
            return track == null ? null : track.position();
        }
        UUID id;
        try {
            id = UUID.fromString(track.id());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        if (!Mods.SABLE.isLoaded()) {
            return null;
        }

        SubLevelAccess subLevel = SableWeaponFiringAccess.findSubLevel(serverLevel, id);
        Vec3 origin = this.getCannonRayStart();
        if (subLevel == null || origin == null) {
            return null;
        }
        ResolvedSublevelAim aim = this.resolveSublevelAim(
                serverLevel, subLevel, origin, requireLos, false);
        return aim == null ? null : aim.worldPoint();
    }

    private static boolean isSableTrack(RadarTrack track) {
        return track != null && track.trackCategory() == TrackCategory.SABLE
                && "Sable:ship".equals(track.entityType());
    }

    @Nullable
    private ResolvedSublevelAim resolveSublevelAim(
            ServerLevel serverLevel, SubLevelAccess subLevel,
            Vec3 worldOrigin, boolean requireLos, boolean cacheSelection) {
        UUID id = subLevel.getUniqueId();
        SableSilhouetteServerCache.Snapshot snapshot =
                SableSilhouetteServerCache.getSnapshot(serverLevel, id);
        long now = serverLevel.getGameTime();

        if (!snapshot.hasDetailedHull()) {
            if (cacheSelection && this.cachedSublevelAim != null) {
                this.invalidateSublevelAimDependentState(id);
                this.cachedSublevelAim = null;
            }
            Vec3 center = RadarTrackUtil.getPosition(subLevel);
            ResolvedSublevelAim fallback = new ResolvedSublevelAim(
                    center, this.resolveWholeSublevelWorldAabb(subLevel),
                    null, snapshot.revision(), true);
            if (requireLos && !this.rayReachesTargetSublevel(
                    worldOrigin, center, id)) {
                return null;
            }
            return fallback;
        }

        CachedSublevelAim cached = cacheSelection
                ? this.cachedSublevelAim : null;
        boolean sameCache = cached != null
                && id.equals(cached.sublevelId())
                && cached.revision() == snapshot.revision()
                && cached.requireLos() == requireLos;
        boolean cachedPointGone = sameCache && !isSublevelAimBlockPresent(serverLevel, cached.localAim());

        if (cachedPointGone) {
            SableSilhouetteServerCache.requestRefreshForStaleAim(serverLevel, id, snapshot.revision());
        }

        boolean reacquire = cachedPointGone
                || !sameCache
                || now - cached.acquiredTick() >= SUBLEVEL_AIM_REACQUIRE_TICKS;

        if (sameCache && !reacquire) {
            ResolvedSublevelAim resolved = this.materializeSublevelAim(
                    subLevel, cached.localAim(), cached.revision());
            if (!requireLos || this.rayReachesTargetSublevel(
                    worldOrigin, resolved.worldPoint(), id)) {
                return resolved;
            }
        }

        Vec3 localOrigin = SubLevelAimResolver.toLocal(subLevel, worldOrigin);
        final boolean[] missingCandidate = {false};

        SubLevelAimResolver.LocalAim selected = SubLevelAimResolver.select(
                snapshot.silhouette().localBoxes(), localOrigin,
                SUBLEVEL_AIM_CANDIDATE_LIMIT, candidate -> {
                    if (!isSublevelAimBlockPresent(serverLevel, candidate)) {
                        missingCandidate[0] = true;
                        return false;
                    }

                    if (!requireLos) {
                        return true;
                    }

                    Vec3 worldPoint = SubLevelAimResolver.toWorld(subLevel, candidate.point());
                    return this.rayReachesTargetSublevel(worldOrigin, worldPoint, id);
                });

        if (missingCandidate[0]) {
            SableSilhouetteServerCache.requestRefreshForStaleAim(serverLevel, id, snapshot.revision());
        }

        if (selected == null) {
            if (cacheSelection && cachedPointGone) {
                this.cachedSublevelAim = null;
                this.invalidateSublevelAimDependentState(id);
            }
            return null;
        }

        if (cacheSelection) {
            boolean changed = cached == null
                    || cached.revision() != snapshot.revision()
                    || !cached.localAim().equals(selected);
            this.cachedSublevelAim = new CachedSublevelAim(
                    id, selected, snapshot.revision(), requireLos, now);
            if (changed) {
                this.invalidateSublevelAimDependentState(id);
            }
        }
        return this.materializeSublevelAim(
                subLevel, selected, snapshot.revision());
    }

    private static boolean isSublevelAimBlockPresent(ServerLevel level, SubLevelAimResolver.LocalAim aim) {
        BlockPos localPos = BlockPos.containing(aim.point());
        var state = level.getBlockState(localPos);

        return !state.isAir() && !state.getCollisionShape(level, localPos).isEmpty();
    }

    private ResolvedSublevelAim materializeSublevelAim(
            SubLevelAccess subLevel, SubLevelAimResolver.LocalAim localAim,
            int revision) {
        return new ResolvedSublevelAim(
                SubLevelAimResolver.toWorld(subLevel, localAim.point()),
                SubLevelAimResolver.toWorldAabb(subLevel, localAim.box()),
                localAim, revision, false);
    }

    private boolean rayReachesTargetSublevel(Vec3 start, Vec3 end,
                                             UUID targetId) {
        for (SafeZone zone : this.safeZones) {
            if (zone != null && zone.intersects(this.level, start, end)) {
                return false;
            }
        }
        ClipContext context = new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, CollisionContext.empty());
        HitResult hit = this.level.clip(context);

        if (hit.getType() == Type.MISS) {
            return true;
        }

        Vec3 worldHitPosition = hit.getLocation();
        SubLevelAccess hitSublevel = null;

        if (Mods.SABLE.isLoaded() && hit instanceof BlockHitResult blockHit) {
            hitSublevel = SableCompanion.INSTANCE.getContaining(this.level, blockHit.getBlockPos());

            if (hitSublevel != null) {
                worldHitPosition = SubLevelAimResolver.toWorld(hitSublevel, worldHitPosition);
            }
        }

        if (worldHitPosition.distanceToSqr(start) >= end.distanceToSqr(start) - 1.0E-6) {
            return true;
        }

        return hitSublevel != null && targetId.equals(hitSublevel.getUniqueId());
    }

    public static record SolverDebugReport(List<String> lines, @Nullable ProjectileSimulator.SimulationResult trajectory) {
    }

    private static record AimCommand(double pitchDeg, double controllerYawDeg) {
    }

    static record AimCommandEvaluation(
            boolean valid,
            double requestedPitchDeg,
            double appliedPitchDeg,
            double requestedControllerYawDeg,
            double appliedControllerYawDeg,
            boolean pitchConstrained,
            boolean yawConstrained,
            String reason
    ) {
        static AimCommandEvaluation invalid(String reason) {
            return new AimCommandEvaluation(false,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    false, false, reason);
        }

        boolean constrained() {
            return pitchConstrained || yawConstrained;
        }

        boolean fireEligible() {
            return valid && !constrained();
        }

        AimCommand command() {
            return new AimCommand(requestedPitchDeg,
                    requestedControllerYawDeg);
        }

        AimCommand appliedCommand() {
            return new AimCommand(appliedPitchDeg,
                    appliedControllerYawDeg);
        }
    }

    record FiringAlignmentTolerance(
            double maximumDegrees,
            double distanceBlocks,
            double lateralErrorBudgetBlocks,
            int axisCount
    ) {
    }

    static record MountAimFrame(
            MountFrameKind kind,
            @Nullable UUID sourceSublevelId,
            PitchConstraint pitchConstraint,
            ControllerMovementLimits yawLimits,
            double yawNeutralDeg,
            @Nullable String unavailableReason,
            @Nullable KineticAimFrame yawLimitFrame
    ) {
        MountAimFrame(MountFrameKind kind, @Nullable UUID sourceSublevelId,
                      PitchConstraint pitchConstraint,
                      ControllerMovementLimits yawLimits, double yawNeutralDeg,
                      @Nullable String unavailableReason) {
            this(kind, sourceSublevelId, pitchConstraint, yawLimits,
                    yawNeutralDeg, unavailableReason, null);
        }

        AimCommandEvaluation evaluate(@Nullable Vec3 worldAimDirection) {
            if (kind == MountFrameKind.UNAVAILABLE) {
                return AimCommandEvaluation.invalid(unavailableReason == null
                        ? "mount_frame_unavailable" : unavailableReason);
            }
            if (!finite(worldAimDirection)
                    || worldAimDirection.lengthSqr() < 1.0E-12) {
                return AimCommandEvaluation.invalid("invalid_aim_direction");
            }
            if (pitchConstraint == null
                    || !pitchConstraint.hasReachablePitch()) {
                return AimCommandEvaluation.invalid(
                        "no_pitch_limit_intersection");
            }
            if (yawLimits == null
                    || yawLimits.axis() != CannonAxis.YAW
                    || !Double.isFinite(yawNeutralDeg)) {
                return AimCommandEvaluation.invalid("invalid_yaw_limits");
            }

            TargetingMath.YawPitch angles = pitchConstraint
                    .mountYawPitch(worldAimDirection);
            double requestedPitch = angles.pitchDeg();
            double requestedYaw = wrap360(angles.yawDeg() + 270.0);
            if (!Double.isFinite(requestedPitch)
                    || !Double.isFinite(requestedYaw)) {
                return AimCommandEvaluation.invalid("invalid_mount_angles");
            }

            boolean pitchConstrained = !pitchConstraint
                    .allows(worldAimDirection);
            // Command angles stay in the mount/pitch frame for trajectory
            // reconstruction. A structural yaw actuator has its own parent
            // frame, which must own both its limits and neutral heading.
            double limitYaw = yawLimitFrame == null ? requestedYaw
                    : yawLimitFrame.controllerTargetDegrees(
                    CannonAxis.YAW, worldAimDirection);
            if (!Double.isFinite(limitYaw)) {
                return AimCommandEvaluation.invalid("invalid_yaw_frame_angle");
            }
            boolean yawConstrained = !yawLimits.allowsControllerTarget(
                    limitYaw, yawNeutralDeg);
            double appliedPitch = Math.max(
                    pitchConstraint.minPitchDeg(), Math.min(
                            pitchConstraint.maxPitchDeg(), requestedPitch));
            double appliedYaw = yawLimits.clampControllerTarget(
                    limitYaw, yawNeutralDeg);
            if (yawLimitFrame != null) {
                Vec3 boundaryDirection = yawLimitFrame.worldDirection(
                        appliedYaw, yawLimitFrame.controllerTargetDegrees(
                                CannonAxis.PITCH, worldAimDirection));
                appliedYaw = wrap360(pitchConstraint.mountYawPitch(
                        boundaryDirection).yawDeg() + 270.0);
            }
            String reason = pitchConstrained && yawConstrained
                    ? "pitch_and_yaw_outside_limits"
                    : pitchConstrained ? "pitch_outside_limits"
                    : yawConstrained ? "yaw_outside_limits" : "ok";
            return new AimCommandEvaluation(true,
                    requestedPitch, appliedPitch,
                    requestedYaw, appliedYaw,
                    pitchConstrained, yawConstrained, reason);
        }

        @Nullable
        Vec3 worldDirection(double pitchDeg,
                            double controllerYawDeg) {
            if (kind == MountFrameKind.UNAVAILABLE
                    || pitchConstraint == null
                    || !Double.isFinite(pitchDeg)
                    || !Double.isFinite(controllerYawDeg)) {
                return null;
            }
            Vec3 local = TargetingMath.directionFromYawPitch(
                    controllerYawDeg - 270.0, pitchDeg);
            if (local.lengthSqr() < 1.0E-12) {
                return null;
            }
            Vec3 world = pitchConstraint.rightAxis().scale(local.x)
                    .add(pitchConstraint.upAxis().scale(local.y))
                    .add(pitchConstraint.forwardAxis().scale(local.z));
            return world.lengthSqr() < 1.0E-12
                    ? null : world.normalize();
        }
    }

    enum MountFrameKind {
        WORLD,
        SUBLEVEL,
        STRUCTURAL,
        UNAVAILABLE
    }

    @Nullable
    private static String motionTraceSolverField(@Nullable TargetingResult result, String field) {
        if (result == null) {
            return null;
        }

        String prefix = field + "=";
        for (String entry : result.debugInfo()) {
            if (entry.startsWith(prefix)) {
                return entry.substring(prefix.length());
            }
        }
        return null;
    }

    static AimUpdateMode selectAimUpdateMode(boolean hasResolvedAim, boolean ballisticAimExpected) {
        if (hasResolvedAim) {
            return AimUpdateMode.SOLVED;
        }
        return ballisticAimExpected ? AimUpdateMode.HOLD : AimUpdateMode.DIRECT;
    }

    static boolean hasResolvedAimForUpdate(
            boolean hasNewTargetingSolution,
            boolean hasLeadSolution,
            boolean canFireWithoutLead,
            boolean adapterDirectAim) {
        return !adapterDirectAim
                && (hasNewTargetingSolution
                || hasLeadSolution
                || canFireWithoutLead);
    }

    static boolean isDirectAdapterAimReady(
            boolean adapterDirectAim, boolean directAimResolved) {
        return !adapterDirectAim || directAimResolved;
    }

    static boolean shouldIssueAimCommand(AimUpdateMode mode) {
        return mode != AimUpdateMode.HOLD;
    }

    static boolean hasFireEligibleAim(
            boolean hasLeadSolution,
            boolean hasNewTargetingSolution,
            boolean canFireWithoutLead) {
        return hasLeadSolution
                || hasNewTargetingSolution
                || canFireWithoutLead;
    }

    private RadarTargetingSolution resolvePublicTargetingSolution(ServerLevel level, Vec3 position, Vec3 velocity, Vec3 acceleration) {
        RadarTargetingSolution defaults = new RadarTargetingSolution(position, finiteOrZero(velocity), finiteOrZero(acceleration));
        RadarWeaponContext weaponContext = weapon != null ? weapon.createContext(level) : null;
        return RadarTargetingRegistry.resolve(new RadarTargetingContext(level, binoMode ? null : activetrack, weaponContext, getWeaponMountPos(), defaults));
    }

    static int nextAimStableTicks(
            @Nullable Vec3 previousAim, @Nullable Vec3 currentAim,
            int stableTicks, double epsilon) {
        if (previousAim == null || currentAim == null
                || previousAim.distanceTo(currentAim)
                > Math.max(0.0, epsilon)) {
            return 0;
        }
        return Math.max(0, stableTicks) + 1;
    }

    enum AimUpdateMode {
        SOLVED,
        HOLD,
        DIRECT
    }

    private enum SolverKind {
        STANDARD_ASYNC,
        CBCAT_ASYNC,
        CBCMS_SERVER
    }

    private static record ResolvedProjectileState(
            ProjectileModel model,
            @Nullable BallisticPropertiesComponent ballistics,
            int lifetimeCapTicks,
            String reason,
            SolverKind solverKind,
            String fingerprint,
            boolean legacyEligible,
            String family,
            String ammunition,
            int fuelTicks
    ) {}

    private static record AsyncTargetingRequest(
            BlockPos mountPos,
            @Nullable UUID targetId,
            Vec3 solvePos,
            long requestTick,
            TargetingSnapshot snapshot,
            ProjectileModel projectileModel,
            SolverKind solverKind,
            String shotFingerprint,
            Vec3 observedSteeringOrigin,
            int leadTicks,
            int trackingLeadTicks,
            boolean ballisticRefined,
            @Nullable RigidBodyLaunchMath.Motion shooterMotion
    ) {}

    private static record AsyncTargetingResult(AsyncTargetingRequest request, @Nullable TargetingResult result, @Nullable List<TargetingResult> alternatives) {}

    private record ImmediateLaunchCheck(boolean allowed, String reason, int impactTick) {
        static ImmediateLaunchCheck reject(String reason) {
            return new ImmediateLaunchCheck(false, reason, -1);
        }

        static ImmediateLaunchCheck accept(int impactTick) {
            return new ImmediateLaunchCheck(true, "trajectory_intersects_target", impactTick);
        }
    }

    private record TargetingValidation(@Nullable TargetingResult result, boolean obstructed) {
        static TargetingValidation invalid() {
            return new TargetingValidation(null, false);
        }

        static TargetingValidation blockedPath() {
            return new TargetingValidation(null, true);
        }

        static TargetingValidation accepted(TargetingResult result) {
            return new TargetingValidation(result, false);
        }
    }

    private static <T> CompletableFuture<T> submitTargetingTask(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        Runnable queuedTask = () -> {
            if (future.isDone()) {
                return;
            }

            try {
                future.complete(supplier.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };

        future.whenComplete((result, failure) -> {
            if (future.isCancelled()) {
                TARGETING_EXECUTOR.remove(queuedTask);
            }
        });

        TARGETING_EXECUTOR.execute(queuedTask);
        return future;
    }

    private static ThreadPoolExecutor createTargetingExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                TARGETING_EXECUTOR_THREADS,
                TARGETING_EXECUTOR_THREADS,
                10L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(TARGETING_EXECUTOR_QUEUE_CAPACITY),
                new TargetingThreadFactory()
        );
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private static final class TargetingThreadFactory implements ThreadFactory {
        private final AtomicInteger threadIndex = new AtomicInteger();

        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CreateRadar-Targeting-" + this.threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class VisCache {
        boolean hasFrac = false;
        double fx;
        double fy;
        double fz;
        @Nullable
        Vec3 lastWorldPoint = null;
        int probeCursor = 0;
        int blockedStreak = 0;
        long lastTick = 0L;
        long lastReacquireTick = 0L;
    }

    private static enum RayResult {
        CLEAR,
        BLOCKED_BLOCK,
        BLOCKED_SAFEZONE;

        public boolean isClear() {
            return this == CLEAR;
        }

        // synthetic method
        private static RayResult[] $values() {
            return new RayResult[]{CLEAR, BLOCKED_BLOCK, BLOCKED_SAFEZONE};
        }
    }
}