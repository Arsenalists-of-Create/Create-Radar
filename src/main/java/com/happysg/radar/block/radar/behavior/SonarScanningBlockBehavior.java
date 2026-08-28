package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlockEntity;
import com.happysg.radar.block.radar.track.RadarEntityTypeTags;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SubLevelAccess;
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
import redstonedubstep.mods.vanishmod.VanishUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SonarScanningBlockBehavior extends BlockEntityBehaviour {
    public static final BehaviourType<SonarScanningBlockBehavior> TYPE = new BehaviourType<>();

    public static final int SCAN_PERIOD_TICKS = 40;
    public static final int MAX_AIR_BLOCKS = 16;
    private static final int TRACK_EXPIRATION_TICKS = 100;
    private final SonarBearingBlockEntity sonar;
    private final Map<String, RadarTrack> radarTracks = new HashMap<>();

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

    @Override
    public void tick() {
        super.tick();

        Level level = sonar.getLevel();

        if (!(level instanceof ServerLevel serverLevel))
            return;

        long gameTime = serverLevel.getGameTime();

        if (gameTime % 5 == 0) {
            expireOldTracks(gameTime);
        }

        if (!running || range <= 0)
            return;

        if (gameTime % SCAN_PERIOD_TICKS != 0)
            return;

        performScan(serverLevel);
    }

    private void performScan(ServerLevel level) {
        Vec3 origin = PhysicsHandler.getWorldVec(sonar);

        AABB scanBounds = createScanBounds(level, origin);

        for (Entity entity : level.getEntitiesOfClass(Entity.class, scanBounds, this::shouldScanEntity)) {
            Vec3 target = entity.position();

            if (!insideCoverage(level, origin, target))
                continue;

            if (!signalSurvivesFromSensors(level, target))
                continue;

            radarTracks.compute(entity.getUUID().toString(), (id, existing) -> {
                if (existing == null)
                    return new RadarTrack(entity);

                existing.updateRadarTrack(entity);
                return existing;
            });
        }

        if (Mods.SABLE.isLoaded() && scanSable) {
            scanSableShips(level, origin, scanBounds);
        }
    }

    private void scanSableShips(ServerLevel level, Vec3 origin, AABB scanBounds) {
        SubLevelAccess ownShip = SableUtils.getShipManagingPos(sonar);

        for (SubLevelAccess ship : SableUtils.getLoadedShips(level, scanBounds)) {
            if (ship == null)
                continue;

            if (ownShip != null && ownShip.getUniqueId().equals(ship.getUniqueId())) {
                continue;
            }

            Vec3 target = RadarTrackUtil.getPosition(ship);

            if (!insideCoverage(level, origin, target))
                continue;

            if (!signalSurvivesFromSensors(level, target))
                continue;

            radarTracks.compute(ship.getUniqueId().toString(), (id, existing) -> {
                if (existing == null)
                    return RadarTrackUtil.getRadarTrack(ship, level);

                existing.updateRadarTrack(ship, level);
                return existing;
            });
        }
    }

    private AABB createScanBounds(ServerLevel level, Vec3 origin) {
        double minY = level.getMinBuildHeight();

        return new AABB(
                origin.x - range,
                minY,
                origin.z - range,

                origin.x + range,
                origin.y + 0.001,
                origin.z + range
        );
    }

    private boolean insideCoverage(
            ServerLevel level,
            Vec3 origin,
            Vec3 target
    ) {
        if (target.y > origin.y)
            return false;

        if (target.y < level.getMinBuildHeight())
            return false;

        double dx = target.x - origin.x;
        double dz = target.z - origin.z;

        return dx * dx + dz * dz <= range * range;
    }

    /**
     * Sonar propagation rule:
     *
     * solid -> propagates
     * fluid -> propagates
     * air <= 16 consecutive blocks -> propagates
     * air > 16 consecutive blocks -> signal dies
     */
    private boolean signalSurvives(ServerLevel level, Vec3 start, Vec3 end) {
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

        int guard = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 8;

        while (guard-- > 0) {
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.hasChunkAt(pos))
                return false;

            if (level.getBlockState(pos).isAir()) {
                airBlocks++;

                if (airBlocks > MAX_AIR_BLOCKS) {
                    return false;
                }
            }

            if (x == endX && y == endY && z == endZ) {
                return true;
            }

            double nextT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            // Step all axes tied at this boundary.
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
        if (config == null)
            config = DetectionConfig.DEFAULT;

        scanPlayers = config.player();
        scanSable = config.sable();
        scanContraptions = config.contraption();
        scanMobs = config.mob();
        scanAnimals = config.animal();
        scanProjectiles = config.projectile();
        scanItems = config.item();

        radarTracks.values().removeIf(track -> !allowCategory(track.trackCategory()));
    }

    private boolean signalSurvivesFromSensors(ServerLevel level, Vec3 target) {
        Collection<BlockPos> sensors = sonar.getSensorPositions();

        if (sensors.isEmpty()) {
            return false;
        }

        for (BlockPos sensorPos : sensors) {
            Vec3 sensorOrigin = Vec3.atCenterOf(sensorPos);

            if (signalSurvives(level, sensorOrigin, target)) {
                return true;
            }
        }

        return false;
    }

    public Collection<RadarTrack> getRadarTracks() {
        return radarTracks.values();
    }

    public void setRange(double range) {
        this.range = Math.max(0, range);
    }

    public void setRunning(boolean running) {
        this.running = running;

        if (!running) {

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
