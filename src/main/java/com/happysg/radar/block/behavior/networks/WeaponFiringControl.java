package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.*;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.VS2ShipVelocityTracker;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WeaponFiringControl {

    private static final Logger LOGGER = LogUtils.getLogger();

    public TargetingConfig targetingConfig = TargetingConfig.DEFAULT;
    private Vec3 target;
    private float offset;

    private Vec3 lastOffsetAim = null;
    private int aimStableTicks = 0;
    private static final int AIM_STABLE_REQUIRED = 2;
    private static final double AIM_STABLE_EPS = 0.5; // blocks


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
    private long targetShipId = -1;
    @Nullable private Vec3 lastAimPoint = null;

    private List<List<Double>> cachedVS2Angles = null;
    private Vec3 cachedVS2AimTarget = null;
    private long cachedVS2SolveTick = -1;
    private static final int VS2_SOLVE_INTERVAL = 3;
    private static final double VS2_AIM_CHANGE_THRESHOLD = 0.3;

    private static final int VIS_REFRESH_TICKS = 3; // recompute every N ticks per entity
    private static final int MAX_POINTS_PER_REFRESH = 10; // ray budget per refresh
    private static final double ENTITY_INFLATE = 0.0; // grow AABB a bit for modded hitboxes
    private static final double FACE_EPS = 0.01; // tiny offset outside faces
    private final java.util.Map<Integer, VisCache> visCache = new java.util.HashMap<>();

    private static final int REACQUIRE_EVERY_TICKS = 10;
    private static final int MAX_NEW_PROBES_PER_REFRESH = 3;
    private static final double FRAC_EPS = 1e-9;

    private static final int LOS_SELECTION_TTL_TICKS = 10;
    private static final int LOS_PREFIRE_TTL_TICKS = 1;

    double maxSimDistanceBlocks = 4096.0;

    private static final class LosCache {
        boolean ok;
        long tick;
    }

    private final java.util.Map<String, LosCache> losSelectionCache = new java.util.HashMap<>();
    private final LosCache losPrefireCache = new LosCache();

    private static final class VisCache {
        // Normalized point inside the entity AABB:
        // fx=0 -> minX, fx=1 -> maxX (same for y/z)
        boolean hasFrac = false;
        double fx, fy, fz;

        // For debugging / last resolved world point (optional)
        @Nullable Vec3 lastWorldPoint = null;

        int probeCursor = 0;
        int blockedStreak = 0;
        long lastTick = 0L;
        long lastReacquireTick = 0L;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double invSpan(double min, double max) {
        double span = max - min;
        return Math.abs(span) < FRAC_EPS ? 0.0 : 1.0 / span;
    }

    private static Vec3 fracToWorld(AABB bb, double fx, double fy, double fz) {
        return new Vec3(
                bb.minX + (bb.maxX - bb.minX) * fx,
                bb.minY + (bb.maxY - bb.minY) * fy,
                bb.minZ + (bb.maxZ - bb.minZ) * fz
        );
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

    public List<AABB> safeZones = new ArrayList<>();
    private long lastTargetTick = -1;   // server-time when we last got a target update
    private enum RayResult {
        CLEAR,
        BLOCKED_BLOCK,
        BLOCKED_SAFEZONE;

        public boolean isClear() {
            return this == CLEAR;
        }
    }

    public WeaponFiringControl(AutoPitchControllerBlockEntity controller, CannonMountBlockEntity cannonMount, AutoYawControllerBlockEntity yawController) {
        this.cannonMount = cannonMount;
        this.pitchController = controller;
        this.yawController = yawController;
        this.level = cannonMount.getLevel();

        LOGGER.debug("FiringControlBlockEntity.<init>() -> controller={} mountPos={}", controller, cannonMount.getBlockPos());
    }
    private RayResult rayClear(Vec3 start, Vec3 end) {

        RayResult result = RayResult.CLEAR;


        if (!safeZones.isEmpty()) {
            for (AABB zone : safeZones) {
                if (zone == null) continue;
                if (zone.contains(start) || zone.contains(end) || zone.clip(start, end).isPresent()) {
                    return RayResult.BLOCKED_SAFEZONE;
                }
            }
        }


        if (result == RayResult.CLEAR) {
            ClipContext ctx = new ClipContext(
                    start, end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            );

            HitResult hit = level.clip(ctx);

            if (hit.getType() != HitResult.Type.MISS) {
                double hitDist = hit.getLocation().distanceTo(start);
                double targetDist = end.distanceTo(start);

                if (hitDist < targetDist) {
                    result = RayResult.BLOCKED_BLOCK;
                }
            }
        }


        if (RadarConfig.DEBUG_BEAMS && level instanceof ServerLevel server) {
            debugRay(server, start, end, result);
        }

        return result;
    }

    public Vec3 getCannonRayStart() {
        if (cannonMount == null)
            return null;

        PitchOrientedContraptionEntity poce = cannonMount.getContraption();

        if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(level, cannonMount.getBlockPos())) {
            if (poce != null) {
                // toGlobalVector gives shipyard-global coords
                Vec3 shipyardPos = poce.toGlobalVector(VecHelper.getCenterOf(BlockPos.ZERO), 1.0f);
                return SableUtils.getWorldVec(level, shipyardPos);
            }
            return SableUtils.getWorldVec(level, cannonMount.getBlockPos().getCenter());
        }

        if (poce == null)
            return cannonMount.getBlockPos().getCenter();

        return poce.toGlobalVector(VecHelper.getCenterOf(BlockPos.ZERO), 1.0f);
    }




    private AABB inflatedAabb(Entity e) {
        AABB bb = e.getBoundingBox();
        return bb.inflate(ENTITY_INFLATE);
    }


    /**
     * Adds a set of points on a face of the AABB.
     * axis 'x' => plane at x=planeVal, varying y/z.
     * axis 'y' => plane at y=planeVal, varying x/z.
     * axis 'z' => plane at z=planeVal, varying x/y.
     */
    private void addFaceCandidates(List<Vec3> out,
                                   double planeVal,
                                   double uMin, double uMax,
                                   double vMin, double vMax,
                                   char axis,
                                   boolean maxFace) {

        double uMid = (uMin + uMax) * 0.5;
        double vMid = (vMin + vMax) * 0.5;

        // Face center
        out.add(facePoint(axis, planeVal, uMid, vMid, maxFace));

        // 4 corners
        out.add(facePoint(axis, planeVal, uMin, vMin, maxFace));
        out.add(facePoint(axis, planeVal, uMin, vMax, maxFace));
        out.add(facePoint(axis, planeVal, uMax, vMin, maxFace));
        out.add(facePoint(axis, planeVal, uMax, vMax, maxFace));

        // 4 edge midpoints
        out.add(facePoint(axis, planeVal, uMin, vMid, maxFace));
        out.add(facePoint(axis, planeVal, uMax, vMid, maxFace));
        out.add(facePoint(axis, planeVal, uMid, vMin, maxFace));
        out.add(facePoint(axis, planeVal, uMid, vMax, maxFace));
    }


    private Vec3 facePoint(char axis, double planeVal, double u, double v, boolean maxFace) {
        double eps = maxFace ? -FACE_EPS : +FACE_EPS;


        return switch (axis) {
            case 'x' -> new Vec3(planeVal + eps, u, v);
            case 'y' -> new Vec3(u, planeVal + eps, v);
            default -> new Vec3(u, v, planeVal + eps); // 'z'
        };
    }


    /**
     * Cached visible point query for entities.
     * Returns null if we couldn't find a visible point within budget.
     */
    @Nullable
    private Vec3 getCachedVisiblePoint(Entity f) {
        int id = f.getId();
        long now = level.getGameTime();

        VisCache c = visCache.get(id);

        Vec3 start = getCannonRayStart();
        if (start == null) return null;

        if (c != null && (now - c.lastTick) < VIS_REFRESH_TICKS) {
            Vec3 cached = null;

            if (c.hasFrac) cached = fracToWorld(inflatedAabb(f), c.fx, c.fy, c.fz);
            else cached = c.lastWorldPoint;

            if (cached != null
                    && isPointInShootableRange(cached)
                    && !isOutOfKnownRange(cached)
                    && rayClear(start, cached).isClear()) {
                return cached;
            }

            c.lastTick = 0L;
        }

        if (c == null) c = new VisCache();

        Vec3 vis = findVisiblePointOnEntityRotating(f, start, c, MAX_POINTS_PER_REFRESH);

        c.lastTick = now;
        visCache.put(id, c);

        if (vis == null) {
            c.hasFrac = false;
            c.lastWorldPoint = null;
            return null;
        }

        return vis;
    }

    @Nullable
    private Vec3 findVisiblePointOnEntityRotating(Entity e, Vec3 start, VisCache cache, int budget) {
        AABB bb = inflatedAabb(e);
        long now = level.getGameTime();

        ArrayList<Vec3> candidates = new ArrayList<>(24);

        Vec3 center = bb.getCenter();
        Vec3 toCannon = start.subtract(center);

        double ax = Math.abs(toCannon.x);
        double ay = Math.abs(toCannon.y);
        double az = Math.abs(toCannon.z);

        double xMin = bb.minX, xMax = bb.maxX;
        double yMin = bb.minY, yMax = bb.maxY;
        double zMin = bb.minZ, zMax = bb.maxZ;

        double yMid   = (yMin + yMax) * 0.5;
        double yChest = yMin + (yMax - yMin) * 0.45;

        // Preferred probes (best first)
        Vec3 chest = new Vec3(center.x, yChest, center.z);
        Vec3 mid   = new Vec3(center.x, yMid, center.z);

        candidates.add(chest);
        candidates.add(mid);

        boolean useX = ax >= ay && ax >= az;
        boolean useY = ay > ax && ay >= az;

        if (useX) {
            boolean maxFace = (toCannon.x >= 0);
            double x = maxFace ? xMax : xMin;
            addFaceCandidates(candidates, x, yMin, yMax, zMin, zMax, 'x', maxFace);
        } else if (useY) {
            boolean maxFace = (toCannon.y >= 0);
            double y = maxFace ? yMax : yMin;
            addFaceCandidates(candidates, y, xMin, xMax, zMin, zMax, 'y', maxFace);
        } else {
            boolean maxFace = (toCannon.z >= 0);
            double z = maxFace ? zMax : zMin;
            addFaceCandidates(candidates, z, xMin, xMax, yMin, yMax, 'z', maxFace);
        }

        // Secondary dominant face
        if (ax >= az) {
            boolean maxFace = (toCannon.x >= 0);
            double x = maxFace ? xMax : xMin;
            addFaceCandidates(candidates, x, yMin, yMax, zMin, zMax, 'x', maxFace);
        } else {
            boolean maxFace = (toCannon.z >= 0);
            double z = maxFace ? zMax : zMin;
            addFaceCandidates(candidates, z, xMin, xMax, yMin, yMax, 'z', maxFace);
        }

        candidates.add(center);

        int n = candidates.size();
        if (n == 0) return null;

        int tries = 0;

        if (cache.hasFrac) {
            Vec3 cached = fracToWorld(bb, cache.fx, cache.fy, cache.fz);
            if (isPointInShootableRange(cached)
                    && !isOutOfKnownRange(cached)
                    && rayClear(start, cached).isClear()) {

                cache.blockedStreak = 0;

                // Periodic reacquire back to chest
                if (now - cache.lastReacquireTick >= REACQUIRE_EVERY_TICKS) {
                    cache.lastReacquireTick = now;
                    if (cached.distanceToSqr(chest) > 1e-4
                            && isPointInShootableRange(chest)
                            && !isOutOfKnownRange(chest)
                            && rayClear(start, chest).isClear()) {

                        worldToFrac(bb, chest, cache);
                        cache.lastWorldPoint = chest;
                        cache.probeCursor = 0;
                        return chest;
                    }
                }

                cache.lastWorldPoint = cached;
                return cached;
            }
            tries++;
        }

        int remaining = budget - tries;
        if (remaining <= 0) {
            cache.blockedStreak++;
            return null;
        }

        int maxNewTries = Math.min(MAX_NEW_PROBES_PER_REFRESH, remaining);

        // If we just lost LOS, restart from best probes
        int idx = (cache.blockedStreak == 0) ? 0 : Math.floorMod(cache.probeCursor, n);

        for (int k = 0; k < maxNewTries; k++) {
            Vec3 end = candidates.get((idx + k) % n);

            if (!isPointInShootableRange(end)) continue;
            if (isOutOfKnownRange(end)) continue;

            if (rayClear(start, end).isClear()) {
                worldToFrac(bb, end, cache);
                cache.lastWorldPoint = end;
                cache.probeCursor = (idx + k + 1) % n;
                cache.blockedStreak = 0;
                return end;
            }
        }

        cache.probeCursor = (idx + maxNewTries) % n;
        cache.blockedStreak++;
        return null;
    }

    public boolean checkLineOfSight(Vec3 target) {
        if (!binoMode && activetrack == null && target == null) {
            return false;
        }
        if(!binoMode && activetrack == null){
            boolean networkcast = true;
        }
        if(!targetingConfig.lineOfSight()) return true;

        float height;

        if (!binoMode) {
            height = (targetEntity != null) ? targetEntity.getBbHeight()
                    : (activetrack != null ? activetrack.getEnityHeight() : 1f);
        } else {
            height = 1f;
        }

        int blocksHigh = (int) Math.ceil(height);
        Vec3 start = getCannonRayStart();
        if (isOutOfKnownRange(target)) return false;
        if (!isPointInShootableRange(target)) return false;

        LOGGER.warn("LOS DBG: trackCat={} entityType={} height={} blocksHigh={} target={}", activetrack != null ? activetrack.trackCategory() : "null", activetrack != null ? activetrack.entityType() : "null", height, blocksHigh, target);
        for (int h = blocksHigh - 1; h >= 0; h--) {
            // center of each block, top-first
            Vec3 end = target.add(0, h + 0.5, 0);
            if (rayClear(start, end).isClear()) {
                offset = h + 0.5f; // highest valid clear point
                return true;
            }
        }

        return false;
    }

    private boolean isPointInShootableRange(@Nullable Vec3 point) {
        if (point == null) return false;

        double max = pitchController != null ? pitchController.getMaxEngagementRangeBlocks() : 0.0;


        if (max <= 0.0) return true;

        Vec3 start = getCannonRayStart();
        double dx = point.x - start.x;
        double dz = point.z - start.z;
        double horiz2 = dx * dx + dz * dz;

        return horiz2 <= (max * max);
    }

    // LOS query for network controller
    public boolean hasLineOfSightTo(@Nullable RadarTrack track, boolean requireLos) {
        if (!isMountStateOk()) return false;
        if (!requireLos) return true;

        if (track == null) return false;

        Vec3 p = track.position();
        if (p == null) return false;

        if (!isPointInShootableRange(p)) return false;

        long now = level.getGameTime();
        String key = track.getId();

        LosCache c = losSelectionCache.get(key);
        if (c != null && (now - c.tick) <= LOS_SELECTION_TTL_TICKS) {
            return c.ok;
        }

        boolean ok = computeLosToTrack(track);
        if (c == null) c = new LosCache();
        c.ok = ok;
        c.tick = now;
        losSelectionCache.put(key, c);

        return ok;
    }

    private boolean computeLosToTrack(@Nullable RadarTrack track) {
        if (track == null) return false;
        Vec3 p = track.position();
        if (p == null) return false;

        Vec3 start = getCannonRayStart();

        if (level instanceof ServerLevel sl) {
            // If it looks like an entity track, REQUIRE entity resolution for LOS
            boolean shouldBeEntity =
                    track.trackCategory() == TrackCategory.PLAYER ||
                            track.trackCategory() == TrackCategory.HOSTILE ||
                            track.trackCategory() == TrackCategory.ANIMAL ||
                            track.trackCategory() == TrackCategory.PROJECTILE;

            Entity e = null;
            try {
                UUID uuid = UUID.fromString(track.getId());
                e = sl.getEntity(uuid);
            } catch (Throwable ignored) {}

            if (e != null && e.isAlive()) {
                return getCachedVisiblePoint(e) != null;
            }

            if (shouldBeEntity) {
                return false;
            }
        }

        // Non-entity tracks can keep the fallback samples
        for (int i = 0; i < 4; i++) {
            Vec3 end = p.add(0, 0.25 + i * 0.5, 0);
            if (!isPointInShootableRange(end)) continue;
            if (rayClear(start, end).isClear()) return true;
        }
        return false;
    }



    private void debugRay(ServerLevel server, Vec3 start, Vec3 end, RayResult result) {

        float r, g, b;

        switch (result) {
            case CLEAR -> {  // GREEN
                r = 0.0f; g = 1.0f; b = 0.0f;
            }
            case BLOCKED_BLOCK -> { // RED
                r = 1.0f; g = 0.0f; b = 0.0f;
            }
            case BLOCKED_SAFEZONE -> { // YELLOW
                r = 1.0f; g = 1.0f; b = 0.0f;
            }
            default -> {
                r = 1.0f; g = 1.0f; b = 1.0f;
            }
        }

        double dist = start.distanceTo(end);
        Vec3 dir = end.subtract(start).normalize();

        for (double d = 0; d < dist; d += 0.25) {
            Vec3 p = start.add(dir.scale(d));

            server.sendParticles(
                    new DustParticleOptions(new Vector3f(r, g, b), 1.0f),
                    p.x, p.y, p.z,
                    1, 0, 0, 0, 0
            );
        }
    }


    public void clearBinoTarget() {
        visCache.clear();
        this.binoMode = false;
        this.binoTargetPos = null;
        this.target = null;
        this.activetrack = null;

        lastAimPoint = null;
        lastOffsetAim = null;
        aimStableTicks = 0;

        stopFireCannon();
    }

    public void setSafeZones(List<AABB> safeZones) {
        LOGGER.debug("setSafeZones() -> {} zones", safeZones.size());
        this.safeZones = safeZones;
    }
    public  Entity getEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    public SubLevelAccess getShipByUUID(ServerLevel level, String uuid){
        return SubLevelContainer.getContainer(level).getSubLevel(java.util.UUID.fromString(uuid));
    }

    /**
     * Called every tick by the pitch controller.
     */
    public void refreshControllers() {

        if (!(level instanceof ServerLevel serverLevel)) return;
        this.view = WeaponNetworkData.get(serverLevel).getWeaponGroupViewFromEndpoint(level.dimension(), pitchController.getBlockPos());
        if (view.yawPos() != null && level.getBlockEntity(view.yawPos()) instanceof AutoYawControllerBlockEntity autoyaw) {
            this.yawController = autoyaw;
        } else {
            this.yawController = null;
        }
        if (view.pitchPos() != null && level.getBlockEntity(view.pitchPos()) instanceof AutoPitchControllerBlockEntity autopitch) {
            this.pitchController = autopitch;
        } else {
            this.pitchController = null;
        }
        if (view.firingPos() != null && level.getBlockEntity(view.firingPos()) instanceof FireControllerBlockEntity firecont) {
            this.fireController = firecont;
        } else {
            this.fireController = null;
        }
    }

    private boolean isOutOfKnownRange(@Nullable Vec3 point) {
        if (point == null) return true; // no point to test

        double max = pitchController != null ? pitchController.getMaxEngagementRangeBlocks() : 0.0;

        if (max <= 0.0) return false;

        Vec3 start = getCannonRayStart();
        return point.distanceToSqr(start) > (max * max);
    }

    public void tick() {
        if (!isMountStateOk()) {
            stopFireCannon();
            return;
        }

        if (binoMode) {
            lastTargetTick = level.getGameTime();
        } else if (activetrack != null && (targetEntity != null || targetSublevel != null)) {
            lastTargetTick = level.getGameTime();
        }

        if (!binoMode && activetrack == null) {
            stopFireCannon();
            return;
        }

        if (!binoMode && activetrack != null && level instanceof ServerLevel sl) {

            boolean isVsShip = Mods.SABLE.isLoaded() && "VS2:ship".equals(activetrack.entityType());

            if (isVsShip) {
                long id;
                try {
                    id = Long.parseLong(activetrack.id());
                } catch (NumberFormatException ignored) {
                    LOGGER.warn("WFC: invalid VS2 ship id={}, stopping fire", activetrack.id());
                    stopFireCannon();
                    return;
                }
                if (targetSublevel == null || targetShipId != id) {
                    targetSublevel = getShipByUUID(sl, activetrack.id());
                    targetShipId = id;
                    if (targetSublevel == null) {
                        LOGGER.warn("WFC: VS2 ship id={} not loaded, stopping fire", id);
                        stopFireCannon();
                        return;
                    }
                }
                targetEntity = null;
            } else {
                Entity e = null;
                try {
                    e = getEntityByUUID(sl, UUID.fromString(activetrack.id()));
                } catch (Throwable ignored) {}

                if (e == null || !e.isAlive()) {
                    LOGGER.warn("WFC: entity id={} not loaded/alive, stopping fire", activetrack.id());
                    stopFireCannon();
                    return;
                }

                targetEntity = e;
                targetSublevel = null;
            }
        }

        if (!binoMode && activetrack != null && targetEntity == null && targetSublevel == null) {
            LOGGER.warn("WFC: no resolved target entity/ship, stopping fire (trackId={})", activetrack.id());
            stopFireCannon();
            return;
        }

        if (!binoMode) {
            if (targetSublevel != null) {
                target = RadarTrackUtil.getPosition(targetSublevel);
            } else if (targetEntity != null) {
                target = targetEntity.position();
            }
        }else {
            target = binoTargetPos.getCenter();
        }




        AbstractMountedCannonContraption cannonContraption;
        if (cannonMount.getContraption() == null) return;
        if (cannonMount.getContraption().getContraption() instanceof AbstractMountedCannonContraption cannon) {
            cannonContraption = cannon;
        } else return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (targetEntity != null) {
            if (!targetEntity.isAlive()) {
                LOGGER.warn("WFC: target entity died mid-tick, stopping fire (id={})", targetEntity.getUUID());
                stopFireCannon();
                return;
            }
        }
        if (targetSublevel != null && !binoMode) {
            UUID id = UUID.fromString(activetrack.id());
            SubLevelAccess live = SubLevelContainer.getContainer(serverLevel).getSubLevel(id);
            if (live == null) {
                LOGGER.warn("WFC: Sable ship id={} unloaded mid-tick, stopping fire", id);
                stopFireCannon();
                return;
            }
            targetSublevel = live;
        }

        Vec3 shooterVel;
        Vec3 shooterAccel;
        Vec3 targetVel;
        Vec3 targetAccel;
        boolean lag;
        if(Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(level, cannonMount.getBlockPos())){
            SubLevelAccess mountship = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, cannonMount.getBlockPos());
            if(mountship == null){
                shooterVel = Vec3.ZERO;
                shooterAccel = Vec3.ZERO;
            } else{
                shooterVel = VS2ShipVelocityTracker.getShipVelocityPerTick(mountship);
                shooterAccel = AccelerationTracker.getAccelerationPerTick2(mountship.getUniqueId(), shooterVel);
            }
        }else{
            shooterVel = Vec3.ZERO;
            shooterAccel = Vec3.ZERO;
        }
        if(targetSublevel != null){
            target = RadarTrackUtil.getPosition(targetSublevel);
            targetVel = VS2ShipVelocityTracker.getShipVelocityPerTick(targetSublevel);
            targetAccel = AccelerationTracker.getAccelerationPerTick2(targetSublevel.getUniqueId(), targetVel);
        }else if(!binoMode && targetEntity != null){
            target = targetEntity.position();
            targetVel = VelocityTracker.getEstimatedVelocityPerTick(targetEntity);
            targetAccel = AccelerationTracker.getAccelerationPerTick2(targetEntity.getUUID(),targetVel);
        }else if(binoMode && binoTargetPos != null){

            target = binoTargetPos.getCenter();
            targetVel = Vec3.ZERO;
            targetAccel = Vec3.ZERO;
        }else{
            return;
        }
        double dist = getCannonRayStart().distanceTo(target);
        double noLeadDist = 1; // tune this

        Vec3 solvePos = target;

        if (!binoMode && targetEntity != null) {
           // Vec3 vis = checkLineOfSight(targetEntity);

            if (!checkLineOfSight(targetEntity.position())) {
                LOGGER.warn("WFC: LOS blocked to entity, stopping fire (id={})", targetEntity.getUUID());
                stopFireCannon();
                return;
            }

            solvePos = targetEntity.position();
        }
        double maxSpeed = 0.01; // 5 m/s in blocks/tick
        double maxSpeedSqr = maxSpeed * maxSpeed;

        if (targetVel.lengthSqr() > maxSpeedSqr) {
            lag = false; // allows lower tolerance when leading
        }else {
            lag = true;
        }
        CannonLead.LeadSolution lead = null;
        if (!CannonUtil.isLaserCannon(cannonContraption) && dist > noLeadDist) {
            lead = CannonLead.solveLeadPerTickConstantVelocity(
                    cannonMount, cannonContraption, serverLevel,
                    shooterVel,
                    solvePos,
                    targetVel,
                    RadarConfig.server().leadFiringDelay.get(),
                    maxSimDistanceBlocks);
        }

        WeaponNetworkData wnd = WeaponNetworkData.get(serverLevel);
        WeaponNetworkData.Group grp = (wnd != null && pitchController != null) ? wnd.getGroupForController(serverLevel.dimension(), pitchController.getBlockPos()) : null;

        if (grp != null && !grp.dataLinks.isEmpty()) {
            Vec3 cannonOrigin = getCannonRayStart();
            double best = 0.0;

            for (BlockPos dlPos : grp.dataLinks) {
                BlockEntity be = serverLevel.getBlockEntity(dlPos);
                if (!(be instanceof com.happysg.radar.block.datalink.DataLinkBlockEntity dl)) continue;

                BlockPos srcPos = dl.getSourcePosition();
                BlockEntity srcBe = serverLevel.getBlockEntity(srcPos);

                if (srcBe instanceof com.happysg.radar.block.radar.behavior.IRadar radar) {
                    Vec3 radarWorldPos = PhysicsHandler.getWorldVec(srcBe); // VS2-safe world position
                    double d = cannonOrigin.distanceTo(radarWorldPos);
                    double cap = radar.getRange() + d; // relative-to-cannon max distance

                    if (cap > best) best = cap;
                }
            }

            if (best > 0.0) maxSimDistanceBlocks = best;
        }

        boolean hasLeadSolution = (lead != null && lead.aimPoint != null);
        // Laser cannons don't need lead solutions (instantaneous beam)
        boolean canFireWithoutLead = CannonUtil.isLaserCannon(cannonContraption);
        Vec3 offsetAim = hasLeadSolution ? lead.aimPoint : solvePos;
        lastAimPoint = offsetAim;


        if (lastOffsetAim == null || lastOffsetAim.distanceTo(offsetAim) > AIM_STABLE_EPS) {
            aimStableTicks = 0;
            lastOffsetAim = offsetAim;
        } else {
            aimStableTicks++;
        }

        Double desiredPitch = null;
        Double desiredYaw = null;

        if (Mods.SABLE.isLoaded() && PhysicsHandler.isBlockInPlotyard(level, cannonMount.getBlockPos())) {
            long now = level.getGameTime();
            boolean needSolve = cachedVS2Angles == null
                    || (now - cachedVS2SolveTick) >= VS2_SOLVE_INTERVAL
                    || cachedVS2AimTarget == null
                    || cachedVS2AimTarget.distanceToSqr(offsetAim) > VS2_AIM_CHANGE_THRESHOLD * VS2_AIM_CHANGE_THRESHOLD;

            if (needSolve) {
                cachedVS2Angles = VS2CannonTargeting.calculatePitchAndYawVS2(cannonMount, offsetAim, serverLevel);
                cachedVS2AimTarget = offsetAim;
                cachedVS2SolveTick = now;
            }

            List<List<Double>> angles = cachedVS2Angles;
            if (angles != null && !angles.isEmpty() && !angles.get(0).isEmpty()) {
                desiredPitch = angles.get(0).get(0);
                desiredYaw   = angles.get(0).get(1);
            }
        } else {
            Vec3 origin = getCannonRayStart();

            double dx = offsetAim.x - origin.x;
            double dz = offsetAim.z - origin.z;
            double yawDeg = Math.toDegrees(Math.atan2(dz, dx)) + 90.0;
            desiredYaw = yawDeg + 180.0;

            List<Double> pitchRoots = CannonTargeting.calculatePitch(cannonMount, origin, offsetAim, serverLevel);
            if (pitchRoots != null && !pitchRoots.isEmpty()) desiredPitch = pitchRoots.get(0);
        }

        if (desiredPitch != null && pitchController != null) {
            pitchController.setTargetAngle(desiredPitch.floatValue());
        }
        if (desiredYaw != null && yawController != null) {
            yawController.setTargetAngle(desiredYaw.floatValue());
        }

        // Debug
        boolean auto = targetingConfig.autoFire();
        boolean yawPitchOk = hasCorrectYawPitch(lag);
        boolean safeOk = !passesSafeZone();
        boolean cannonReady = CannonUtil.isCannonReadyToFire(cannonMount);
        boolean stableOk = (aimStableTicks >= AIM_STABLE_REQUIRED) || (!lag);

        if (level.getGameTime() % 20 == 0) {
            LOGGER.warn("WFC FIREGATES: auto={} lead={} laserNoLead={} yawPitchOk={} safeOk={} cannonReady={} stableOk={} firingBE={} target={} aim={} offset={} stable={}/{}", auto, hasLeadSolution, canFireWithoutLead, yawPitchOk, safeOk, cannonReady, stableOk, fireController != null, target, offsetAim, offset, aimStableTicks, AIM_STABLE_REQUIRED);
            if (!yawPitchOk) {
                LOGGER.warn("WFC AIMCHK: yawCtrl={} pitchCtrl={} atYaw={} atPitch={} targYaw={} targPitch={}", yawController != null ? yawController.getBlockPos() : null, pitchController != null ? pitchController.getBlockPos() : null, yawController != null && yawController.atTargetYaw(lag), pitchController != null && pitchController.atTargetPitch(lag), yawController != null ? yawController.getTargetAngle() : null, pitchController != null ? pitchController.getTargetAngle() : null);
            }
            if (!auto) LOGGER.warn("WFC BLOCK: autoFire disabled");
            if (!hasLeadSolution && !canFireWithoutLead) LOGGER.warn("WFC BLOCK: no lead solution");
            if (!safeOk) LOGGER.warn("WFC BLOCK: safe zone violation");
            if (!cannonReady) LOGGER.warn("WFC BLOCK: cannon not ready");
            if (!yawPitchOk) LOGGER.warn("WFC BLOCK: yaw/pitch not aligned");
            if (!stableOk) LOGGER.warn("WFC BLOCK: aim not stable");
        }

        boolean shouldFire =
                targetingConfig.autoFire()
                        && (hasLeadSolution || canFireWithoutLead)
                        && yawPitchOk
                        && safeOk
                        && cannonReady
                        && stableOk;

        if (fireController != null) {
            if (shouldFire) tryFireCannon();
            else stopFireCannon();
        }
    }

    public void resetTarget(){
        visCache.clear();
        this.target =null;
        this.activetrack =null;
        this.targetEntity = null;
        this.targetSublevel = null;
        this.targetShipId = -1;

        lastAimPoint = null;
        lastOffsetAim = null;
        aimStableTicks = 0;
        cachedVS2Angles = null;
        cachedVS2AimTarget = null;
        cachedVS2SolveTick = -1;

        stopFireCannon();
    }

    public void setTarget(Vec3 target, TargetingConfig config, RadarTrack track, WeaponNetworkData.WeaponGroupView view){
        LOGGER.warn("setTarget() -> new target={} config={} atTick={}",
                target, config, level != null ? level.getGameTime() : -1L);
        if (target == null) {
            this.target = null;
            this.activetrack = null;
            this.targetEntity = null;
            this.targetSublevel = null;
            this.targetShipId = -1;

            lastAimPoint = null;
            lastOffsetAim = null;
            aimStableTicks = 0;

            stopFireCannon();
            return;
        }
        this.binoMode =false;

        this.target = target;//.add(0,offset,0);

        lastOffsetAim = null;
        aimStableTicks = 0;

        this.targetingConfig = config;
        if (level != null) this.lastTargetTick  = level.getGameTime();
        this.view = view;
        this.activetrack = track;
        this.targetEntity = null;
        this.targetSublevel = null;
    }

    public void setBinoTarget(@Nullable BlockPos binoTarget, TargetingConfig config,
                              WeaponNetworkData.WeaponGroupView view, boolean reset) {

        this.view = view;
        this.targetingConfig = config;
        this.activetrack = null;

        if (reset || binoTarget == null) {
            this.binoMode = false;
            this.binoTargetPos = null;
            this.target = null;
            stopFireCannon();
            return;
        }

        this.binoMode = true;
        this.binoTargetPos = binoTarget.immutable();
        if (level != null) this.lastTargetTick = level.getGameTime();
    }

    private boolean passesSafeZone() {
        if (safeZones == null || safeZones.isEmpty()) return false;

        Vec3 aim = (lastAimPoint != null) ? lastAimPoint : target;
        if (aim == null) return false;

        Vec3 start = getCannonRayStart();
        for (AABB zone : safeZones) {
            if (zone == null) continue;

            if (zone.contains(start) || zone.contains(aim)) {
                return true;
            }

            if (zone.clip(start, aim).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCorrectYawPitch(boolean lag) {
        if(yawController == null && pitchController == null)return false;
        boolean yaw =true;
        if(yawController !=null) {
            yaw = yawController.atTargetYaw(lag);
        }
        boolean pitch = pitchController.atTargetPitch(lag);

        return yaw && pitch;
    }


    private void stopFireCannon() {
        if(this.fireController == null) return;
        fireController.setPowered(false);
    }

    private void tryFireCannon() {
        if(this.fireController == null) return;
        fireController.setPowered(true);
        LOGGER.debug("firing!");

    }


    private boolean isMountStateOk() {
        if (level == null || cannonMount == null) return false;
        if (cannonMount.isRemoved()) return false;

        PitchOrientedContraptionEntity ce = cannonMount.getContraption();
        if (ce == null) return false;
        if (!ce.isAlive()) return false;

        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption)) return false;

        return true;
    }
}
