package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.api.physics.RadarPhysics;
import com.happysg.radar.api.radar.RadarDetectionSettings;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlockEntity;
import com.happysg.radar.block.radar.track.RadarEntityTypeTags;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import redstonedubstep.mods.vanishmod.VanishUtil;

import java.util.*;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SonarScanningBlockBehavior extends BlockEntityBehaviour {
    public static final BehaviourType<SonarScanningBlockBehavior> TYPE = new BehaviourType<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SCAN_PERIOD_TICKS = 20;
    public static final int MAX_AIR_BLOCKS = 16;
    private static final int TRACK_EXPIRATION_TICKS = 100;
    private static final int CANDIDATE_REFRESH_TICKS = 5;
    public static final double SWEEP_DEGREES_PER_TICK = 19.2;
    private static final int MAX_RAYS_PER_TICK = 64;
    private static final int MAX_SENSOR_VISITS_PER_TICK = 256;
    private static final int MAX_CONTACTS_PER_TICK = 64;


    private final SonarBearingBlockEntity sonar;
    private final Map<String, RadarTrack> radarTracks = new HashMap<>();
    private final Set<Entity> entityCandidates = new HashSet<>();
    private final Set<SubLevelAccess> shipCandidates = new HashSet<>();
    private long lastCandidateRefreshTick = Long.MIN_VALUE;

    private double range;
    private boolean running;

    private boolean scanPlayers = true;
    private boolean scanSable = true;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;

    public SonarScanningBlockBehavior(SonarBearingBlockEntity sonar) {
        super(sonar);
        this.sonar = sonar;
    }

    private static final class ScanBudget {
        int raysLeft = MAX_RAYS_PER_TICK;
        int visitsLeft = MAX_SENSOR_VISITS_PER_TICK;
        int contactsLeft = MAX_CONTACTS_PER_TICK;

        boolean hasCapacity() {
            return raysLeft > 0 && visitsLeft > 0 && contactsLeft > 0;
        }

        boolean canTrace() {
            return raysLeft > 0 && visitsLeft > 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(sonar.getLevel() instanceof ServerLevel serverLevel)) return;
        long gameTime = serverLevel.getGameTime();

        if (gameTime % 100 == 0) {
            LOGGER.debug(
                    "[SONAR] pos={} speed={} running={} sensors={} candidates={} ships={} tracks={}",
                    sonar.getBlockPos(),
                    sonar.getSpeed(),
                    running,
                    sonar.getSensorPositions().size(),
                    entityCandidates.size(),
                    shipCandidates.size(),
                    radarTracks.size()
            );
        }

        expireOldTracks(gameTime);

        if (!running || range <= 0) {
            clearCandidateCache();
            return;
        }

        if (lastCandidateRefreshTick == Long.MIN_VALUE || gameTime < lastCandidateRefreshTick || gameTime - lastCandidateRefreshTick >= CANDIDATE_REFRESH_TICKS) {

            refreshEntityCandidates(serverLevel);
            lastCandidateRefreshTick = gameTime;
        }

        scanCurrentSweep(serverLevel);
    }

    private void refreshEntityCandidates(ServerLevel level) {
        entityCandidates.clear();
        shipCandidates.clear();

        List<Vec3> sensors = getSensorWorldPositions();
        if (sensors.isEmpty()) return;

        AABB scanBounds = createScanBounds(sensors);

        for (AABB section : RadarScanningBlockBehavior.splitAABB(scanBounds, 256.0)) {
            for (Entity entity : level.getEntitiesOfClass(Entity.class, section, this::shouldScanEntity)) {
                if (entity instanceof AbstractContraptionEntity contraption && sonar.isAttachedTo(contraption)) {
                    continue;
                }

                entityCandidates.add(entity);
            }
        }

        if (Mods.SABLE.isLoaded() && scanSable) {
            SubLevelAccess ownShip = SableUtils.getShipManagingPos(sonar);

            for (SubLevelAccess ship : SableUtils.getLoadedShips(level, scanBounds)) {
                if (ship == null || (ownShip != null && ownShip.getUniqueId().equals(ship.getUniqueId()))) {
                    continue;
                }

                shipCandidates.add(ship);
            }
        }
    }

    private List<Vec3> getSensorWorldPositions() {
        Collection<BlockPos> positions = sonar.getSensorPositions();

        if (positions.isEmpty()) {
            return List.of();
        }

        SubLevelAccess carrier = Mods.SABLE.isLoaded() ? SableUtils.getShipManagingPos(sonar) : null;
        List<Vec3> sensors = new ArrayList<>(positions.size());

        for (BlockPos pos : positions) {
            Vec3 center = Vec3.atCenterOf(pos);
            sensors.add(carrier == null ? center : SableUtils.getWorldVec(center, carrier));
        }

        return sensors;
    }

    private boolean isWithinSensorRange(Vec3 target, List<Vec3> sensors) {
        double rangeSquared = range * range;

        for (Vec3 sensor : sensors) {
            if (sensor.distanceToSqr(target) <= rangeSquared) {
                return true;
            }
        }

        return false;
    }

    private void scanCurrentSweep(ServerLevel level) {
        List<Vec3> sensors = getSensorWorldPositions();
        if (sensors.isEmpty()) return;

        Vec3 sweepCenter = RadarPhysics.worldPosition(sonar);
        double sweepAngle = (180.0 - sonar.getGlobalAngle() + 360.0 + SWEEP_DEGREES_PER_TICK * 0.5) % 360.0;

        List<Entity> inSector = new ArrayList<>();

        for (Entity entity : entityCandidates) {
            if (entity.level() != level || !shouldScanEntity(entity))
                continue;

            if (entity instanceof AbstractContraptionEntity contraption && sonar.isAttachedTo(contraption))
                continue;

            Vec3 target = entity.position();

            if (!RadarScanningBlockBehavior.isWithinHorizontalFov(sweepCenter, target, sweepAngle, RadarConfig.server().sonarFOV.get()))
                continue;

            if (!isWithinSensorRange(target, sensors))
                continue;

            inSector.add(entity);
        }


        inSector.sort(Comparator
                .comparing((Entity entity) -> !radarTracks.containsKey(entity.getUUID().toString()))
                .thenComparingDouble(entity -> entity.position().distanceToSqr(sweepCenter)));

        ScanBudget budget = new ScanBudget();

        boolean shipsFirst = (level.getGameTime() & 1L) == 0L;

        if (shipsFirst && Mods.SABLE.isLoaded() && scanSable) {
            scanSableShips(level, sensors, sweepCenter, sweepAngle, budget);
        }

        for (Entity entity : inSector) {
            if (!budget.hasCapacity())
                break;

            if (!detectNow(level, entity.position(), sensors, budget))
                continue;

            radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                if (track == null)
                    return new RadarTrack(entity);

                track.updateRadarTrack(entity);
                return track;
            });
        }

        if (!shipsFirst && Mods.SABLE.isLoaded() && scanSable) {
            scanSableShips(level, sensors, sweepCenter, sweepAngle, budget);
        }
    }

    private void scanSableShips(ServerLevel level, List<Vec3> sensors, Vec3 sweepCenter, double sweepAngle, ScanBudget budget) {
        SubLevelAccess ownShip = SableUtils.getShipManagingPos(sonar);
        List<SubLevelAccess> ships = new ArrayList<>(shipCandidates);

        ships.sort(Comparator.comparing(ship -> !radarTracks.containsKey(ship.getUniqueId().toString())));

        for (SubLevelAccess ship : ships) {
            if (!budget.hasCapacity())
                break;

            if (ship == null || (ownShip != null && ownShip.getUniqueId().equals(ship.getUniqueId())))
                continue;

            Vec3 target = RadarTrackUtil.getPosition(ship);
            if (target == null)
                continue;

            if (!RadarScanningBlockBehavior.isWithinHorizontalFov(sweepCenter, target, sweepAngle, RadarConfig.server().sonarFOV.get()))
                continue;

            if (!isWithinSensorRange(target, sensors))
                continue;

            if (!detectNow(level, target, sensors, budget))
                continue;

            radarTracks.compute(ship.getUniqueId().toString(), (id, track) -> {
                if (track == null) return RadarTrackUtil.getRadarTrack(ship, level);

                track.updateRadarTrack(ship, level);
                return track;
            });
        }
    }

    private boolean detectNow(ServerLevel level, Vec3 target, List<Vec3> sensors, ScanBudget budget) {
        if (!budget.hasCapacity())
            return false;

        budget.contactsLeft--;

        int nearestIndex = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < sensors.size(); i++) {
            double distance = sensors.get(i).distanceToSqr(target);

            if (distance > range * range)
                continue;

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        if (nearestIndex < 0)
            return false;

        if (testSensor(level, sensors.get(nearestIndex), target, budget))
            return true;

        for (int i = 0; i < sensors.size() && budget.canTrace(); i++) {
            if (i == nearestIndex)
                continue;

            if (testSensor(level, sensors.get(i), target, budget))
                return true;
        }

        return false;
    }

    private boolean testSensor(ServerLevel level, Vec3 origin, Vec3 target, ScanBudget budget) {
        if (!budget.canTrace())
            return false;

        budget.visitsLeft--;

        if (origin.distanceToSqr(target) > range * range)
            return false;

        budget.raysLeft--;

        return signalSurvives(level, origin, target);
    }

    private void clearCandidateCache() {
        entityCandidates.clear();
        shipCandidates.clear();
        lastCandidateRefreshTick = Long.MIN_VALUE;
    }

    private AABB createScanBounds(List<Vec3> sensors) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vec3 origin : sensors) {
            minX = Math.min(minX, origin.x);
            minY = Math.min(minY, origin.y);
            minZ = Math.min(minZ, origin.z);

            maxX = Math.max(maxX, origin.x);
            maxY = Math.max(maxY, origin.y);
            maxZ = Math.max(maxZ, origin.z);
        }

        return new AABB(
                minX - range, minY - range, minZ - range,
                maxX + range, maxY + range, maxZ + range
        );
    }

    /**
     * Sonar propagation rule:
     *
     * solid -> propagates
     * fluid -> propagates
     * total air <= configured limit -> propagates
     * total air > configured limit -> signal dies
     */
    private boolean signalSurvives(ServerLevel level, Vec3 start, Vec3 end) {
        if (traceSignal(level, start, end, List.of())) {
            return true;
        }

        if (!Mods.SABLE.isLoaded()) {
            return false;
        }

        List<SubLevel> intersectingShips = new ArrayList<>();
        SableUtils.getLoadedShips(level, new AABB(start, end).inflate(1.0E-4)).forEach(intersectingShips::add);

        if (intersectingShips.isEmpty()) {
            return false;
        }

        return traceSignal(level, start, end, intersectingShips);
    }

    private boolean traceSignal(ServerLevel level, Vec3 start, Vec3 end, Collection<SubLevel> intersectingShips) {
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);

        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        int stepX = Double.compare(dx, 0.0);
        int stepY = Double.compare(dy, 0.0);
        int stepZ = Double.compare(dz, 0.0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = initialTMax(start.x, x, stepX, dx);
        double tMaxY = initialTMax(start.y, y, stepY, dy);
        double tMaxZ = initialTMax(start.z, z, stepZ, dz);

        int airBlocks = 0;
        int worldBlocks = 0;
        int sableBlocks = 0;

        int maxAirBlocks = RadarConfig.server().sonarMaxAirBlocks.get();

        double entryT = 0.0;
        int guard = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 8;

        while (guard-- > 0) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return false;
            }

            double nextT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            double exitT = Math.min(1.0, nextT);
            double sampleT = Mth.clamp((entryT + Math.max(entryT, exitT)) * 0.5, 0.0, 1.0);

            boolean isAir = level.getBlockState(pos).isAir();

            if (!isAir) {
                worldBlocks++;
            }

            if (isAir && !intersectingShips.isEmpty()) {
                Vec3 samplePoint = new Vec3(start.x + dx * sampleT, start.y + dy * sampleT, start.z + dz * sampleT);

                if (isOccupiedInSable(intersectingShips, samplePoint)) {
                    isAir = false;
                    sableBlocks++;
                }
            }

            if (isAir) {
                airBlocks++;

                if (airBlocks > maxAirBlocks) {
                    return false;
                }
            }

            if (x == endX && y == endY && z == endZ) {
                if (level.getGameTime() % 20 == 0 && start.distanceToSqr(end) > 256.0) {

                    LOGGER.debug(
                            "[SONAR RAY] path={} distance={} air={} worldBlocks={} sableBlocks={} origin={} target={}",
                            intersectingShips.isEmpty() ? "WORLD" : "SABLE",
                            Math.sqrt(start.distanceToSqr(end)),
                            airBlocks,
                            worldBlocks,
                            sableBlocks,
                            start,
                            end
                    );
                }

                return true;
            }

            final double epsilon = 1.0E-10;

            if (tMaxX <= nextT + epsilon) {
                x += stepX;
                tMaxX += tDeltaX;
            }

            if (tMaxY <= nextT + epsilon) {
                y += stepY;
                tMaxY += tDeltaY;
            }

            if (tMaxZ <= nextT + epsilon) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }

            entryT = Math.max(entryT, exitT);
        }

        return false;
    }

    private static boolean isOccupiedInSable(Collection<SubLevel> ships, Vec3 worldPoint) {
        for (SubLevel ship : ships) {
            Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z));
            BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
            Level shipLevel = ship.getLevel();

            if (shipLevel == null || !shipLevel.hasChunkAt(localPos)) {
                continue;
            }

            if (!shipLevel.getBlockState(localPos).isAir()) {
                return true;
            }
        }

        return false;
    }

    private static double initialTMax(double start, int cell, int step, double delta) {
        if (step == 0)
            return Double.POSITIVE_INFINITY;

        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - start) / delta;
    }

    private boolean shouldScanEntity(Entity entity) {
        if (entity == null || !entity.isAlive())
            return false;

        if (isMissile(entity))
            return true;

        if (entity instanceof Player player) {
            if (!scanPlayers || player.isSpectator())
                return false;

            if (entity instanceof ServerPlayer serverPlayer && Mods.VMOD.isLoaded() && VanishUtil.isVanished(serverPlayer)) {
                return false;
            }

            return true;
        }

        if (entity instanceof Projectile)
            return scanProjectiles;

        if (entity instanceof ItemEntity)
            return scanItems;

        if (entity instanceof AbstractContraptionEntity)
            return scanContraptions;

        if (entity instanceof Animal)
            return scanAnimals;

        if (entity instanceof Mob)
            return scanMobs;

        return allowCategory(TrackCategory.get(entity));
    }

    private static boolean isMissile(Entity entity) {
        return entity.getType().is(RadarEntityTypeTags.RADAR_MISSILE);
    }

    private boolean allowCategory(TrackCategory category) {
        return switch (category) {
            case PLAYER -> scanPlayers;
            case SABLE -> scanSable;
            case CONTRAPTION -> scanContraptions;
            case PROJECTILE -> scanProjectiles;
            case MISSILE -> true;
            case ITEM -> scanItems;
            case ANIMAL -> scanAnimals;
            case HOSTILE, MOB -> scanMobs;
            default -> true;
        };
    }

    private void expireOldTracks(long gameTime) {
        radarTracks.values().removeIf(track -> gameTime - track.scannedTime() > TRACK_EXPIRATION_TICKS);
    }

    public void applyDetectionConfig(DetectionConfig config) {
        if (config == null) {
            config = DetectionConfig.DEFAULT;
        }

        applyDetectionSettings(config.toApiSettings());
    }

    public void applyDetectionSettings(RadarDetectionSettings settings) {
        if (settings == null) {
            applyDetectionConfig(DetectionConfig.DEFAULT);
            return;
        }

        boolean changed = scanPlayers != settings.player()
                || scanSable != settings.sable()
                || scanContraptions != settings.contraption()
                || scanMobs != settings.mob()
                || scanAnimals != settings.animal()
                || scanProjectiles != settings.projectile()
                || scanItems != settings.item();

        if (!changed) {
            return;
        }

        scanPlayers = settings.player();
        scanSable = settings.sable();
        scanContraptions = settings.contraption();
        scanMobs = settings.mob();
        scanAnimals = settings.animal();
        scanProjectiles = settings.projectile();
        scanItems = settings.item();
        radarTracks.values().removeIf(track -> !allowCategory(track.trackCategory()));
        clearCandidateCache();
    }

    public Collection<RadarTrack> getRadarTracks() {
        return radarTracks.values();
    }

    public void setRange(double range) {
        double newRange = Math.max(0, range);

        if (Double.compare(this.range, newRange) != 0) {
            this.range = newRange;
            clearCandidateCache();
        }
    }

    public void setRunning(boolean running) {
        this.running = running;

        if (!running) {
            clearCandidateCache();
        }
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        if (tag.contains("Range"))
            range = tag.getDouble("Range");

        if (tag.contains("Running"))
            running = tag.getBoolean("Running");
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putDouble("Range", range);
        tag.putBoolean("Running", running);
    }
}
