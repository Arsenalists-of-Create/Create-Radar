package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
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
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.cbc.VelocityTracker;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.radarproxfuze.AdvancedProximityFuze;
import com.happysg.radar.targeting.AimSolution;
import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.ObstructionResult;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileSimulator;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetMotionClass;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
import com.happysg.radar.targeting.TargetingMath;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
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
    public final CannonMountBlockEntity cannonMount;
    public AutoPitchControllerBlockEntity pitchController;
    public AutoYawControllerBlockEntity yawController;
    public FireControllerBlockEntity fireController;
    public WeaponNetworkRuntime.WeaponGroupView view;
    public final Level level;
    private RadarTrack activetrack;
    private Entity targetEntity;
    private SubLevelAccess targetSublevel;
    private BlockPos binoTargetPos;
    private boolean binoMode;
    private UUID targetShipId;
    @Nullable
    private Vec3 lastAimPoint;
    private List<List<Double>> cachedSableAngles;
    private Vec3 cachedSableAimTarget;
    private long cachedSableSolveTick;
    private Double cachedSablePitchDeg;
    private Double cachedSableYawDeg;
    private static final int SABLE_SOLVE_INTERVAL = 3;
    private final Map<Integer, VisCache> visCache;
    double maxSimDistanceBlocks;
    private static final double NEW_SOLVER_MIN_CONFIDENCE = 0.05;
    private static final int TARGETING_DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final int MAX_NEW_SOLVER_FLIGHT_TICKS = 2400;
    private static final double CLOSE_TARGET_SOLVE_EVERY_TICK_BLOCKS = 64.0;
    private static final double EXTREME_TARGET_SPEED_MULTIPLIER = 2.0;
    private static final int ASYNC_TARGETING_RESULT_TTL_TICKS = 4;
    private static final int TARGETING_EXECUTOR_THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final int TARGETING_EXECUTOR_QUEUE_CAPACITY = 16;
    private static final ExecutorService TARGETING_EXECUTOR = createTargetingExecutor();
    private final TargetingComputer targetingComputer;
    private final TargetingComputer asyncTargetingComputer;
    private final ProjectileSimulator validationProjectileSimulator;
    private final ObstructionChecker mainThreadObstructionChecker;
    private long lastTargetingDebugLogTick;
    @Nullable
    private TargetingResult cachedTargetingResult;
    @Nullable
    private UUID cachedTargetingTargetId;
    @Nullable
    private Vec3 cachedTargetingSolvePos;
    private long cachedTargetingSolveTick;
    @Nullable
    private CompletableFuture<AsyncTargetingResult> pendingTargetingFuture;
    @Nullable
    private AsyncTargetingRequest pendingTargetingRequest;
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

    public WeaponFiringControl(AutoPitchControllerBlockEntity controller, CannonMountBlockEntity cannonMount, AutoYawControllerBlockEntity yawController) {
        this.targetingConfig = TargetingConfig.DEFAULT;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.targetShipId = null;
        this.lastAimPoint = null;
        this.cachedSableAngles = null;
        this.cachedSableAimTarget = null;
        this.cachedSableSolveTick = -1L;
        this.cachedSablePitchDeg = null;
        this.cachedSableYawDeg = null;
        this.visCache = new HashMap<>();
        this.maxSimDistanceBlocks = (double)8192.0F;
        this.targetingComputer = TargetingComputer.createDefault();
        this.asyncTargetingComputer = new TargetingComputer(null, new ProjectileSimulator(), null, null);
        this.validationProjectileSimulator = new ProjectileSimulator();
        this.mainThreadObstructionChecker = new ObstructionChecker();
        this.lastTargetingDebugLogTick = Long.MIN_VALUE;
        this.cachedTargetingResult = null;
        this.cachedTargetingTargetId = null;
        this.cachedTargetingSolvePos = null;
        this.cachedTargetingSolveTick = Long.MIN_VALUE;
        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;
        this.lastSableVelocityTargetId = null;
        this.lastSableVelocityTargetPos = null;
        this.lastSableVelocityTargetTick = Long.MIN_VALUE;
        this.lastSableVelocityPerTick = Vec3.ZERO;
        this.losSelectionCache = new HashMap<>();
        this.targetMotionStates = new HashMap<>();
        this.losPrefireCache = new LosCache();
        this.safeZones = new ArrayList<>();
        this.lastTargetTick = -1L;
        this.cannonMount = cannonMount;
        this.pitchController = controller;
        this.yawController = yawController;
        this.level = cannonMount.getLevel();
        LOGGER.debug("FiringControlBlockEntity.<init>() -> controller={} mountPos={}", controller, cannonMount.getBlockPos());
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
            if (hit.getType() != Type.MISS) {
                double hitDist = hit.getLocation().distanceTo(start);
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

    public Vec3 getCannonRayStart() {
        if (this.cannonMount == null) {
            return null;
        } else {
            PitchOrientedContraptionEntity poce = this.cannonMount.getContraption();
            if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(this.level, this.cannonMount.getBlockPos())) {
                if (poce != null) {
                    Vec3 shipyardPos = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 1.0F);
                    return SableUtils.getWorldVec(this.level, shipyardPos);
                } else {
                    return SableUtils.getWorldVec(this.level, this.cannonMount.getBlockPos().getCenter());
                }
            } else {
                return poce == null ? this.cannonMount.getBlockPos().getCenter() : poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 1.0F);
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
            if (c != null && now - c.lastTick < 3L) {
                Vec3 cached = null;
                if (c.hasFrac) {
                    cached = fracToWorld(this.inflatedAabb(f), c.fx, c.fy, c.fz);
                } else {
                    cached = c.lastWorldPoint;
                }

                if (cached != null && this.isPointInShootableRange(cached) && !this.isOutOfKnownRange(cached) && this.rayClear(start, cached).isClear()) {
                    return cached;
                }

                c.lastTick = 0L;
            }

            if (c == null) {
                c = new VisCache();
            }

            Vec3 vis = this.findVisiblePointOnEntityRotating(f, start, c, 10);
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
    private Vec3 findVisiblePointOnEntityRotating(Entity e, Vec3 start, VisCache cache, int budget) {
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
                if (this.isPointInShootableRange(cached) && !this.isOutOfKnownRange(cached) && this.rayClear(start, cached).isClear()) {
                    cache.blockedStreak = 0;
                    if (now - cache.lastReacquireTick >= 10L) {
                        cache.lastReacquireTick = now;
                        if (cached.distanceToSqr(center) > 1.0E-4 && this.isPointInShootableRange(center) && !this.isOutOfKnownRange(center) && this.rayClear(start, center).isClear()) {
                            worldToFrac(bb, center, cache);
                            cache.lastWorldPoint = center;
                            cache.probeCursor = 0;
                            return center;
                        }

                        if (cached.distanceToSqr(upper) > 1.0E-4 && this.isPointInShootableRange(upper) && !this.isOutOfKnownRange(upper) && this.rayClear(start, upper).isClear()) {
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
                    if (this.isPointInShootableRange(end) && !this.isOutOfKnownRange(end) && this.rayClear(start, end).isClear()) {
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
                } else if (!this.isPointInShootableRange(target)) {
                    return false;
                } else {
                    LOGGER.debug("LOS DBG: trackCat={} entityType={} height={} blocksHigh={} target={}", new Object[]{this.activetrack != null ? this.activetrack.trackCategory() : "null", this.activetrack != null ? this.activetrack.entityType() : "null", height, blocksHigh, target});

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

    public boolean hasLineOfSightTo(@Nullable RadarTrack track, boolean requireLos) {
        if (!this.isMountStateOk()) {
            return false;
        } else if (!requireLos) {
            return true;
        } else if (track == null) {
            return false;
        } else {
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
                    boolean shouldBeEntity = track.trackCategory() == TrackCategory.PLAYER || track.trackCategory() == TrackCategory.HOSTILE || track.trackCategory() == TrackCategory.ANIMAL || track.trackCategory() == TrackCategory.PROJECTILE || track.trackCategory() == TrackCategory.MISSILE;
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
        this.binoMode = false;
        this.binoTargetPos = null;
        this.target = null;
        this.activetrack = null;
        this.lastAimPoint = null;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.clearTargetingResultCache();
        this.stopFireCannon();
    }

    public void setSafeZones(List<SafeZone> safeZones) {
        LOGGER.debug("setSafeZones() -> {} zones", safeZones.size());
        this.safeZones = new ArrayList<>(safeZones);
    }

    public Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public SubLevelAccess getShipByUUID(ServerLevel level, String uuid) {
        return SubLevelContainer.getContainer(level).getSubLevel(UUID.fromString(uuid));
    }

    public void refreshControllers() {
        if (this.level instanceof ServerLevel serverLevel) {
            this.view = WeaponNetworkRuntime.get(serverLevel).getWeaponGroupViewFromEndpoint(this.pitchController.getBlockPos());
            if (this.view == null) {
                this.yawController = null;
                this.fireController = null;
                return;
            }
            if (this.view.yawPos() != null && this.level.getBlockEntity(this.view.yawPos()) instanceof AutoYawControllerBlockEntity autoyaw) {
                this.yawController = autoyaw;
            } else {
                this.yawController = null;
            }

            if (this.view.pitchPos() != null && this.level.getBlockEntity(this.view.pitchPos()) instanceof AutoPitchControllerBlockEntity autopitch) {
                this.pitchController = autopitch;
            } else {
                this.pitchController = null;
            }

            if (this.view.firingPos() != null && this.level.getBlockEntity(this.view.firingPos()) instanceof FireControllerBlockEntity firecont) {
                this.fireController = firecont;
                return;
            }

            this.fireController = null;
        }
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

    public void tick() {
        if (!this.isMountStateOk()) {
            this.stopFireCannon();
        } else {
            if (this.binoMode) {
                this.lastTargetTick = this.level.getGameTime();
            } else if (this.activetrack != null && (this.targetEntity != null || this.targetSublevel != null)) {
                this.lastTargetTick = this.level.getGameTime();
            }

            if (!this.binoMode && this.activetrack == null) {
                this.stopFireCannon();
            } else {
                if (!this.binoMode && this.activetrack != null) {
                    if (this.level instanceof ServerLevel sl) {
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
                            } catch (Throwable var42) {
                            }

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

                if (!this.binoMode && this.activetrack != null && this.targetEntity == null && this.targetSublevel == null) {
                    LOGGER.debug("WFC: no resolved target entity/ship, stopping fire (trackId={})", this.activetrack.id());
                    this.stopFireCannon();
                } else {
                    if (!this.binoMode) {
                        if (this.targetSublevel != null) {
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
                                if (this.targetEntity != null && !this.targetEntity.isAlive()) {
                                    LOGGER.debug("WFC: target entity died mid-tick, stopping fire (id={})", this.targetEntity.getUUID());
                                    this.stopFireCannon();
                                } else {
                                    if (this.targetSublevel != null && !this.binoMode) {
                                        UUID id = UUID.fromString(this.activetrack.id());
                                        SubLevelAccess live = SubLevelContainer.getContainer(serverLevel).getSubLevel(id);
                                        if (live == null) {
                                            LOGGER.debug("WFC: Sable ship id={} unloaded mid-tick, stopping fire", id);
                                            this.stopFireCannon();
                                            return;
                                        }

                                        this.targetSublevel = live;
                                    }

                                    Vec3 cannonMuzzleWorld = this.resolveMuzzleWorldPosition(cannon);
                                    Vec3 shooterVel = this.getPlatformVelocityAtMuzzle(serverLevel, cannonMuzzleWorld);
                                    this.getPlatformAcceleration(serverLevel, shooterVel);
                                    Vec3 targetVel;
                                    Vec3 targetAccel;
                                    Vec3 rawTargetPos;
                                    UUID targetMotionId = null;
                                    if (this.targetSublevel != null) {
                                        rawTargetPos = RadarTrackUtil.getPosition(this.targetSublevel);
                                        this.target = this.toWorldPosition(serverLevel, rawTargetPos, this.targetSublevel);
                                        targetVel = this.getSublevelTargetVelocityPerTick(serverLevel, this.targetSublevel, this.target);
                                        targetAccel = AccelerationTracker.getAccelerationPerTick2(this.targetSublevel.getUniqueId(), targetVel);
                                        targetMotionId = this.targetSublevel.getUniqueId();
                                    } else if (!this.binoMode && this.targetEntity != null) {
                                        rawTargetPos = this.getEntityAimPoint(this.targetEntity);
                                        this.target = this.toWorldPosition(serverLevel, rawTargetPos, this.targetEntity);
                                        targetVel = VelocityTracker.getEstimatedVelocityPerTick(this.targetEntity);
                                        targetAccel = AccelerationTracker.getAccelerationPerTick2(this.targetEntity.getUUID(), targetVel);
                                        targetMotionId = this.targetEntity.getUUID();
                                    } else {
                                        if (!this.binoMode || this.binoTargetPos == null) {
                                            return;
                                        }

                                        rawTargetPos = this.binoTargetPos.getCenter();
                                        this.target = this.toWorldPosition(serverLevel, this.binoTargetPos);
                                        targetVel = Vec3.ZERO;
                                        targetAccel = Vec3.ZERO;
                                    }

                                    TargetMotionEstimate motion = this.estimateTargetMotion(serverLevel, targetMotionId, this.targetEntity, this.target, targetVel, targetAccel);
                                    targetVel = motion.velocity();
                                    targetAccel = motion.acceleration();

                                    double dist = this.getCannonRayStart().distanceTo(this.target);
                                    double noLeadDist = (double)1.0F;
                                    Vec3 solvePos = this.target;
                                    if (!this.binoMode && this.targetEntity != null) {
                                        if (!this.checkLineOfSight(this.target)) {
                                            LOGGER.debug("WFC: LOS blocked to entity, stopping fire (id={})", this.targetEntity.getUUID());
                                            this.stopFireCannon();
                                            return;
                                        }

                                        solvePos = this.target;
                                    }

                                    boolean lag = !motion.looseAim();

                                    WeaponNetworkRuntime.WeaponGroupView grp = this.pitchController != null
                                            ? WeaponNetworkRuntime.get(serverLevel).getWeaponGroupViewFromEndpoint(this.pitchController.getBlockPos())
                                            : null;
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

                                    int trackingLeadTicks = this.targetTrackingLeadTicks(motion.motionClass());
                                    Vec3 trackingSolvePos = predictTarget(solvePos, targetVel, targetAccel, (double)trackingLeadTicks, motion.motionClass(), (double)-0.08F);
                                    CannonLead.LeadSolution lead = null;
                                    TargetingResult targetingResult = null;
                                    boolean forceLegacyLead = (Boolean)RadarConfig.server().forceLegacyCannonLeadSolver.get();
                                    boolean useNewSolver = !forceLegacyLead && (Boolean)RadarConfig.server().useNewTargetingComputer.get() && !CannonUtil.isLaserCannon(cannon);
                                    if (useNewSolver) {
                                        targetingResult = this.pollCompletedAsyncTargetingResult(serverLevel, cannon, targetMotionId, solvePos);
                                        boolean completedAsyncResult = targetingResult != null;
                                        boolean solveEveryTick = this.shouldSolveEveryTick(targetVel, motion, dist);
                                        if (targetingResult == null && this.shouldReuseTargetingResult(serverLevel, targetMotionId, solvePos, targetVel, motion, dist)) {
                                            targetingResult = this.cachedTargetingResult;
                                        }

                                        if ((!completedAsyncResult && targetingResult == null) || (completedAsyncResult && solveEveryTick)) {
                                            this.submitAsyncTargetingSolve(serverLevel, cannon, rawTargetPos, solvePos, targetVel, targetAccel, shooterVel, this.targetEntity, this.targetSublevel, motion.motionClass(), trackingLeadTicks, targetMotionId);
                                        }
                                    }

                                    boolean newSolverOk = targetingResult != null && targetingResult.valid() && targetingResult.hasShot() && targetingResult.confidence() >= motion.minConfidence();
                                    if (forceLegacyLead && !CannonUtil.isLaserCannon(cannon) && dist > noLeadDist) {
                                        lead = CannonLead.solveLeadPerTickConstantVelocity(this.cannonMount, cannon, serverLevel, shooterVel, solvePos, targetVel, (Integer)RadarConfig.server().leadFiringDelay.get() + trackingLeadTicks, this.maxSimDistanceBlocks, this.targetingConfig.preferHighArc());
                                    }

                                    boolean hasLeadSolution = lead != null && lead.aimPoint != null;
                                    boolean hasNewTargetingSolution = newSolverOk && targetingResult.aimSolution() != null;
                                    boolean canFireWithoutLead = CannonUtil.isLaserCannon(cannon);
                                    Vec3 offsetAim = hasNewTargetingSolution && targetingResult.aimSolution().aimPoint() != null ? targetingResult.aimSolution().aimPoint() : (hasLeadSolution ? lead.aimPoint : trackingSolvePos);
                                    this.lastAimPoint = offsetAim;
                                    if (this.lastOffsetAim != null && !(this.lastOffsetAim.distanceTo(offsetAim) > motion.aimStableEps())) {
                                        ++this.aimStableTicks;
                                    } else {
                                        this.aimStableTicks = 0;
                                        this.lastOffsetAim = offsetAim;
                                    }

                                    Double desiredPitch = null;
                                    Double desiredYaw = null;
                                    if (Mods.SABLE.isLoaded() && PhysicsHandler.isBlockInPlotyard(this.level, this.cannonMount.getBlockPos())) {
                                        if (hasNewTargetingSolution) {
                                            AimCommand command = this.mountAimCommand(cannon, targetingResult.aimSolution().aimDirection());
                                            if (command != null) {
                                                desiredPitch = command.pitchDeg();
                                                desiredYaw = command.controllerYawDeg();
                                                this.cachedSablePitchDeg = desiredPitch;
                                                this.cachedSableYawDeg = desiredYaw;
                                            } else {
                                                hasNewTargetingSolution = false;
                                            }
                                        } else if (hasLeadSolution || canFireWithoutLead) {
                                            long now = this.level.getGameTime();
                                            int sableSolveInterval = motion.motionClass() == TargetMotionClass.ERRATIC ? 1 : SABLE_SOLVE_INTERVAL;
                                            boolean needSolve = this.cachedSableAngles == null || now - this.cachedSableSolveTick >= (long)sableSolveInterval || this.cachedSableAimTarget == null || this.cachedSableAimTarget.distanceToSqr(offsetAim) > 0.09;
                                            if (needSolve) {
                                                this.cachedSableAngles = VS2CannonTargeting.calculatePitchAndYawVS2(this.cannonMount, offsetAim, serverLevel, this.cachedSablePitchDeg, this.cachedSableYawDeg);
                                                this.cachedSableAimTarget = offsetAim;
                                                this.cachedSableSolveTick = now;
                                            }

                                            List<List<Double>> angles = this.cachedSableAngles;
                                            if (angles != null && !angles.isEmpty()) {
                                                List<Double> firstAngles = angles.get(0);
                                                if (!firstAngles.isEmpty()) {
                                                    desiredPitch = firstAngles.get(0);
                                                    desiredYaw = firstAngles.get(1);
                                                    this.cachedSablePitchDeg = desiredPitch;
                                                    this.cachedSableYawDeg = desiredYaw;
                                                }
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
                                        }
                                    }

                                    if (desiredPitch != null && this.pitchController != null) {
                                        this.pitchController.setTargetAngle(desiredPitch.floatValue());
                                    }

                                    if (desiredYaw != null && this.yawController != null) {
                                        this.yawController.setTargetAngle(desiredYaw.floatValue());
                                    }

                                    boolean auto = this.targetingConfig.autoFire();
                                    boolean yawPitchOk = this.hasCorrectYawPitch(lag);
                                    boolean safeOk = !this.passesSafeZone();
                                    boolean cannonReady = CannonUtil.isCannonReadyToFire(this.cannonMount);
                                    boolean stableOk = this.aimStableTicks >= motion.stableTicksRequired();
                                    if (this.level.getGameTime() % 20L == 0L) {
                                        LOGGER.debug("WFC FIREGATES: auto={} lead={} newSolver={} newConf={} minConf={} laserNoLead={} yawPitchOk={} safeOk={} cannonReady={} stableOk={} firingBE={} target={} aim={} offset={} stable={}/{} motion={} jerk={} eps={} reason={}", new Object[]{auto, hasLeadSolution, hasNewTargetingSolution, targetingResult != null ? targetingResult.confidence() : null, motion.minConfidence(), canFireWithoutLead, yawPitchOk, safeOk, cannonReady, stableOk, this.fireController != null, this.target, offsetAim, this.offset, this.aimStableTicks, motion.stableTicksRequired(), motion.motionClass(), motion.jerk(), motion.aimStableEps(), motion.reason()});
                                        if (!yawPitchOk) {
                                            LOGGER.debug("WFC AIMCHK: yawCtrl={} pitchCtrl={} atYaw={} atPitch={} targYaw={} targPitch={}", new Object[]{this.yawController != null ? this.yawController.getBlockPos() : null, this.pitchController != null ? this.pitchController.getBlockPos() : null, this.yawController != null && this.yawController.atTargetYaw(lag), this.pitchController != null && this.pitchController.atTargetPitch(lag), this.yawController != null ? this.yawController.getTargetAngle() : null, this.pitchController != null ? this.pitchController.getTargetAngle() : null});
                                        }

                                        if (!auto) {
                                            LOGGER.debug("WFC BLOCK: autoFire disabled");
                                        }

                                        if (!hasLeadSolution && !hasNewTargetingSolution && !canFireWithoutLead) {
                                            LOGGER.debug("WFC BLOCK: no lead solution");
                                        }

                                        if (!safeOk) {
                                            LOGGER.debug("WFC BLOCK: safe zone violation");
                                        }

                                        if (!cannonReady) {
                                            LOGGER.debug("WFC BLOCK: cannon not ready");
                                        }

                                        if (!yawPitchOk) {
                                            LOGGER.debug("WFC BLOCK: yaw/pitch not aligned");
                                        }

                                        if (!stableOk) {
                                            LOGGER.debug("WFC BLOCK: aim not stable");
                                        }
                                    }

                                    boolean shouldFire = this.targetingConfig.autoFire() && (hasLeadSolution || hasNewTargetingSolution || canFireWithoutLead) && yawPitchOk && safeOk && cannonReady && stableOk;
                                    if (this.fireController != null) {
                                        if (shouldFire) {
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

    public void resetTarget() {
        this.visCache.clear();
        this.target = null;
        this.activetrack = null;
        this.targetEntity = null;
        this.targetSublevel = null;
        this.targetShipId = null;
        this.lastAimPoint = null;
        this.lastOffsetAim = null;
        this.aimStableTicks = 0;
        this.cachedSableAngles = null;
        this.cachedSableAimTarget = null;
        this.cachedSableSolveTick = -1L;
        this.cachedSablePitchDeg = null;
        this.cachedSableYawDeg = null;
        this.clearTargetingResultCache();
        this.stopFireCannon();
    }

    public void setTarget(Vec3 target, TargetingConfig config, RadarTrack track, WeaponNetworkRuntime.WeaponGroupView view) {
        LOGGER.debug("setTarget() -> new target={} config={} atTick={}", new Object[]{target, config, this.level != null ? this.level.getGameTime() : -1L});
        if (target == null) {
            this.target = null;
            this.activetrack = null;
            this.targetEntity = null;
            this.targetSublevel = null;
            this.targetShipId = null;
            this.lastAimPoint = null;
            this.lastOffsetAim = null;
            this.aimStableTicks = 0;
            this.clearTargetingResultCache();
            this.stopFireCannon();
        } else {
            this.binoMode = false;
            this.target = target;
            this.lastOffsetAim = null;
            this.aimStableTicks = 0;
            this.clearTargetingResultCache();
            this.targetingConfig = config;
            if (this.level != null) {
                this.lastTargetTick = this.level.getGameTime();
            }

            this.view = view;
            this.activetrack = track;
            this.targetEntity = null;
            this.targetSublevel = null;
        }
    }

    public void setBinoTarget(@Nullable BlockPos binoTarget, TargetingConfig config, WeaponNetworkRuntime.WeaponGroupView view, boolean reset) {
        this.view = view;
        this.targetingConfig = config;
        this.activetrack = null;
        if (!reset && binoTarget != null) {
            this.binoMode = true;
            this.binoTargetPos = binoTarget.immutable();
            this.clearTargetingResultCache();
            if (this.level != null) {
                this.lastTargetTick = this.level.getGameTime();
            }

        } else {
            this.binoMode = false;
            this.binoTargetPos = null;
            this.target = null;
            this.clearTargetingResultCache();
            this.stopFireCannon();
        }
    }

    private boolean shouldReuseTargetingResult(ServerLevel serverLevel, @Nullable UUID targetMotionId, Vec3 solvePos, Vec3 targetVel, TargetMotionEstimate motion, double distanceToTarget) {
        if (this.cachedTargetingResult == null || !this.cachedTargetingResult.valid() || !this.cachedTargetingResult.hasShot()) {
            return false;
        }

        long now = serverLevel.getGameTime();
        if (now - this.cachedTargetingSolveTick != 1L) {
            return false;
        }

        if (this.shouldSolveEveryTick(targetVel, motion, distanceToTarget)) {
            return false;
        }

        if (targetMotionId != null || this.cachedTargetingTargetId != null) {
            if (targetMotionId == null || !targetMotionId.equals(this.cachedTargetingTargetId)) {
                return false;
            }
        }

        return this.cachedTargetingSolvePos != null && solvePos.distanceToSqr(this.cachedTargetingSolvePos) <= (double)4.0F;
    }

    private boolean shouldSolveEveryTick(Vec3 targetVel, TargetMotionEstimate motion, double distanceToTarget) {
        if (motion.motionClass() == TargetMotionClass.ERRATIC || distanceToTarget <= CLOSE_TARGET_SOLVE_EVERY_TICK_BLOCKS) {
            return true;
        }

        double fastThreshold = (Double)RadarConfig.server().targetLoosenThreshold.get();
        double extremeSpeedMps = Math.max((double)6.0F, fastThreshold * EXTREME_TARGET_SPEED_MULTIPLIER);
        return targetVel.length() * (double)20.0F >= extremeSpeedMps;
    }

    private void storeTargetingResult(ServerLevel serverLevel, @Nullable UUID targetMotionId, Vec3 solvePos, @Nullable TargetingResult targetingResult) {
        if (targetingResult != null && targetingResult.valid() && targetingResult.hasShot()) {
            this.cachedTargetingResult = targetingResult;
            this.cachedTargetingTargetId = targetMotionId;
            this.cachedTargetingSolvePos = solvePos;
            this.cachedTargetingSolveTick = serverLevel.getGameTime();
        } else {
            this.clearTargetingResultCache();
        }
    }

    private void clearTargetingResultCache() {
        this.cachedTargetingResult = null;
        this.cachedTargetingTargetId = null;
        this.cachedTargetingSolvePos = null;
        this.cachedTargetingSolveTick = Long.MIN_VALUE;
        if (this.pendingTargetingFuture != null) {
            this.pendingTargetingFuture.cancel(true);
        }
        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;
    }

    @Nullable
    private TargetingResult pollCompletedAsyncTargetingResult(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, @Nullable UUID targetMotionId, Vec3 solvePos) {
        CompletableFuture<AsyncTargetingResult> future = this.pendingTargetingFuture;
        if (future == null || !future.isDone()) {
            return null;
        }

        this.pendingTargetingFuture = null;
        this.pendingTargetingRequest = null;

        try {
            AsyncTargetingResult asyncResult = future.getNow(null);
            if (asyncResult == null || !this.isAsyncResultFresh(serverLevel, asyncResult.request(), targetMotionId, solvePos)) {
                return null;
            }

            TargetingResult validated = this.validateAsyncTargetingResult(serverLevel, cannonContraption, asyncResult.request(), asyncResult.result());
            this.storeTargetingResult(serverLevel, targetMotionId, solvePos, validated);
            return validated;
        } catch (Throwable throwable) {
            LOGGER.warn("WFC TargetingComputer async solve failed", throwable);
            return null;
        }
    }

    private boolean isAsyncResultFresh(ServerLevel serverLevel, AsyncTargetingRequest request, @Nullable UUID targetMotionId, Vec3 solvePos) {
        long age = serverLevel.getGameTime() - request.requestTick();
        if (age < 0L || age > (long)ASYNC_TARGETING_RESULT_TTL_TICKS) {
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

        return solvePos != null && solvePos.distanceToSqr(request.solvePos()) <= (double)4.0F;
    }

    private void submitAsyncTargetingSolve(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, Vec3 rawTargetPos, Vec3 targetWorldPos, Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel, @Nullable Entity targetEntity, @Nullable SubLevelAccess targetSublevel, TargetMotionClass targetMotionClass, int trackingLeadTicks, @Nullable UUID targetMotionId) {
        if (this.pendingTargetingFuture != null && !this.pendingTargetingFuture.isDone()) {
            AsyncTargetingRequest pending = this.pendingTargetingRequest;
            long age = pending == null ? 0L : serverLevel.getGameTime() - pending.requestTick();
            boolean differentTarget = pending != null && (targetMotionId != null || pending.targetId() != null)
                    && (targetMotionId == null || !targetMotionId.equals(pending.targetId()));
            boolean movedTooFar = pending != null && targetWorldPos.distanceToSqr(pending.solvePos()) > 4.0;
            if (age < ASYNC_TARGETING_RESULT_TTL_TICKS && !differentTarget && !movedTooFar) {
                return;
            }
            this.pendingTargetingFuture.cancel(true);
            this.pendingTargetingFuture = null;
            this.pendingTargetingRequest = null;
        }

        AsyncTargetingRequest request = this.buildAsyncTargetingRequest(serverLevel, cannonContraption, rawTargetPos, targetWorldPos, targetVel, targetAccel, shooterVel, targetEntity, targetSublevel, targetMotionClass, trackingLeadTicks, targetMotionId);
        if (request == null) {
            return;
        }

        try {
            this.pendingTargetingRequest = request;
            this.pendingTargetingFuture = CompletableFuture.supplyAsync(() -> new AsyncTargetingResult(request, this.asyncTargetingComputer.solve(request.snapshot())), TARGETING_EXECUTOR);
        } catch (RejectedExecutionException rejected) {
            this.pendingTargetingRequest = null;
            this.pendingTargetingFuture = null;
            LOGGER.debug("WFC TargetingComputer async solve queue is full; skipping request for mount={} target={}", request.mountPos(), request.targetId());
        }
    }

    @Nullable
    private AsyncTargetingRequest buildAsyncTargetingRequest(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, Vec3 rawTargetPos, Vec3 targetWorldPos, Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel, @Nullable Entity targetEntity, @Nullable SubLevelAccess targetSublevel, TargetMotionClass targetMotionClass, int trackingLeadTicks, @Nullable UUID targetMotionId) {
        if (serverLevel == null || cannonContraption == null || targetWorldPos == null) {
            return null;
        }

        Vec3 rawMuzzlePos = this.getCannonRayStart();
        Vec3 muzzleWorldPos = this.resolveMuzzleWorldPosition(cannonContraption);
        if (muzzleWorldPos == null) {
            return null;
        }

        boolean cbcPhysics = CannonUtil.isBigCannon(cannonContraption);
        CannonUtil.BigCannonShotState bigCannonShotState = cbcPhysics ? CannonUtil.resolveBigCannonShotState(cannonContraption, serverLevel) : null;
        double projectileSpeed = bigCannonShotState != null ? bigCannonShotState.speed() : (double)CannonUtil.getInitialVelocity(cannonContraption, serverLevel);
        if (!Double.isFinite(projectileSpeed) || projectileSpeed <= (double)0.0F) {
            return null;
        }

        BallisticPropertiesComponent ballistics = bigCannonShotState != null ? bigCannonShotState.ballistics() : CannonUtil.getBallistics(cannonContraption, serverLevel);
        double gravity = ballistics != null ? ballistics.gravity() : CannonUtil.getProjectileGravity(cannonContraption, serverLevel);
        double drag = ballistics != null ? ballistics.drag() : CannonUtil.getProjectileDrag(cannonContraption, serverLevel);
        double dragDensity = (double)1.0F;
        if (!cbcPhysics) {
            CannonUtil.logCannonTypeReadFailure("async targeting snapshot", cannonContraption);
        }
        if (cbcPhysics) {
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(serverLevel);
            gravity *= dimension.gravityMultiplier();
            dragDensity = dimension.dragMultiplier();
        }
        if (!Double.isFinite(gravity)) {
            gravity = -0.05;
        }

        if (!Double.isFinite(drag)) {
            drag = 0.01;
        }

        Vec3 safeTargetVel = finiteOrZero(targetVel);
        Vec3 safeTargetAccel = clampAcceleration(finiteOrZero(targetAccel));
        Vec3 safeShooterVel = finiteOrZero(shooterVel);
        int predictedTicks = this.predictedAsyncSolveTicks(serverLevel, cannonContraption, trackingLeadTicks);
        Vec3 delayedTargetPos = predictTarget(targetWorldPos, safeTargetVel, safeTargetAccel, (double)predictedTicks, targetMotionClass, gravity);
        AABB targetAabb = targetEntity != null ? this.resolveEntityWorldAabb(serverLevel, targetEntity) : this.resolveSublevelWorldAabb(targetSublevel);
        if (targetAabb != null && predictedTicks > 0) {
            targetAabb = targetAabb.move(delayedTargetPos.subtract(targetWorldPos));
        }

        if (this.shouldLogTargetingDebug(serverLevel)) {
            this.logTargetingCoordinateResolution(serverLevel, rawTargetPos, targetWorldPos, rawMuzzlePos, muzzleWorldPos, safeShooterVel);
            if (bigCannonShotState != null) {
                LOGGER.warn("WFC async big cannon shot state: speed={} projectile={} projectileLocal={} muzzleExit={} muzzleOffset={} gravity={} drag={} quadratic={} reason={}",
                        bigCannonShotState.speed(), bigCannonShotState.projectileClass(), bigCannonShotState.projectileLocalPos(),
                        bigCannonShotState.muzzleExitLocalPos(), bigCannonShotState.muzzleForwardOffset(),
                        gravity, drag, ballistics != null && ballistics.isQuadraticDrag(), bigCannonShotState.reason());
            }
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
        TargetingSnapshot snapshot = TargetingSnapshot.builder(serverLevel).muzzlePosition(muzzleWorldPos).inheritedVelocity(safeShooterVel).targetPosition(delayedTargetPos).targetVelocity(safeTargetVel).targetAcceleration(safeTargetAccel).targetAabb(targetAabb).projectileSpeed(projectileSpeed).gravity(gravity).drag(drag).quadraticDrag(ballistics != null && ballistics.isQuadraticDrag()).cbcPhysics(cbcPhysics).dragDensity(dragDensity).maxFlightTicks(this.computeNewSolverMaxFlightTicks(projectileSpeed, muzzleWorldPos, delayedTargetPos)).gameTime(serverLevel.getGameTime() + (long)predictedTicks).preferredYawDeg(preferredYaw).preferredPitchDeg(preferredPitch).currentYawDeg(this.currentSolverYawDeg()).currentPitchDeg(this.currentPitchDeg(cannonContraption)).targetSublevelId(targetSublevel != null ? targetSublevel.getUniqueId() : null).targetMotionClass(targetMotionClass).pitchConstraint(this.effectivePitchConstraint(cannonContraption)).preferHighArc(this.targetingConfig.preferHighArc()).build();
        return new AsyncTargetingRequest(this.cannonMount.getBlockPos().immutable(), targetMotionId, targetWorldPos, serverLevel.getGameTime(), snapshot);
    }

    private int predictedAsyncSolveTicks(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, int trackingLeadTicks) {
        int predictedTicks = Math.max(0, trackingLeadTicks);
        TargetingResult previous = this.cachedTargetingResult;
        if (previous != null && previous.valid() && previous.hasShot()) {
            Vec3 aimPoint = previous.aimSolution() != null && previous.aimSolution().aimPoint() != null ? previous.aimSolution().aimPoint() : null;
            if (aimPoint != null) {
                predictedTicks = this.estimateSlewTicksForSolution(cannonContraption, previous.aimSolution(), previous.desiredYawDeg() + (double)270.0F, previous.desiredPitchDeg()) + Math.max(0, trackingLeadTicks);
            }
        }

        return Math.max(0, Math.min(60, predictedTicks));
    }

    @Nullable
    private Double preferredYaw(@Nullable Double previousAngle) {
        if (previousAngle != null && Double.isFinite(previousAngle)) {
            return previousAngle;
        }
        if (this.isSableMount() || this.yawController == null) {
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
        if (this.isSableMount() || this.pitchController == null) {
            return null;
        }
        double angle = this.pitchController.getTargetAngle();
        return Double.isFinite(angle) ? angle : null;
    }

    @Nullable
    private TargetingResult validateAsyncTargetingResult(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, AsyncTargetingRequest request, @Nullable TargetingResult result) {
        if (result == null || !result.valid() || !result.hasShot() || result.aimSolution() == null) {
            return null;
        }

        AimSolution aim = result.aimSolution();
        Vec3 direction = aim.aimDirection();
        if (direction == null || direction.lengthSqr() < 1.0E-12) {
            return null;
        }

        TargetingSnapshot snapshot = request.snapshot();
        ProjectileModel model = snapshot.cbcPhysics()
                ? ProjectileModel.cbc(snapshot.projectileSpeed(), snapshot.gravity(), snapshot.drag(), snapshot.dragDensity(), snapshot.quadraticDrag())
                : ProjectileModel.simple(snapshot.projectileSpeed(), snapshot.gravity(), snapshot.drag(), snapshot.quadraticDrag());
        int validationTicks = Math.max(1, Math.min(snapshot.maxFlightTicks(), result.predictedFlightTicks() + 1));
        ProjectileSimulator.SimulationResult trajectory = this.validationProjectileSimulator.simulate(snapshot.muzzlePosition(), direction, snapshot.inheritedVelocity(), model, validationTicks, snapshot.level());
        ObstructionResult obstruction = this.mainThreadObstructionChecker.check(serverLevel, trajectory, Math.max(0, result.predictedFlightTicks()));
        if (obstruction.clear() || this.isValidatedObstructionAtTarget(snapshot, obstruction)) {
            return result;
        }

        TargetingResult fallback = this.targetingComputer.solve(snapshot);
        return fallback != null && fallback.valid() && fallback.hasShot() ? fallback : null;
    }

    private boolean isValidatedObstructionAtTarget(TargetingSnapshot snapshot, ObstructionResult obstruction) {
        if (!obstruction.blocked() || obstruction.blockedPosition() == null) {
            return false;
        }

        int blockedTick = Math.max(0, obstruction.blockedTick());
        Vec3 targetAtBlock = predictTarget(snapshot.targetPosition(), snapshot.targetVelocity(), snapshot.targetAcceleration(), (double)blockedTick, snapshot.targetMotionClass(), snapshot.gravity());
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
    private TargetingResult solveWithTargetingComputer(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, Vec3 rawTargetPos, Vec3 targetWorldPos, Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel, @Nullable Entity targetEntity, @Nullable SubLevelAccess targetSublevel, TargetMotionClass targetMotionClass, int trackingLeadTicks) {
        if (serverLevel != null && cannonContraption != null && targetWorldPos != null) {
            Vec3 rawMuzzlePos = this.getCannonRayStart();
            Vec3 muzzleWorldPos = this.resolveMuzzleWorldPosition(cannonContraption);
            if (muzzleWorldPos == null) {
                return null;
            } else {
                boolean cbcPhysics = CannonUtil.isBigCannon(cannonContraption);
                CannonUtil.BigCannonShotState bigCannonShotState = cbcPhysics ? CannonUtil.resolveBigCannonShotState(cannonContraption, serverLevel) : null;
                double projectileSpeed = bigCannonShotState != null ? bigCannonShotState.speed() : (double)CannonUtil.getInitialVelocity(cannonContraption, serverLevel);
                if (Double.isFinite(projectileSpeed) && !(projectileSpeed <= (double)0.0F)) {
                    BallisticPropertiesComponent ballistics = bigCannonShotState != null ? bigCannonShotState.ballistics() : CannonUtil.getBallistics(cannonContraption, serverLevel);
                    double gravity = ballistics != null ? ballistics.gravity() : CannonUtil.getProjectileGravity(cannonContraption, serverLevel);
                    double drag = ballistics != null ? ballistics.drag() : CannonUtil.getProjectileDrag(cannonContraption, serverLevel);
                    double dragDensity = (double)1.0F;
                    if (!cbcPhysics) {
                        CannonUtil.logCannonTypeReadFailure("targeting snapshot", cannonContraption);
                    }
                    if (cbcPhysics) {
                        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(serverLevel);
                        gravity *= dimension.gravityMultiplier();
                        dragDensity = dimension.dragMultiplier();
                    }
                    if (!Double.isFinite(gravity)) {
                        gravity = -0.05;
                    }

                    if (!Double.isFinite(drag)) {
                        drag = 0.01;
                    }

                    Vec3 safeTargetVel = finiteOrZero(targetVel);
                    Vec3 safeTargetAccel = clampAcceleration(finiteOrZero(targetAccel));
                    Vec3 safeShooterVel = finiteOrZero(shooterVel);
                    boolean logTargetingDebug = this.shouldLogTargetingDebug(serverLevel);
                    if (logTargetingDebug) {
                        this.logTargetingCoordinateResolution(serverLevel, rawTargetPos, targetWorldPos, rawMuzzlePos, muzzleWorldPos, safeShooterVel);
                        if (bigCannonShotState != null) {
                            LOGGER.warn("WFC big cannon shot state: speed={} projectile={} projectileLocal={} muzzleExit={} muzzleOffset={} gravity={} drag={} quadratic={} reason={}",
                                    bigCannonShotState.speed(), bigCannonShotState.projectileClass(), bigCannonShotState.projectileLocalPos(),
                                    bigCannonShotState.muzzleExitLocalPos(), bigCannonShotState.muzzleForwardOffset(),
                                    gravity, drag, ballistics != null && ballistics.isQuadraticDrag(), bigCannonShotState.reason());
                        }
                    }

                    AABB baseTargetAabb = targetEntity != null ? this.resolveEntityWorldAabb(serverLevel, targetEntity) : this.resolveSublevelWorldAabb(targetSublevel);
                    int predictedTicks = Math.max(0, trackingLeadTicks);
                    TargetingResult result = null;
                    for(int i = 0; i < 3; ++i) {
                        Vec3 delayedTargetPos = predictTarget(targetWorldPos, safeTargetVel, safeTargetAccel, (double)predictedTicks, targetMotionClass, gravity);
                        AABB targetAabb = baseTargetAabb;
                        if (targetAabb != null && predictedTicks > 0) {
                            targetAabb = targetAabb.move(delayedTargetPos.subtract(targetWorldPos));
                        }

                        TargetingSnapshot snapshot = TargetingSnapshot.builder(serverLevel).muzzlePosition(muzzleWorldPos).inheritedVelocity(safeShooterVel).targetPosition(delayedTargetPos).targetVelocity(safeTargetVel).targetAcceleration(safeTargetAccel).targetAabb(targetAabb).projectileSpeed(projectileSpeed).gravity(gravity).drag(drag).quadraticDrag(ballistics != null && ballistics.isQuadraticDrag()).cbcPhysics(cbcPhysics).dragDensity(dragDensity).maxFlightTicks(this.computeNewSolverMaxFlightTicks(projectileSpeed, muzzleWorldPos, delayedTargetPos)).gameTime(serverLevel.getGameTime() + (long)predictedTicks).preferredYawDeg(this.preferredYaw(null)).preferredPitchDeg(this.preferredPitch(null)).currentYawDeg(this.currentSolverYawDeg()).currentPitchDeg(this.currentPitchDeg(cannonContraption)).targetSublevelId(targetSublevel != null ? targetSublevel.getUniqueId() : null).targetMotionClass(targetMotionClass).pitchConstraint(this.effectivePitchConstraint(cannonContraption)).preferHighArc(this.targetingConfig.preferHighArc()).build();
                        result = this.targetingComputer.solve(snapshot);
                        if (result == null || !result.valid() || !result.hasShot()) {
                            break;
                        }

                        Vec3 aimPoint = result.aimSolution() != null && result.aimSolution().aimPoint() != null ? result.aimSolution().aimPoint() : delayedTargetPos;
                        int nextTicks = this.estimateSlewTicksForSolution(cannonContraption, result.aimSolution(), result.desiredYawDeg() + (double)270.0F, result.desiredPitchDeg()) + Math.max(0, trackingLeadTicks);
                        nextTicks = Math.max(0, Math.min(60, nextTicks));
                        if (Math.abs(nextTicks - predictedTicks) <= 1) {
                            predictedTicks = nextTicks;
                            break;
                        }

                        predictedTicks = nextTicks;
                    }

                    if (this.level.getGameTime() % 20L == 1L && result != null) {
                        LOGGER.warn("WFC TargetingComputer trackingLead={} {}", predictedTicks, result.debugString());
                    }

                    return result;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    @Nullable
    private Vec3 resolveMuzzleWorldPosition(AbstractMountedCannonContraption cannon) {
        if (cannon instanceof MountedBigCannonContraption && this.cannonMount != null && this.cannonMount.getContraption() != null && !this.isSableMount()) {
            return CBCMuzzleUtil.getCBCSpawnAnchorWorld(this.cannonMount.getContraption());
        }
        return this.getCannonRayStart();
    }

    public SolverDebugReport buildSolverDebugReport(ServerLevel serverLevel, int arcTicks) {
        List<String> lines = new ArrayList<>();
        ProjectileSimulator.SimulationResult trajectory = null;
        PitchOrientedContraptionEntity mounted = this.cannonMount == null ? null : this.cannonMount.getContraption();
        AbstractMountedCannonContraption cannon = mounted != null && mounted.getContraption() instanceof AbstractMountedCannonContraption c ? c : null;
        Vec3 rawMuzzlePos = this.getCannonRayStart();
        Vec3 muzzleWorldPos = cannon == null ? rawMuzzlePos : this.resolveMuzzleWorldPosition(cannon);
        Vec3 targetPos = this.target;
        Vec3 aimPoint = this.lastAimPoint;
        TargetingResult result = this.cachedTargetingResult;

        lines.add("=== Create Radar Cannon Solver Debug ===");
        lines.add("mount=" + (this.cannonMount == null ? "<null>" : this.cannonMount.getBlockPos().toShortString())
                + " dim=" + (serverLevel == null ? "<null>" : serverLevel.dimension().location()));
        lines.add("detected cannon type=" + describeCannonType(cannon));
        lines.add("raw mount ray origin=" + fmtVec(rawMuzzlePos) + " resolved muzzle=" + fmtVec(muzzleWorldPos));
        PitchConstraint effectivePitch = cannon == null ? PitchConstraint.unconstrained() : this.effectivePitchConstraint(cannon);
        lines.add("effective pitch limits=" + effectivePitch.summary());
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
        String shellType = "<unknown>";
        String propellant = "<unknown>";
        String ballisticsReason = "<none>";

        if (cannon != null && serverLevel != null) {
            CannonUtil.BigCannonShotState bigShot = cbcPhysics ? CannonUtil.resolveBigCannonShotState(cannon, serverLevel) : null;
            BallisticPropertiesComponent ballistics = bigShot != null ? bigShot.ballistics() : CannonUtil.getBallistics(cannon, serverLevel);
            projectileSpeed = bigShot != null ? bigShot.speed() : (double) CannonUtil.getInitialVelocity(cannon, serverLevel);
            if (ballistics != null) {
                gravity = ballistics.gravity();
                drag = ballistics.drag();
                quadraticDrag = ballistics.isQuadraticDrag();
            }
            if (cbcPhysics) {
                DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(serverLevel);
                gravity *= dimension.gravityMultiplier();
                dragDensity = dimension.dragMultiplier();
            }
            if (bigShot != null) {
                shellType = bigShot.projectileClass() == null ? "<none detected; using HE fallback ballistics>" : bigShot.projectileClass();
                propellant = bigShot.propellantCharges() + " charges, propellantPower=" + fmt(bigShot.propellantPower())
                        + ", projectileAddedPower=" + fmt(bigShot.projectileAddedPower());
                ballisticsReason = bigShot.reason();
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
        lines.add("expected pitch=" + fmt(expectedPitch == null ? Double.NaN : expectedPitch)
                + " expected solver yaw=" + fmt(expectedYaw == null ? Double.NaN : expectedYaw)
                + " controller yaw=" + (this.yawController == null ? "<none>" : fmt(this.yawController.getTargetAngle()))
                + " controller pitch=" + (this.pitchController == null ? "<none>" : fmt(this.pitchController.getTargetAngle())));

        if (result != null) {
            lines.add("cached solver result: valid=" + result.valid()
                    + " hasShot=" + result.hasShot()
                    + " flightTicks=" + result.predictedFlightTicks()
                    + " miss=" + fmt(result.missDistance())
                    + " confidence=" + fmt(result.confidence())
                    + " reason=" + result.reason());
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
            ProjectileModel model = cbcPhysics
                    ? ProjectileModel.cbc(projectileSpeed, gravity, drag, dragDensity, quadraticDrag)
                    : ProjectileModel.simple(projectileSpeed, gravity, drag, quadraticDrag);
            int ticks = Math.max(20, Math.min(400, arcTicks));
            trajectory = this.validationProjectileSimulator.simulate(muzzleWorldPos, direction, Vec3.ZERO, model, ticks, serverLevel);
            lines.add("arc samples=" + trajectory.samples().size() + " ticks=" + ticks);
        } else {
            lines.add("arc unavailable: missing muzzle/direction/valid projectile model");
        }

        String joined = String.join(" | ", lines);
        LOGGER.warn(joined);
        return new SolverDebugReport(lines, trajectory);
    }

    private static String describeCannonType(@Nullable AbstractMountedCannonContraption cannon) {
        if (cannon == null) {
            return "<none>";
        }
        String family = CannonUtil.isBigCannon(cannon) ? "big_cannon"
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
        if (origin == null || aimPoint == null) {
            return null;
        }

        double dx = aimPoint.x - origin.x;
        double dz = aimPoint.z - origin.z;
        double yawDeg = Math.toDegrees(Math.atan2(dz, dx)) + (double)90.0F;
        double controllerYaw = wrap360(yawDeg + (double)180.0F);
        return controllerYaw < 0.02 || controllerYaw > 359.98 ? (double)0.0F : controllerYaw;
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

    private Vec3 getSublevelTargetVelocityPerTick(ServerLevel serverLevel, SubLevelAccess subLevel, Vec3 targetWorldPosition) {
        Vec3 apiVelocity = this.toWorldVelocity(serverLevel, subLevel, targetWorldPosition);
        Vec3 fallbackVelocity = this.estimateSublevelTargetVelocityFromPosition(subLevel, targetWorldPosition, serverLevel.getGameTime());
        return apiVelocity.lengthSqr() > 1.0E-8 ? apiVelocity : fallbackVelocity;
    }

    private Vec3 estimateSublevelTargetVelocityFromPosition(SubLevelAccess subLevel, Vec3 targetWorldPosition, long gameTime) {
        UUID id = subLevel.getUniqueId();
        if (targetWorldPosition != null && id != null) {
            Vec3 velocity = this.lastSableVelocityPerTick;
            if (id.equals(this.lastSableVelocityTargetId) && this.lastSableVelocityTargetPos != null && this.lastSableVelocityTargetTick != Long.MIN_VALUE && gameTime > this.lastSableVelocityTargetTick) {
                long dt = gameTime - this.lastSableVelocityTargetTick;
                velocity = targetWorldPosition.subtract(this.lastSableVelocityTargetPos).scale((double)1.0F / (double)dt);
                if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z) || velocity.lengthSqr() > (double)25.0F) {
                    velocity = Vec3.ZERO;
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

    private TargetMotionEstimate estimateTargetMotion(ServerLevel serverLevel, @Nullable UUID targetId, @Nullable Entity targetEntity, Vec3 targetWorldPosition, Vec3 targetVel, Vec3 targetAccel) {
        Vec3 rawVelocity = finiteOrZero(targetVel);
        Vec3 rawAcceleration = clampAcceleration(finiteOrZero(targetAccel));
        if (targetId == null || serverLevel == null) {
            return this.motionEstimate(TargetMotionClass.UNKNOWN, rawVelocity, rawAcceleration, (double)0.0F, false, "no_history");
        }

        long tick = serverLevel.getGameTime();
        TargetMotionState previous = this.targetMotionStates.get(targetId);
        long dt = previous == null ? 1L : Math.max(1L, tick - previous.tick);
        Vec3 smoothedVelocity = previous == null ? rawVelocity : previous.smoothedVelocity.scale(0.45).add(rawVelocity.scale(0.55));
        Vec3 smoothedAcceleration = previous == null ? rawAcceleration : previous.smoothedAcceleration.scale(0.55).add(rawAcceleration.scale(0.45));
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

        this.targetMotionStates.put(targetId, new TargetMotionState(targetWorldPosition == null ? Vec3.ZERO : targetWorldPosition, rawVelocity, rawAcceleration, smoothedVelocity, smoothedAcceleration, onGround, tick));
        if (this.targetMotionStates.size() > 256) {
            this.targetMotionStates.entrySet().removeIf(entry -> tick - entry.getValue().tick > 200L);
        }

        return this.motionEstimate(motionClass, smoothedVelocity, smoothedAcceleration, jerk, fastMoving || erratic, reason);
    }

    private TargetMotionEstimate motionEstimate(TargetMotionClass motionClass, Vec3 velocity, Vec3 acceleration, double jerk, boolean looseAim, String reason) {
        int stableTicks = motionClass == TargetMotionClass.ERRATIC || looseAim ? Math.min(AIM_STABLE_REQUIRED, (Integer)RadarConfig.server().erraticTargetStableTicks.get()) : AIM_STABLE_REQUIRED;
        double minConfidence = NEW_SOLVER_MIN_CONFIDENCE;
        double aimStableEps = looseAim ? Math.max(AIM_STABLE_EPS, (Double)RadarConfig.server().targetLoosenThreshold.get()) : AIM_STABLE_EPS;

        return new TargetMotionEstimate(motionClass == null ? TargetMotionClass.UNKNOWN : motionClass, finiteOrZero(velocity), clampAcceleration(finiteOrZero(acceleration)), jerk, stableTicks, minConfidence, aimStableEps, looseAim, reason);
    }

    private int targetTrackingLeadTicks(TargetMotionClass motionClass) {
        int configured = (Integer)RadarConfig.server().targetTrackingLeadTicks.get();
        if (configured <= 0 || motionClass == TargetMotionClass.UNKNOWN) {
            return 0;
        }

        return motionClass == TargetMotionClass.ERRATIC ? configured : Math.max(0, configured / 2);
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
        if (Mods.SABLE.isLoaded() && muzzleWorldPosition != null && SableUtils.isBlockInShipyard(serverLevel, this.cannonMount.getBlockPos())) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, this.cannonMount.getBlockPos());
            return mountShip == null ? Vec3.ZERO : VS2ShipVelocityTracker.getShipVelocityPerTick(mountShip, serverLevel, muzzleWorldPosition);
        } else {
            return Vec3.ZERO;
        }
    }

    private Vec3 getPlatformAcceleration(ServerLevel serverLevel, Vec3 platformVelocity) {
        if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(serverLevel, this.cannonMount.getBlockPos())) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(serverLevel, this.cannonMount.getBlockPos());
            return mountShip == null ? Vec3.ZERO : AccelerationTracker.getAccelerationPerTick2(mountShip.getUniqueId(), platformVelocity);
        } else {
            return Vec3.ZERO;
        }
    }

    private void logTargetingCoordinateResolution(ServerLevel serverLevel, Vec3 rawTargetPos, Vec3 resolvedTargetPos, @Nullable Vec3 rawMuzzlePos, @Nullable Vec3 resolvedMuzzlePos, Vec3 inheritedVelocity) {
        if (RadarConfig.DEBUG_BEAMS) {
            LOGGER.warn("WFC TargetingComputer coords dim={} rawTarget={} resolvedTarget={} rawMuzzle={} resolvedMuzzle={} inheritedVel={}", new Object[]{serverLevel.dimension().location(), rawTargetPos, resolvedTargetPos, rawMuzzlePos, resolvedMuzzlePos, inheritedVelocity});
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
        PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
        return contraption == null ? null : wrap360((double)contraption.yaw) - (double)270.0F;
    }

    @Nullable
    private Double currentPitchDeg(AbstractMountedCannonContraption cannonContraption) {
        PitchOrientedContraptionEntity contraption = this.cannonMount.getContraption();
        if (contraption != null && cannonContraption != null) {
            int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
            return (double)contraption.pitch * (double)(-invert);
        } else {
            return null;
        }
    }

    private boolean isSableMount() {
        return Mods.SABLE.isLoaded() && this.level != null && PhysicsHandler.isBlockInPlotyard(this.level, this.cannonMount.getBlockPos());
    }

    private int estimateSlewTicksForSolution(AbstractMountedCannonContraption cannonContraption, @Nullable AimSolution solution, double fallbackControllerYaw, double fallbackPitch) {
        if (this.isSableMount() && solution != null) {
            AimCommand command = this.mountAimCommand(cannonContraption, solution.aimDirection());
            if (command != null) {
                return this.estimateSlewTicks(cannonContraption, command.controllerYawDeg(), command.pitchDeg());
            }
        }

        return this.estimateSlewTicks(cannonContraption, fallbackControllerYaw, fallbackPitch);
    }

    private PitchConstraint effectivePitchConstraint(AbstractMountedCannonContraption cannonContraption) {
        double controllerMin = this.pitchController == null
                ? PitchConstraint.SOLVER_MIN_PITCH_DEG
                : this.pitchController.getMinAngleDeg();
        double controllerMax = this.pitchController == null
                ? PitchConstraint.SOLVER_MAX_PITCH_DEG
                : this.pitchController.getMaxAngleDeg();
        double cannonMin = cannonContraption == null
                ? PitchConstraint.SOLVER_MIN_PITCH_DEG
                : -cannonContraption.maximumDepression(this.cannonMount);
        double cannonMax = cannonContraption == null
                ? PitchConstraint.SOLVER_MAX_PITCH_DEG
                : cannonContraption.maximumElevation(this.cannonMount);

        Vec3 rightAxis = new Vec3(1.0, 0.0, 0.0);
        Vec3 upAxis = new Vec3(0.0, 1.0, 0.0);
        Vec3 forwardAxis = new Vec3(0.0, 0.0, 1.0);
        if (this.isSableMount()) {
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(this.level, this.cannonMount.getBlockPos());
            if (mountShip != null) {
                rightAxis = SableUtils.getWorldVecDirectionTransform(rightAxis, mountShip);
                upAxis = SableUtils.getWorldVecDirectionTransform(upAxis, mountShip);
                forwardAxis = SableUtils.getWorldVecDirectionTransform(forwardAxis, mountShip);
            }
        }

        return PitchConstraint.intersect(controllerMin, controllerMax, cannonMin, cannonMax, rightAxis, upAxis, forwardAxis);
    }

    @Nullable
    private AimCommand mountAimCommand(AbstractMountedCannonContraption cannonContraption, @Nullable Vec3 worldAimDirection) {
        PitchConstraint constraint = this.effectivePitchConstraint(cannonContraption);
        if (worldAimDirection == null || !constraint.allows(worldAimDirection)) {
            return null;
        }
        TargetingMath.YawPitch mountAngles = constraint.mountYawPitch(worldAimDirection);
        return new AimCommand(mountAngles.pitchDeg(), wrap360(mountAngles.yawDeg() + 270.0));
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
                double yawError = Math.abs(shortestDelta(currentYaw, targetYaw));
                yawTicks = ticksForAngle(yawError, (double)Math.abs(this.yawController.getSpeed()) / (double)24.0F);
            }

            double pitchTicks = (double)0.0F;
            if (this.pitchController != null) {
                double currentPitch = (double)contraption.pitch;
                int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
                currentPitch *= (double)(-invert);
                double pitchError = Math.abs(desiredPitch - currentPitch);
                pitchTicks = ticksForAngle(pitchError, (double)Math.abs(this.pitchController.getSpeed()) / (double)24.0F);
            }

            double ticks = Math.max(yawTicks, pitchTicks);
            return !Double.isFinite(ticks) ? 0 : (int)Math.max((double)0.0F, Math.min((double)40.0F, Math.ceil(ticks)));
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

    private static Vec3 finiteOrZero(Vec3 vec) {
        return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z) ? vec : Vec3.ZERO;
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
        if (this.safeZones != null && !this.safeZones.isEmpty()) {
            Vec3 aim = this.lastAimPoint != null ? this.lastAimPoint : this.target;
            if (aim == null) {
                return false;
            } else {
                Vec3 start = this.getCannonRayStart();

                for(SafeZone zone : this.safeZones) {
                    if (zone != null && zone.intersects(this.level, start, aim)) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private boolean hasCorrectYawPitch(boolean lag) {
        if (this.yawController == null && this.pitchController == null) {
            return false;
        } else {
            boolean yaw = true;
            if (this.yawController != null) {
                yaw = this.yawController.atTargetYaw(lag);
            }

            boolean pitch = this.pitchController.atTargetPitch(lag);
            return yaw && pitch;
        }
    }

    private void stopFireCannon() {
        if (this.fireController != null) {
            this.fireController.setPowered(false);
        }
    }

    private void tryFireCannon() {
        if (this.fireController != null) {
            if (this.level instanceof ServerLevel serverLevel) {
                AdvancedProximityFuze.pushLaunchContext(serverLevel, this.cannonMount.getBlockPos());
                try {
                    this.fireController.setPowered(true);
                } finally {
                    AdvancedProximityFuze.popLaunchContext();
                }
            } else {
                this.fireController.setPowered(true);
            }
            LOGGER.debug("firing!");
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

    private static record TargetMotionState(Vec3 position, Vec3 rawVelocity, Vec3 rawAcceleration, Vec3 smoothedVelocity, Vec3 smoothedAcceleration, boolean onGround, long tick) {
    }

    private static record TargetMotionEstimate(TargetMotionClass motionClass, Vec3 velocity, Vec3 acceleration, double jerk, int stableTicksRequired, double minConfidence, double aimStableEps, boolean looseAim, String reason) {
    }

    public static record SolverDebugReport(List<String> lines, @Nullable ProjectileSimulator.SimulationResult trajectory) {
    }

    private static record AimCommand(double pitchDeg, double controllerYawDeg) {
    }

    private static record AsyncTargetingRequest(BlockPos mountPos, @Nullable UUID targetId, Vec3 solvePos, long requestTick, TargetingSnapshot snapshot) {
    }

    private static record AsyncTargetingResult(AsyncTargetingRequest request, @Nullable TargetingResult result) {
    }

    private static ExecutorService createTargetingExecutor() {
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

        // $FF: synthetic method
        private static RayResult[] $values() {
            return new RayResult[]{CLEAR, BLOCKED_BLOCK, BLOCKED_SAFEZONE};
        }
    }
}
