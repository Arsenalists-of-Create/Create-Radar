package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SubLevelAccess;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SkyRadarScanningBehavior extends BlockEntityBehaviour {

    public static final BehaviourType<SkyRadarScanningBehavior> TYPE = new BehaviourType<>();
    private static final double SKY_SCAN_MAX_Y = 300000.0;

    private int trackExpiration = 100;
    private int fov = RadarConfig.server().skyRadarFOV.get();
    private double range;
    private double angle;
    private boolean running = false;
    private final SmartBlockEntity radarEntity;
    private Vec3 scanPos = Vec3.ZERO;

    private final Set<Entity> scannedEntities = new HashSet<>();
    private final Set<SubLevelAccess> scannedShips = new HashSet<>();
    private final Set<Projectile> scannedProjectiles = new HashSet<>();
    private final HashMap<String, RadarTrack> radarTracks = new HashMap<>();

    private boolean scanPlayers = true;
    private boolean scanSable = true;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;

    public SkyRadarScanningBehavior(SmartBlockEntity be) {
        super(be);
        this.radarEntity = be;
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

    private boolean allowCategory(TrackCategory c) {
        return switch (c) {
            case PLAYER -> scanPlayers;
            case SABLE -> scanSable;
            case CONTRAPTION -> scanContraptions;
            case PROJECTILE -> scanProjectiles;
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
        if (blockEntity.getLevel().getGameTime() % 5 != 1) return;

        scanPos = PhysicsHandler.getWorldVec(radarEntity);
        removeDeadTracks();

        if (!running || !isHighEnough()) {
            scannedEntities.clear();
            scannedShips.clear();
            scannedProjectiles.clear();
            radarTracks.clear();
            return;
        }

        scannedEntities.clear();
        scannedShips.clear();
        scannedProjectiles.clear();

        scanForEntityTracks();
        if (Mods.SABLE.isLoaded() && scanSable)
            scanForSableTracks();

        updateRadarTracks();
    }

    private boolean isHighEnough() {
        return scanPos.y >= getMinimumOperatingY();
    }

    public static int getMinimumOperatingY() {
        return RadarConfig.server().skyRadarMinY.get();
    }

    private void updateRadarTracks() {
        Level level = blockEntity.getLevel();
        if (level == null) return;
        ServerLevel sl = level instanceof ServerLevel serverLevel ? serverLevel : null;

        for (Entity entity : scannedEntities) {
            if (entity.isAlive() && isInFovAndRange(entity.position())) {
                radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(entity);
                    track.updateRadarTrack(entity);
                    return track;
                });

                if (entity instanceof Projectile projectile)
                    scannedProjectiles.add(projectile);
            }
        }

        for (SubLevelAccess ship : scannedShips) {
            Vec3 pos = RadarTrackUtil.getPosition(ship);
            if (isInFovAndRange(pos)) {
                radarTracks.compute(ship.getUniqueId().toString(), (id, track) -> {
                    if (track == null) return RadarTrackUtil.getRadarTrack(ship, level);
                    track.updateRadarTrack(ship, level);
                    return track;
                });
                if (sl != null) {
                    RadarContactRegistry.markInRange(sl, ship.getUniqueId(), 20);
                }
            }
        }
    }

    private boolean isInFovAndRange(Vec3 target) {
        if (target.y() < scanPos.y())
            return false;

        double horizontalDistance = Math.sqrt(Math.pow(target.x() - scanPos.x(), 2) + Math.pow(target.z() - scanPos.z(), 2));
        if (horizontalDistance > range)
            return false;

        if (horizontalDistance < 2 || fov >= 360)
            return true;

        double angleToEntity = Math.toDegrees(Math.atan2(target.x() - scanPos.x(), target.z() - scanPos.z()));
        angleToEntity = (angleToEntity + 360) % 360;
        double angleDiff = Math.abs(angleToEntity - angle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        return angleDiff <= fov / 2.0;
    }

    private void removeDeadTracks() {
        for (Entity entity : scannedEntities) {
            if (!entity.isAlive())
                radarTracks.remove(entity.getUUID().toString());
        }

        List<String> toRemove = new ArrayList<>();
        assert blockEntity.getLevel() != null;
        long currentTime = blockEntity.getLevel().getGameTime();
        for (RadarTrack track : radarTracks.values()) {
            if (currentTime - track.scannedTime() > trackExpiration)
                toRemove.add(track.id());
        }
        toRemove.forEach(radarTracks::remove);

        scannedProjectiles.removeIf(p -> {
            boolean dead = !p.isAlive();
            if (dead) radarTracks.remove(p.getUUID().toString());
            return dead;
        });
    }

    private void scanForEntityTracks() {
        Level level = blockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (Entity entity : serverLevel.getAllEntities()) {
            if (!isInSkyRadarVolume(entity.position()))
                continue;

            if (entity instanceof Player) {
                if (scanPlayers && shouldRadarSee(entity)) scannedEntities.add(entity);
            } else if (entity instanceof Projectile) {
                if (scanProjectiles) scannedEntities.add(entity);
            } else if (entity instanceof ItemEntity) {
                if (scanItems) scannedEntities.add(entity);
            } else if (entity instanceof AbstractContraptionEntity) {
                if (scanContraptions) scannedEntities.add(entity);
            } else if (entity instanceof Animal) {
                if (scanAnimals) scannedEntities.add(entity);
            } else if (entity instanceof Mob) {
                if (scanMobs) scannedEntities.add(entity);
            }
        }
    }

    private boolean isInSkyRadarVolume(Vec3 target) {
        if (target.y() < scanPos.y())
            return false;
        double dx = target.x() - scanPos.x();
        double dz = target.z() - scanPos.z();
        return dx * dx + dz * dz <= range * range;
    }

    private boolean shouldRadarSee(Entity entity) {
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (Mods.VMOD.isLoaded()) {
            return !VanishUtil.isVanished(serverPlayer);
        }
        return true;
    }

    private void scanForSableTracks() {
        if (blockEntity.getLevel() == null || !Mods.SABLE.isLoaded()) return;
        RadarScanningBlockBehavior.splitAABB(getRadarAABB(), 1024).forEach(aabb ->
                SableUtils.getLoadedShips(blockEntity.getLevel(), aabb).forEach(scannedShips::add));

        scannedShips.remove(SableUtils.getShipManagingPos(blockEntity));
    }

    private AABB getRadarAABB() {
        return new AABB(
                scanPos.x - range, scanPos.y, scanPos.z - range,
                scanPos.x + range, Math.max(scanPos.y + 1, SKY_SCAN_MAX_Y), scanPos.z + range
        );
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if (nbt.contains("fov")) fov = nbt.getInt("fov");
        if (nbt.contains("range")) range = nbt.getDouble("range");
        if (nbt.contains("angle")) angle = nbt.getDouble("angle");
        if (nbt.contains("scanPosX")) scanPos = new Vec3(nbt.getDouble("scanPosX"), nbt.getDouble("scanPosY"), nbt.getDouble("scanPosZ"));
        if (nbt.contains("running")) running = nbt.getBoolean("running");
        if (nbt.contains("trackExpiration")) trackExpiration = nbt.getInt("trackExpiration");
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("fov", fov);
        nbt.putDouble("range", range);
        nbt.putDouble("angle", angle);
        nbt.putDouble("scanPosX", scanPos.x);
        nbt.putDouble("scanPosY", scanPos.y);
        nbt.putDouble("scanPosZ", scanPos.z);
        nbt.putBoolean("running", running);
        nbt.putInt("trackExpiration", trackExpiration);
    }

    public void setFov(int fov) { this.fov = fov; }
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
}
