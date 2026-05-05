package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.PhysicsHandler;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.Ship;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.lang.reflect.Method;
import java.util.*;

public class RadarScanningBlockBehavior extends BlockEntityBehaviour {

    public static final BehaviourType<RadarScanningBlockBehavior> TYPE = new BehaviourType<>();

    private int trackExpiration = 100;
    private int fov = RadarConfig.server().radarFOV.get();
    private int yRange = 20;
    private double range = RadarConfig.server().radarBaseRange.get();
    private double angle;
    private boolean running = false;
    private SmartBlockEntity bearingEntity;
    private RadarBearingBlockEntity radarBearing;
    Vec3 scanPos = Vec3.ZERO;

    private final Set<Entity> scannedEntities = new HashSet<>();
    private final Set<Ship> scannedShips = new HashSet<>();
    private final Set<Object> scannedSubLevels = new HashSet<>();
    private final Set<Projectile> scannedProjectiles = new HashSet<>();
    private final HashMap<String, RadarTrack> radarTracks = new HashMap<>();

    public RadarScanningBlockBehavior(SmartBlockEntity be) {
        super(be);
        this.bearingEntity = be;
    }

    public void applyDetectionConfig(DetectionConfig cfg) {
        if (cfg == null) cfg = DetectionConfig.DEFAULT;
        setScanFlags(
                cfg.player(),
                cfg.physicsContraptions(),
                cfg.contraption(),
                cfg.mob(),
                cfg.animal(),
                cfg.projectile(),
                cfg.item(),
                cfg.physicsContraptions()
        );
    }


    private boolean scanPlayers = true;
    private boolean scanVS2 = false;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;
    private boolean scanAeronautics = true;

    private boolean allowCategory(TrackCategory c) {
        return switch (c) {
            case PLAYER -> scanPlayers;
            case VS2 -> scanVS2;
            case CONTRAPTION -> scanContraptions;
            case PROJECTILE -> scanProjectiles;
            case ITEM -> scanItems;
            case AERONAUTICS -> scanAeronautics;
            case ANIMAL -> scanAnimals;
            case HOSTILE, MOB -> scanMobs;

            default -> true;
        };
    }

    private void pruneDisabledTracksNow() {
        radarTracks.entrySet().removeIf(e -> !allowCategory(e.getValue().trackCategory()));
    }

    public void setScanFlags(boolean players, boolean vs2, boolean contraptions, boolean mobs, boolean animals, boolean projectiles, boolean items, boolean aeronautics) {
        boolean changed = players != scanPlayers || vs2 != scanVS2 || contraptions != scanContraptions || mobs != scanMobs 
                || animals != scanAnimals || projectiles != scanProjectiles || items != scanItems || aeronautics != scanAeronautics;

        this.scanPlayers = players;
        this.scanVS2 = vs2;
        this.scanContraptions = contraptions;
        this.scanMobs = mobs;
        this.scanAnimals = animals;
        this.scanProjectiles = projectiles;
        this.scanItems = items;
        this.scanAeronautics = aeronautics;

        if (changed) {
            pruneDisabledTracksNow();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide)
            return;

        removeDeadTracks();


        if (running) {
            scanPos = PhysicsHandler.getScanningVec(blockEntity);
            scannedEntities.clear();
            scannedShips.clear();
            scannedSubLevels.clear();
            scannedProjectiles.clear();

            Level worldLevel = PhysicsHandler.getWorldLevel(blockEntity);
            Level localLevel = blockEntity.getLevel();

            // Scan parent world
            if (worldLevel != null) {
                scanForEntityTracks(worldLevel, scanPos);
                if (Mods.VALKYRIENSKIES.isLoaded() && scanVS2)
                    scanForVSTracks(worldLevel, scanPos);
                if (Mods.SABLE.isLoaded() && scanAeronautics)
                    scanForSableTracks(worldLevel, scanPos);
            }

            // Scan local sub-level if on a ship
            if (localLevel != null && localLevel != worldLevel) {
                Vec3 localPos = Vec3.atCenterOf(blockEntity.getBlockPos());
                scanForEntityTracks(localLevel, localPos);
                // Usually VS2 doesn't nest ships in sub-levels, but we could scan if needed
            }

            updateRadarTracks();

            // Point 3: Drop target if it leaves FOV or Range
            // Point 1: Handle destroyed structure (it won't be in scan result but would be in FOV)
            long now = level.getGameTime();
            radarTracks.entrySet().removeIf(entry -> {
                RadarTrack track = entry.getValue();
                if (track.scannedTime() == now) return false;

                // If truly out of range, drop immediately
                if (track.position().distanceTo(scanPos) > range) return true;

                // If in beam but not updated, it's gone
                return isInFovAndRange(track.position());
            });
        }
    }


    private void updateRadarTracks() {
        net.minecraft.world.level.Level level = PhysicsHandler.getWorldLevel(blockEntity);
        if (level == null )return;
        boolean isServer = level instanceof net.minecraft.server.level.ServerLevel;
        net.minecraft.server.level.ServerLevel sl = isServer ? (net.minecraft.server.level.ServerLevel) level : null;


        for (Entity entity : scannedEntities) {
            Vec3 worldPos = PhysicsHandler.getWorldVec(entity.level(), entity.position());
            Vec3 testPos = worldPos;
            
            if (entity.getBbWidth() > 1 || entity.getBbHeight() > 1) {
               // Approximate closest point to beam in world space
               AABB bb = entity.getBoundingBox(); // This might be local BB if on ship
               // We need world-space BB for distance check
               Vec3 min = PhysicsHandler.getWorldVec(entity.level(), new Vec3(bb.minX, bb.minY, bb.minZ));
               Vec3 max = PhysicsHandler.getWorldVec(entity.level(), new Vec3(bb.maxX, bb.maxY, bb.maxZ));
               AABB worldBB = new AABB(min, max);

               testPos = new Vec3(
                   Math.max(worldBB.minX, Math.min(scanPos.x, worldBB.maxX)),
                   Math.max(worldBB.minY, Math.min(scanPos.y, worldBB.maxY)),
                   Math.max(worldBB.minZ, Math.min(scanPos.z, worldBB.maxZ))
               );
            }

            if (entity.isAlive() && isInFovAndRange(testPos)) {
                if (blockEntity.getLevel() != null && blockEntity.getLevel().getGameTime() % 60 == 0) {
                     com.happysg.radar.CreateRadar.getLogger().info("Radar {} found entity {} at {}", blockEntity.getBlockPos(), entity.getName().getString(), testPos);
                }
                radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(entity);
                    track.updateRadarTrack(entity);
                    return track;
                });

                if (entity instanceof Projectile)
                    scannedProjectiles.add((Projectile) entity);
            }
        }

        for (Ship ship : scannedShips) {
            AABB bb = VS2Utils.getShipAABB(ship);
            Vec3 testPos = bb.getCenter();
            if (bb.getXsize() > 4 || bb.getZsize() > 4) {
                testPos = new Vec3(
                    Math.max(bb.minX, Math.min(scanPos.x, bb.maxX)),
                    Math.max(bb.minY, Math.min(scanPos.y, bb.maxY)),
                    Math.max(bb.minZ, Math.min(scanPos.z, bb.maxZ))
                );
            }

            if (isInFovAndRange(testPos)) {

                long key = ship.getId();

                radarTracks.compute(ship.getSlug(), (id, track) -> {
                    if (track == null) return RadarTrackUtil.getRadarTrack(ship, level);
                    track.updateRadarTrack(ship, level);
                    return track;
                });
                if (isServer) {
                    RadarContactRegistry.markInRange(sl, key, 20);
                }
            }
        }

        for (Object obj : scannedSubLevels) {
            if (!(obj instanceof SubLevel subLevel)) continue;
            net.minecraft.world.phys.AABB bb = subLevel.boundingBox().toMojang();
            if (bb == null) continue;

            Vec3 testPos = bb.getCenter();
            if (bb.getXsize() > 4 || bb.getZsize() > 4) {
                testPos = new Vec3(
                        Math.max(bb.minX, Math.min(scanPos.x, bb.maxX)),
                        Math.max(bb.minY, Math.min(scanPos.y, bb.maxY)),
                        Math.max(bb.minZ, Math.min(scanPos.z, bb.maxZ))
                );
            }

            if (isInFovAndRange(testPos)) {
                radarTracks.compute(subLevel.getUniqueId().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(subLevel, level);
                    track.updateRadarTrack(subLevel, level);
                    return track;
                });
            }
        }

    }

    private boolean isInFovAndRange(Vec3 target) {
        double horizontalDistance = Math.sqrt(Math.pow(target.x() - scanPos.x(), 2) + Math.pow(target.z() - scanPos.z(), 2));
        double verticalDistance = Math.abs(target.y() - scanPos.y());
        double yScanRange = RadarConfig.server().radarYScanRange.get();

        if (horizontalDistance > range || verticalDistance > yScanRange)
            return false;

        if (horizontalDistance < 2)
            return true;

        double angleToEntity = Math.toDegrees(Math.atan2(target.x() - scanPos.x(), target.z() - scanPos.z()));
        angleToEntity = (angleToEntity + 360) % 360;

        // Account for ship rotation
        double shipYaw = PhysicsHandler.getShipYawDeg(blockEntity);
        double actualRadarAngle = (angle + 180 + shipYaw) % 360;
        actualRadarAngle = (actualRadarAngle + 360) % 360;

        double angleDiff = Math.abs(angleToEntity - actualRadarAngle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        return angleDiff <= fov / 2.0;
    }

    private void removeDeadTracks() {
        // entities
        for (Entity entity : scannedEntities) {
            if (!entity.isAlive())
                radarTracks.remove(entity.getUUID().toString());
        }

        // vs2 ships
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            assert blockEntity.getLevel() != null;

            var shipWorld = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(blockEntity.getLevel());
            // i remove ship tracks if the ship id no longer resolves (unloaded/despawned)
            scannedShips.removeIf(ship -> {
                boolean dead = shipWorld == null || shipWorld.getLoadedShips().getById(ship.getId()) == null;
                if (dead) radarTracks.remove(String.valueOf(ship.getId()));
                return dead;
            });
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
    private void scanForEntityTracks(Level level, Vec3 center) {
        if (level == null) return;

        boolean scanAll =
                scanPlayers && scanContraptions && scanMobs && scanAnimals && scanProjectiles && scanItems;

        for (AABB aabb : splitAABB(getRadarAABB(level, center), 256)) {
            if (scanAll) {
                scannedEntities.addAll(level.getEntities(null, aabb));
                continue;
            }

            if (scanPlayers)
                scannedEntities.addAll(level.getEntitiesOfClass(Player.class, aabb));

            if (scanProjectiles)
                scannedEntities.addAll(level.getEntitiesOfClass(Projectile.class, aabb));

            if (scanItems)
                scannedEntities.addAll(level.getEntitiesOfClass(ItemEntity.class, aabb));

            if (scanContraptions) {
                for (AbstractContraptionEntity ce : level.getEntitiesOfClass(AbstractContraptionEntity.class, aabb)) {
                    if (bearingEntity instanceof RadarBearingBlockEntity bearing && bearing.isAttachedTo(ce)) continue;
                    scannedEntities.add(ce);
                }
            }

            if (scanAnimals)
                scannedEntities.addAll(level.getEntitiesOfClass(Animal.class, aabb));

            if (scanMobs) {
                scannedEntities.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, aabb,
                        e -> !(e instanceof Animal)));
            }
        }
    }

    private void scanForVSTracks(Level level, Vec3 center) {
        if (level == null || !Mods.VALKYRIENSKIES.isLoaded()) return;

        splitAABB(getRadarAABB(level, center), 256).forEach(aabb ->
                VS2Utils.getLoadedShips(level, aabb).forEach(scannedShips::add));

        scannedShips.remove(VS2Utils.getShipManagingPos(blockEntity));
    }

    private void scanForSableTracks(Level level, Vec3 center) {
        if (level == null || !Mods.SABLE.isLoaded()) return;
        net.minecraft.world.phys.AABB aabb = getRadarAABB(level, center);
        for (SubLevel subLevel : com.happysg.radar.compat.aeronautics.SableUtils.getLoadedSubLevels(level)) {
            net.minecraft.world.phys.AABB slAABB = subLevel.boundingBox().toMojang();
            if (slAABB != null && slAABB.intersects(aabb)) {
                scannedSubLevels.add(subLevel);
            }
        }
    }

    private AABB getRadarAABB(Level level, Vec3 center) {
        if (blockEntity.getLevel() != null && blockEntity.getLevel().getGameTime() % 60 == 0) {
            com.happysg.radar.CreateRadar.getLogger().info("Radar {} AABB Center: {}, Level used for scan: {}", blockEntity.getBlockPos(), center, level != null ? level.dimension().location() : "null");
        }
        double x = center.x();
        double y = center.y();
        double z = center.z();

        double yScan = RadarConfig.server().radarYScanRange.get();
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
    public void read(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        if (nbt.contains("fov")) fov = nbt.getInt("fov");
        if (nbt.contains("yRange")) yRange = nbt.getInt("yRange");
        if (nbt.contains("range")) range = nbt.getDouble("range");
        if (nbt.contains("angle")) angle = nbt.getDouble("angle");
        if (nbt.contains("scanPosX")) scanPos = new Vec3(nbt.getDouble("scanPosX"), nbt.getDouble("scanPosY"), nbt.getDouble("scanPosZ"));
        if (nbt.contains("trackExpiration")) trackExpiration = nbt.getInt("trackExpiration");
        if (nbt.contains("scanPlayers")) scanPlayers = nbt.getBoolean("scanPlayers");
        if (nbt.contains("scanVS2")) scanVS2 = nbt.getBoolean("scanVS2");
        if (nbt.contains("scanContraptions")) scanContraptions = nbt.getBoolean("scanContraptions");
        if (nbt.contains("scanMobs")) scanMobs = nbt.getBoolean("scanMobs");
        if (nbt.contains("scanAnimals")) scanAnimals = nbt.getBoolean("scanAnimals");
        if (nbt.contains("scanProjectiles")) scanProjectiles = nbt.getBoolean("scanProjectiles");
        if (nbt.contains("scanItems")) scanItems = nbt.getBoolean("scanItems");
        if (nbt.contains("scanAeronautics")) scanAeronautics = nbt.getBoolean("scanAeronautics");
    }

    @Override
    public void write(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        nbt.putInt("fov", fov);
        nbt.putInt("yRange", yRange);
        nbt.putDouble("range", range);
        nbt.putDouble("angle", angle);
        nbt.putDouble("scanPosX", scanPos.x);
        nbt.putDouble("scanPosY", scanPos.y);
        nbt.putDouble("scanPosZ", scanPos.z);
        nbt.putBoolean("running", running);
        nbt.putInt("trackExpiration", trackExpiration);
        nbt.putBoolean("scanPlayers", scanPlayers);
        nbt.putBoolean("scanVS2", scanVS2);
        nbt.putBoolean("scanContraptions", scanContraptions);
        nbt.putBoolean("scanMobs", scanMobs);
        nbt.putBoolean("scanAnimals", scanAnimals);
        nbt.putBoolean("scanProjectiles", scanProjectiles);
        nbt.putBoolean("scanItems", scanItems);
        nbt.putBoolean("scanAeronautics", scanAeronautics);
    }

    public void setFov(int fov) { this.fov = fov; }
    public void setYRange(int yRange) { this.yRange = yRange; }
    public void setRange(double range) { this.range = range; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setScanPos(Vec3 scanPos) { this.scanPos = scanPos; }
    public void setRunning(boolean running) { this.running = running; }
    public void setTrackExpiration(int trackExpiration) { this.trackExpiration = trackExpiration; }

    public double getRange() { return range; }

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
}