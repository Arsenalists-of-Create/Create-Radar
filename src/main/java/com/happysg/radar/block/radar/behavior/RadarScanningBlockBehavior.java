package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;

import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.RadarEntityTypeTags;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.*;
import java.util.function.Predicate;

public class RadarScanningBlockBehavior extends BlockEntityBehaviour {

    public static final BehaviourType<RadarScanningBlockBehavior> TYPE = new BehaviourType<>();
    private static final double RWR_PASSIVE_RANGE_MULTIPLIER = 1.5;

    private int trackExpiration = 100;
    private int fov = RadarConfig.server().radarFOV.get();
    private int yRange = 20;
    private double range = RadarConfig.server().radarBaseRange.get();
    private double angle;
    private boolean running = false;
    private SmartBlockEntity bearingEntity;
    private RadarBearingBlockEntity radarBearing;
    private final RadarType radarType;
    Vec3 scanPos = Vec3.ZERO;

    private final Set<Entity> scannedEntities = new HashSet<>();
    private final Set<SubLevelAccess> scannedShips = new HashSet<>();
    private final Set<Projectile> scannedProjectiles = new HashSet<>();
    private final HashMap<String, RadarTrack> radarTracks = new HashMap<>();

    public RadarScanningBlockBehavior(SmartBlockEntity be) {
        this(be, RadarType.GROUND);
    }

    public RadarScanningBlockBehavior(SmartBlockEntity be, RadarType radarType) {
        super(be);
        this.bearingEntity = be;
        this.radarType = radarType;
    }

    public void applyDetectionConfig(DetectionConfig cfg) {
        if (cfg == null) cfg = DetectionConfig.DEFAULT;
        setScanFlags(
                cfg.player(),
                cfg.sable(),
                cfg.contraption(),
                cfg.mob(),
                cfg.animal(),
                cfg.projectile(),
                cfg.item()
        );
    }


    private boolean scanPlayers = true;
    private boolean scanSable = true;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;

    private boolean allowCategory(TrackCategory c) {
        return switch (c) {
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

    private void pruneDisabledTracksNow() {
        radarTracks.entrySet().removeIf(e -> !allowCategory(e.getValue().trackCategory()));
    }

    public void setScanFlags(boolean players, boolean sable, boolean contraptions, boolean mobs, boolean animals, boolean projectiles, boolean items) {
        boolean changed = players != scanPlayers || sable != scanSable || contraptions != scanContraptions || mobs != scanMobs || animals != scanAnimals || projectiles != scanProjectiles || items != scanItems;

        this.scanPlayers = players;
        this.scanSable = sable;
        this.scanContraptions = contraptions;
        this.scanMobs = mobs;
        this.scanAnimals = animals;
        this.scanProjectiles = projectiles;
        this.scanItems = items;

        if (changed) {
            pruneDisabledTracksNow();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide)
            return;
        if(blockEntity.getLevel().getGameTime() %5!=1)return;
        removeDeadTracks();
        if (running)
            updateRadarTracks();
        if (running) {
            scannedEntities.clear();
            scannedShips.clear();
            scannedProjectiles.clear();

            scanForEntityTracks();
            if (Mods.SABLE.isLoaded() && scanSable)
                scanForSableTracks();
        }
    }


    private void updateRadarTracks() {
        scanPos = PhysicsHandler.getWorldVec(bearingEntity);
        Level level = blockEntity.getLevel();
        boolean isServer = level instanceof net.minecraft.server.level.ServerLevel;
        net.minecraft.server.level.ServerLevel sl = isServer ? (net.minecraft.server.level.ServerLevel) level : null;
        if (level == null )return;


        for (Entity entity : scannedEntities) {
            if (entity.isAlive()
                    && isInFovAndRange(entity.position())
                    && !RadarOcclusion.isOccluded(bearingEntity, scanPos, entity, radarType)) {
                radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(entity);
                    track.updateRadarTrack(entity);
                    return track;
                });


                if (entity instanceof Projectile)
                    scannedProjectiles.add((Projectile) entity);
            }
        }

        for (SubLevelAccess ship : scannedShips) {
            Vec3 pos = RadarTrackUtil.getPosition(ship);
            boolean inPassiveCoverage = isInPassiveRwrCoverage(pos);
            boolean inActiveCoverage = isInFovAndRange(pos);
            if (!inPassiveCoverage && !inActiveCoverage) {
                continue;
            }

            boolean occluded = RadarOcclusion.isOccluded(bearingEntity, scanPos, pos, radarType, ship);
            if (inPassiveCoverage && isServer && !occluded) {
                RadarContactRegistry.markInRange(sl, ship.getUniqueId(), radarSourceId(sl), 40, redstoneSignalFor(pos));
            }

            if (inActiveCoverage && !occluded) {

                radarTracks.compute(ship.getUniqueId().toString(), (id, track) -> {
                    if (track == null) return RadarTrackUtil.getRadarTrack(ship, level);
                    track.updateRadarTrack(ship, level);
                    return track;
                });
            }
        }
    }

    private String radarSourceId(net.minecraft.server.level.ServerLevel level) {
        return level.dimension().location() + "|" + blockEntity.getBlockPos().asLong();
    }

    private int redstoneSignalFor(Vec3 target) {
        return redstoneSignalFor(target, scanPos);
    }

    private int redstoneSignalFor(Vec3 target, Vec3 radarPos) {
        if (range <= 0.0) {
            return 1;
        }

        double dx = target.x() - radarPos.x();
        double dz = target.z() - radarPos.z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double normalized = Math.max(0.0, Math.min(1.0, horizontalDistance / range));
        return Math.max(1, Math.min(14, (int) Math.ceil((1.0 - normalized) * 14.0)));
    }

    private boolean isInRadarCoverage(Vec3 target) {
        return isInRadarCoverage(target, scanPos);
    }

    private boolean isInRadarCoverage(Vec3 target, Vec3 radarPos) {
        double horizontalDistance = Math.sqrt(Math.pow(target.x() - radarPos.x(), 2) + Math.pow(target.z() - radarPos.z(), 2));
        double verticalDistance = Math.abs(target.y() - radarPos.y());
        double yScanRange = RadarConfig.server().radarYScanRange.get();

        return horizontalDistance <= range && verticalDistance <= yScanRange;
    }

    private boolean isInPassiveRwrCoverage(Vec3 target) {
        return isInPassiveRwrCoverage(target, scanPos);
    }

    private boolean isInPassiveRwrCoverage(Vec3 target, Vec3 radarPos) {
        double horizontalDistance = Math.sqrt(Math.pow(target.x() - radarPos.x(), 2) + Math.pow(target.z() - radarPos.z(), 2));
        double verticalDistance = Math.abs(target.y() - radarPos.y());
        double yScanRange = RadarConfig.server().radarYScanRange.get();

        return horizontalDistance <= range * RWR_PASSIVE_RANGE_MULTIPLIER && verticalDistance <= yScanRange;
    }

    private boolean isInFovAndRange(Vec3 target) {
        return isInFovAndRange(target, scanPos);
    }

    private boolean isInFovAndRange(Vec3 target, Vec3 radarPos) {
        if (!isInRadarCoverage(target, radarPos))
            return false;

        double horizontalDistance = Math.sqrt(Math.pow(target.x() - radarPos.x(), 2) + Math.pow(target.z() - radarPos.z(), 2));

        if (horizontalDistance < 2)
            return true;

        double angleToEntity = Math.toDegrees(Math.atan2(target.x() - radarPos.x(), target.z() - radarPos.z()));
        angleToEntity = (angleToEntity + 360) % 360;
        double angleDiff = Math.abs(angleToEntity - angle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        return angleDiff <= fov / 2.0;
    }

    public boolean canDetectRwrReceiver(RwrTargetReference receiver, ServerLevel level) {
        Optional<Vec3> pos = receiver.resolvePosition(level);
        if (pos.isEmpty()) {
            return false;
        }
        Vec3 radarPos = PhysicsHandler.getWorldVec(bearingEntity);
        return isInPassiveRwrCoverage(pos.get(), radarPos)
                && !RadarOcclusion.isOccluded(
                bearingEntity, radarPos, level, receiver, pos.get(), radarType);
    }

    public boolean canLockRwrTarget(RwrTargetReference target, ServerLevel level) {
        if (!target.isLiveLockTarget(level)) {
            return false;
        }
        Optional<Vec3> pos = target.resolvePosition(level);
        if (pos.isEmpty()) {
            return false;
        }
        if (!canScanTarget(target, level)) {
            return false;
        }
        Vec3 radarPos = PhysicsHandler.getWorldVec(bearingEntity);
        // Lock capability reuses the scanner's real FOV/range test, not a separate distance approximation.
        return isInFovAndRange(pos.get(), radarPos)
                && !RadarOcclusion.isOccluded(
                bearingEntity, radarPos, level, target, pos.get(), radarType);
    }

    public float signalStrengthForRwrReceiver(RwrTargetReference receiver, ServerLevel level) {
        Optional<Vec3> pos = receiver.resolvePosition(level);
        if (pos.isEmpty()) {
            return 0.0F;
        }
        Vec3 radarPos = PhysicsHandler.getWorldVec(bearingEntity);
        return isInPassiveRwrCoverage(pos.get(), radarPos)
                && !RadarOcclusion.isOccluded(
                bearingEntity, radarPos, level, receiver, pos.get(), radarType)
                ? redstoneSignalFor(pos.get(), radarPos)
                : 0.0F;
    }

    private boolean canScanTarget(RwrTargetReference target, ServerLevel level) {
        if (target.kind() == RwrTargetReference.Kind.SABLE_SHIP) {
            return Mods.SABLE.isLoaded() && scanSable && target.resolveSableShip(level) != null;
        }

        Entity entity = target.resolveEntity(level);
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (isMissile(entity)) {
            return true;
        }
        if (entity instanceof Player) {
            return scanPlayers && shouldRadarSee(entity);
        }
        if (entity instanceof Projectile) {
            return scanProjectiles;
        }
        if (entity instanceof ItemEntity) {
            return scanItems;
        }
        if (entity instanceof AbstractContraptionEntity) {
            return scanContraptions;
        }
        if (entity instanceof Animal) {
            return scanAnimals;
        }
        if (entity instanceof Mob) {
            return scanMobs;
        }

        TrackCategory category = TrackCategory.get(entity);
        return allowCategory(category);
    }

    private void removeDeadTracks() {
        // entities
        for (Entity entity : scannedEntities) {
            if (!entity.isAlive() || entity instanceof ServerPlayer && !shouldRadarSee(entity))
                radarTracks.remove(entity.getUUID().toString());
        }

        // ttl expiration (works for everything: entities, ships, projectiles)
        List<String> toRemove = new ArrayList<>();
        assert blockEntity.getLevel() != null;
        long currentTime = blockEntity.getLevel().getGameTime();
        for (RadarTrack track : radarTracks.values()) {
            if (currentTime - track.scannedTime() > trackExpiration)
                toRemove.add(track.id());
        }
        toRemove.forEach(radarTracks::remove);

        // projectiles
        scannedProjectiles.removeIf(p -> {
            boolean dead = !p.isAlive();
            if (dead) radarTracks.remove(p.getUUID().toString());
            return dead;
        });
    }
    private void scanForEntityTracks() {
        Level level = blockEntity.getLevel();
        if (level == null) return;
        Predicate<Entity> radarFilter = this::shouldRadarSee;
        boolean scanAll =
                scanPlayers && scanContraptions && scanMobs && scanAnimals && scanProjectiles && scanItems;

        for (AABB aabb : splitAABB(getRadarAABB(), 256)) {


            if (scanPlayers)
                scannedEntities.addAll(level.getEntitiesOfClass(Player.class, aabb, radarFilter));

            if (scanProjectiles)
                scannedEntities.addAll(level.getEntitiesOfClass(Projectile.class, aabb));

            scannedEntities.addAll(level.getEntitiesOfClass(Entity.class, aabb, RadarScanningBlockBehavior::isMissile));

            if (scanItems)
                scannedEntities.addAll(level.getEntitiesOfClass(ItemEntity.class, aabb));

            if (scanContraptions)
                scannedEntities.addAll(level.getEntitiesOfClass(AbstractContraptionEntity.class, aabb));

            if (scanAnimals)
                scannedEntities.addAll(level.getEntitiesOfClass(Animal.class, aabb));

            if (scanMobs) {
                scannedEntities.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, aabb,
                        e -> !(e instanceof Animal)));
            }
        }
    }

    private static boolean isMissile(Entity entity) {
        return entity.getType().is(RadarEntityTypeTags.RADAR_MISSILE);
    }

    private boolean shouldRadarSee(Entity entity) {
        if(!(entity instanceof ServerPlayer serverPlayer)){
            return false;
        }
        if (serverPlayer.isSpectator()) {
            return false;
        }
        if(Mods.VMOD.isLoaded()) {
            return !VanishUtil.isVanished(serverPlayer);
        }
        return true;
    }


    private void scanForSableTracks() {
        if (blockEntity.getLevel() == null || !Mods.SABLE.isLoaded()) return;
        splitAABB(getRadarAABB(), 256).forEach(aabb ->
                SableUtils.getLoadedShips(blockEntity.getLevel(), aabb).forEach(scannedShips::add));

        scannedShips.remove(SableUtils.getShipManagingPos(blockEntity));
    }
    private AABB getRadarAABB() {
        Vec3 radarPos = PhysicsHandler.getWorldVec(blockEntity);
        double x = radarPos.x;
        double y = radarPos.y;
        double z = radarPos.z;

        double yScan = RadarConfig.server().radarYScanRange.get();
        Level level = blockEntity.getLevel();
        double minY = level != null ? Math.max(y - yScan, level.getMinBuildHeight()) : y - yScan;
        double maxY = level != null ? Math.min(y + yScan, level.getMaxBuildHeight()) : y + yScan;

        return new AABB(
                x - range, minY, z - range,
                x + range, maxY, z + range
        );
    }


    public static List<AABB> splitAABB(AABB aabb, double maxSize) {
        List<AABB> result = new ArrayList<>();
        for (double x = aabb.minX; x < aabb.maxX; x += maxSize) {
            for (double y = aabb.minY; y < aabb.maxY; y += maxSize) {
                for (double z = aabb.minZ; z < aabb.maxZ; z += maxSize) {
                    result.add(new AABB(
                            x, y, z,
                            Math.min(x + maxSize, aabb.maxX),
                            Math.min(y + maxSize, aabb.maxY),
                            Math.min(z + maxSize, aabb.maxZ)
                    ));
                }
            }
        }
        return result;
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if (nbt.contains("fov")) fov = nbt.getInt("fov");
        if (nbt.contains("yRange")) yRange = nbt.getInt("yRange");
        if (nbt.contains("range")) range = nbt.getDouble("range");
        if (nbt.contains("angle")) angle = nbt.getDouble("angle");
        if (nbt.contains("scanPosX")) scanPos = new Vec3(nbt.getDouble("scanPosX"), nbt.getDouble("scanPosY"), nbt.getDouble("scanPosZ"));
        if (nbt.contains("running")) running = nbt.getBoolean("running");
        if (nbt.contains("trackExpiration")) trackExpiration = nbt.getInt("trackExpiration");
    }

    @Override
    public void write(CompoundTag nbt,HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt,registries, clientPacket);
        nbt.putInt("fov", fov);
        nbt.putInt("yRange", yRange);
        nbt.putDouble("range", range);
        nbt.putDouble("angle", angle);
        nbt.putDouble("scanPosX", scanPos.x);
        nbt.putDouble("scanPosY", scanPos.y);
        nbt.putDouble("scanPosZ", scanPos.z);
        nbt.putBoolean("running", running);
        nbt.putInt("trackExpiration", trackExpiration);
    }

    public void setFov(int fov) { this.fov = fov; }
    public void setYRange(int yRange) { this.yRange = yRange; }
    public void setRange(double range) { this.range = range; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setScanPos(Vec3 scanPos) { this.scanPos = scanPos; }
    public void setRunning(boolean running) { this.running = running; }
    public void setTrackExpiration(int trackExpiration) { this.trackExpiration = trackExpiration; }

    public Collection<RadarTrack> getRadarTracks() {
        return radarTracks.values();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public float getAngle() {
        return (float) angle;
    }

    public int getFov() {
        return fov;
    }
}
