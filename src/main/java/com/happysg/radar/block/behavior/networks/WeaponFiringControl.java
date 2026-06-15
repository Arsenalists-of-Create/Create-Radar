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
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetMotionClass;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
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
import javax.annotation.Nullable;
import net.createmod.catnip.math.VecHelper;
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
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
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
    public WeaponNetworkData.WeaponGroupView view;
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
    private static final double SABLE_AIM_CHANGE_THRESHOLD = 0.3;
    private static final int VIS_REFRESH_TICKS = 3;
    private static final int MAX_POINTS_PER_REFRESH = 10;
    private static final double ENTITY_INFLATE = 0.0F;
    private static final double FACE_EPS = 0.01;
    private final Map<Integer, VisCache> visCache;
    private static final int REACQUIRE_EVERY_TICKS = 10;
    private static final int MAX_NEW_PROBES_PER_REFRESH = 3;
    private static final double FRAC_EPS = 1.0E-9;
    private static final int LOS_SELECTION_TTL_TICKS = 10;
    private static final int LOS_PREFIRE_TTL_TICKS = 1;
    double maxSimDistanceBlocks;
    private static final double NEW_SOLVER_MIN_CONFIDENCE = 0.05;
    private static final int TARGETING_DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final int MAX_NEW_SOLVER_FLIGHT_TICKS = 400;
    private final TargetingComputer targetingComputer;
    private long lastTargetingDebugLogTick;
    @Nullable
    private UUID lastSableVelocityTargetId;
    @Nullable
    private Vec3 lastSableVelocityTargetPos;
    private long lastSableVelocityTargetTick;
    private Vec3 lastSableVelocityPerTick;
    private final Map<String, LosCache> losSelectionCache;
    private final Map<UUID, TargetMotionState> targetMotionStates;
    private final LosCache losPrefireCache;
    public List<AABB> safeZones;
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
        this.maxSimDistanceBlocks = (double)4096.0F;
        this.targetingComputer = TargetingComputer.createDefault();
        this.lastTargetingDebugLogTick = Long.MIN_VALUE;
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
            for(AABB zone : this.safeZones) {
                if (zone != null && (zone.contains(start) || zone.contains(end) || zone.clip(start, end).isPresent())) {
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
                    Vec3 shipyardPos = poce.toGlobalVector(VecHelper.getCenterOf(BlockPos.ZERO), 1.0F);
                    return SableUtils.getWorldVec(this.level, shipyardPos);
                } else {
                    return SableUtils.getWorldVec(this.level, this.cannonMount.getBlockPos().getCenter());
                }
            } else {
                return poce == null ? this.cannonMount.getBlockPos().getCenter() : poce.toGlobalVector(VecHelper.getCenterOf(BlockPos.ZERO), 1.0F);
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
        double yChest = yMin + (yMax - yMin) * 0.45;
        Vec3 chest = new Vec3(center.x, yChest, center.z);
        Vec3 mid = new Vec3(center.x, yMid, center.z);
        candidates.add(chest);
        candidates.add(mid);
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
                        if (cached.distanceToSqr(chest) > 1.0E-4 && this.isPointInShootableRange(chest) && !this.isOutOfKnownRange(chest) && this.rayClear(start, chest).isClear()) {
                            worldToFrac(bb, chest, cache);
                            cache.lastWorldPoint = chest;
                            cache.probeCursor = 0;
                            return chest;
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
                    boolean shouldBeEntity = track.trackCategory() == TrackCategory.PLAYER || track.trackCategory() == TrackCategory.HOSTILE || track.trackCategory() == TrackCategory.ANIMAL || track.trackCategory() == TrackCategory.PROJECTILE;
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
        this.stopFireCannon();
    }

    public void setSafeZones(List<AABB> safeZones) {
        LOGGER.debug("setSafeZones() -> {} zones", safeZones.size());
        this.safeZones = safeZones;
    }

    public Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public SubLevelAccess getShipByUUID(ServerLevel level, String uuid) {
        return SubLevelContainer.getContainer(level).getSubLevel(UUID.fromString(uuid));
    }

    public void refreshControllers() {
        if (this.level instanceof ServerLevel serverLevel) {
            this.view = WeaponNetworkData.get(serverLevel).getWeaponGroupViewFromEndpoint(this.level.dimension(), this.pitchController.getBlockPos());
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
                            this.target = this.targetEntity.position();
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

                                    Vec3 cannonMuzzleWorld = this.resolveMuzzleWorldPosition();
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
                                        rawTargetPos = this.targetEntity.position();
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
                                        if (!this.checkLineOfSight(this.targetEntity.position())) {
                                            LOGGER.debug("WFC: LOS blocked to entity, stopping fire (id={})", this.targetEntity.getUUID());
                                            this.stopFireCannon();
                                            return;
                                        }

                                        solvePos = this.targetEntity.position();
                                    }

                                    boolean lag = !motion.looseAim();

                                    WeaponNetworkData wnd = WeaponNetworkData.get(serverLevel);
                                    WeaponNetworkData.Group grp = wnd != null && this.pitchController != null ? wnd.getGroupForController(serverLevel.dimension(), this.pitchController.getBlockPos()) : null;
                                    if (grp != null && !grp.dataLinks.isEmpty()) {
                                        Vec3 cannonOrigin = this.getCannonRayStart();
                                        double best = (double)0.0F;

                                        for(BlockPos dlPos : grp.dataLinks) {
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

                                    CannonLead.LeadSolution lead = null;
                                    TargetingResult targetingResult = null;
                                    boolean forceLegacyLead = (Boolean)RadarConfig.server().forceLegacyCannonLeadSolver.get();
                                    boolean useNewSolver = !forceLegacyLead && (Boolean)RadarConfig.server().useNewTargetingComputer.get() && !CannonUtil.isLaserCannon(cannon) && !this.isSableMount();
                                    if (useNewSolver) {
                                        targetingResult = this.solveWithTargetingComputer(serverLevel, cannon, rawTargetPos, solvePos, targetVel, targetAccel, shooterVel, this.targetEntity, this.targetSublevel, motion.motionClass());
                                    }

                                    boolean newSolverOk = targetingResult != null && targetingResult.valid() && targetingResult.hasShot() && targetingResult.confidence() >= motion.minConfidence();
                                    boolean allowLegacyFallback = forceLegacyLead || (Boolean)RadarConfig.server().allowLegacyCannonLeadFallback.get();
                                    if (!newSolverOk && allowLegacyFallback && !CannonUtil.isLaserCannon(cannon) && dist > noLeadDist) {
                                        lead = CannonLead.solveLeadPerTickConstantVelocity(this.cannonMount, cannon, serverLevel, shooterVel, solvePos, targetVel, (Integer)RadarConfig.server().leadFiringDelay.get(), this.maxSimDistanceBlocks);
                                    }

                                    boolean hasLeadSolution = lead != null && lead.aimPoint != null;
                                    boolean hasNewTargetingSolution = newSolverOk && targetingResult.aimSolution() != null;
                                    boolean canFireWithoutLead = CannonUtil.isLaserCannon(cannon);
                                    Vec3 offsetAim = hasNewTargetingSolution && targetingResult.aimSolution().aimPoint() != null ? targetingResult.aimSolution().aimPoint() : (hasLeadSolution ? lead.aimPoint : solvePos);
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
                                        long now = this.level.getGameTime();
                                        boolean needSolve = this.cachedSableAngles == null || now - this.cachedSableSolveTick >= 3L || this.cachedSableAimTarget == null || this.cachedSableAimTarget.distanceToSqr(offsetAim) > 0.09;
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
                                    } else if (hasNewTargetingSolution) {
                                        desiredPitch = targetingResult.desiredPitchDeg();
                                        desiredYaw = targetingResult.desiredYawDeg() + (double)270.0F;
                                    } else {
                                        Vec3 origin = this.getCannonRayStart();
                                        double dx = offsetAim.x - origin.x;
                                        double dz = offsetAim.z - origin.z;
                                        double yawDeg = Math.toDegrees(Math.atan2(dz, dx)) + (double)90.0F;
                                        desiredYaw = yawDeg + (double)180.0F;
                                        List<Double> pitchRoots = CannonTargeting.calculatePitch(this.cannonMount, origin, offsetAim, serverLevel);
                                        if (pitchRoots != null && !pitchRoots.isEmpty()) {
                                            desiredPitch = pitchRoots.get(0);
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
        this.stopFireCannon();
    }

    public void setTarget(Vec3 target, TargetingConfig config, RadarTrack track, WeaponNetworkData.WeaponGroupView view) {
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
            this.stopFireCannon();
        } else {
            this.binoMode = false;
            this.target = target;
            this.lastOffsetAim = null;
            this.aimStableTicks = 0;
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

    public void setBinoTarget(@Nullable BlockPos binoTarget, TargetingConfig config, WeaponNetworkData.WeaponGroupView view, boolean reset) {
        this.view = view;
        this.targetingConfig = config;
        this.activetrack = null;
        if (!reset && binoTarget != null) {
            this.binoMode = true;
            this.binoTargetPos = binoTarget.immutable();
            if (this.level != null) {
                this.lastTargetTick = this.level.getGameTime();
            }

        } else {
            this.binoMode = false;
            this.binoTargetPos = null;
            this.target = null;
            this.stopFireCannon();
        }
    }

    @Nullable
    private TargetingResult solveWithTargetingComputer(ServerLevel serverLevel, AbstractMountedCannonContraption cannonContraption, Vec3 rawTargetPos, Vec3 targetWorldPos, Vec3 targetVel, Vec3 targetAccel, Vec3 shooterVel, @Nullable Entity targetEntity, @Nullable SubLevelAccess targetSublevel, TargetMotionClass targetMotionClass) {
        if (serverLevel != null && cannonContraption != null && targetWorldPos != null) {
            Vec3 rawMuzzlePos = this.getCannonRayStart();
            Vec3 muzzleWorldPos = this.resolveMuzzleWorldPosition();
            if (muzzleWorldPos == null) {
                return null;
            } else {
                double projectileSpeed = (double)CannonUtil.getInitialVelocity(cannonContraption, serverLevel);
                if (Double.isFinite(projectileSpeed) && !(projectileSpeed <= (double)0.0F)) {
                    BallisticPropertiesComponent ballistics = CannonUtil.getBallistics(cannonContraption, serverLevel);
                    double gravity = ballistics != null ? ballistics.gravity() : CannonUtil.getProjectileGravity(cannonContraption, serverLevel);
                    double drag = ballistics != null ? ballistics.drag() : CannonUtil.getProjectileDrag(cannonContraption, serverLevel);
                    if (!Double.isFinite(gravity)) {
                        gravity = 0.05;
                    }

                    if (!Double.isFinite(drag)) {
                        drag = 0.01;
                    }

                    Vec3 safeTargetVel = finiteOrZero(targetVel);
                    Vec3 safeTargetAccel = clampAcceleration(finiteOrZero(targetAccel));
                    Vec3 safeShooterVel = finiteOrZero(shooterVel);
                    double initialYaw = oldControllerYawForWorldTarget(muzzleWorldPos, targetWorldPos);
                    double initialPitch = oldLosPitchForWorldTarget(muzzleWorldPos, targetWorldPos);
                    int slewTicks = this.estimateSlewTicks(cannonContraption, initialYaw, initialPitch);
                    Vec3 delayedTargetPos = predictTarget(targetWorldPos, safeTargetVel, safeTargetAccel, (double)slewTicks, targetMotionClass, gravity);
                    AABB targetAabb = targetEntity != null ? this.resolveEntityWorldAabb(serverLevel, targetEntity) : this.resolveSublevelWorldAabb(targetSublevel);
                    if (targetAabb != null && slewTicks > 0) {
                        targetAabb = targetAabb.move(delayedTargetPos.subtract(targetWorldPos));
                    }

                    boolean logTargetingDebug = this.shouldLogTargetingDebug(serverLevel);
                    if (logTargetingDebug) {
                        this.logTargetingCoordinateResolution(serverLevel, rawTargetPos, targetWorldPos, rawMuzzlePos, muzzleWorldPos, safeShooterVel);
                    }

                    TargetingSnapshot snapshot = TargetingSnapshot.builder(serverLevel).muzzlePosition(muzzleWorldPos).inheritedVelocity(safeShooterVel).targetPosition(delayedTargetPos).targetVelocity(safeTargetVel).targetAcceleration(safeTargetAccel).targetAabb(targetAabb).projectileSpeed(projectileSpeed).gravity(gravity).drag(drag).maxFlightTicks(this.computeNewSolverMaxFlightTicks(projectileSpeed)).gameTime(serverLevel.getGameTime() + (long)slewTicks).preferredYawDeg(this.yawController != null ? this.yawController.getTargetAngle() - (double)270.0F : null).preferredPitchDeg(this.pitchController != null ? this.pitchController.getTargetAngle() : null).currentYawDeg(this.currentSolverYawDeg()).currentPitchDeg(this.currentPitchDeg(cannonContraption)).targetSublevelId(targetSublevel != null ? targetSublevel.getUniqueId() : null).targetMotionClass(targetMotionClass).build();
                    TargetingResult result = this.targetingComputer.solve(snapshot);
                    if (this.level.getGameTime() % 20L == 1L && result != null) {
                        LOGGER.debug("WFC TargetingComputer {}", result.debugString());
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
    private Vec3 resolveMuzzleWorldPosition() {
        return this.getCannonRayStart();
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
        TargetMotionClass motionClass = previous == null ? TargetMotionClass.UNKNOWN : (erratic ? TargetMotionClass.ERRATIC : TargetMotionClass.STEADY);
        String reason = previous == null ? "warming_up" : (fallFlying ? "fall_flying" : (playerSprintJump ? "sprint_jump" : (inferredSprintJump ? "inferred_sprint_jump" : (jerk > 0.08 ? "jerk" : (directionChange > 0.55 ? "direction_change" : "steady")))));

        this.targetMotionStates.put(targetId, new TargetMotionState(targetWorldPosition == null ? Vec3.ZERO : targetWorldPosition, rawVelocity, rawAcceleration, smoothedVelocity, smoothedAcceleration, onGround, tick));
        if (this.targetMotionStates.size() > 256) {
            this.targetMotionStates.entrySet().removeIf(entry -> tick - entry.getValue().tick > 200L);
        }

        return this.motionEstimate(motionClass, smoothedVelocity, smoothedAcceleration, jerk, false, reason);
    }

    private TargetMotionEstimate motionEstimate(TargetMotionClass motionClass, Vec3 velocity, Vec3 acceleration, double jerk, boolean looseAim, String reason) {
        int stableTicks = motionClass == TargetMotionClass.ERRATIC ? Math.min(AIM_STABLE_REQUIRED, (Integer)RadarConfig.server().erraticTargetStableTicks.get()) : AIM_STABLE_REQUIRED;
        double minConfidence = NEW_SOLVER_MIN_CONFIDENCE;
        double aimStableEps = AIM_STABLE_EPS;

        return new TargetMotionEstimate(motionClass == null ? TargetMotionClass.UNKNOWN : motionClass, finiteOrZero(velocity), clampAcceleration(finiteOrZero(acceleration)), jerk, stableTicks, minConfidence, aimStableEps, looseAim, reason);
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

    private static double oldControllerYawForWorldTarget(Vec3 origin, Vec3 target) {
        double dx = target.x - origin.x;
        double dz = target.z - origin.z;
        return Math.toDegrees(Math.atan2(dz, dx)) + (double)270.0F;
    }

    private static double oldLosPitchForWorldTarget(Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return Math.toDegrees(Math.atan2(delta.y, Math.max(1.0E-6, horizontal)));
    }

    private int computeNewSolverMaxFlightTicks(double projectileSpeed) {
        double speed = Math.max(1.0E-6, projectileSpeed);
        int ticks = (int)Math.ceil(Math.max((double)1.0F, this.maxSimDistanceBlocks) / speed) + 40;
        return Math.max(40, Math.min(400, ticks));
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

                for(AABB zone : this.safeZones) {
                    if (zone != null) {
                        if (zone.contains(start) || zone.contains(aim)) {
                            return true;
                        }

                        if (zone.clip(start, aim).isPresent()) {
                            return true;
                        }
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
