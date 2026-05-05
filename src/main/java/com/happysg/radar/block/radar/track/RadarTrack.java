package com.happysg.radar.block.radar.track;

import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.compat.cbc.CannonLead;
import com.happysg.radar.compat.cbc.VelocityTracker;
import com.happysg.radar.config.RadarConfig;
import net.createmod.catnip.theme.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.Ship;
import dev.ryanhcode.sable.sublevel.SubLevel;

import javax.annotation.Nullable;
import java.util.UUID;


public class RadarTrack {
    private final String id;
    private Vec3 position;
    private Vec3 velocity;
    private long scannedTime;
    private final TrackCategory trackCategory;
    private final String entityType;
    private final float entityheight;
    private final String name;

    private Vec3 vector;

    public RadarTrack(String id, Vec3 position, Vec3 velocity, long scannedTime, TrackCategory trackCategory, String entityType, float entityheight, String name) {
        this.id = id;
        this.position = position;
        this.velocity = velocity;
        this.scannedTime = scannedTime;
        this.trackCategory = trackCategory;
        this.entityType = entityType;
        this.entityheight = entityheight;
        this.name = name;
    }

    public RadarTrack(Entity entity) {
        this(entity.getUUID().toString(), com.happysg.radar.compat.PhysicsHandler.getWorldVec(entity.level(), entity.position()),
                entity.getDeltaMovement().add(com.happysg.radar.compat.PhysicsHandler.getShipVelocity(entity.level(), entity.blockPosition())),
                entity.level().getGameTime(),
                TrackCategory.get(entity), entity.getType().toString(), entity.getBbHeight(), entity.getName().getString());
    }

    public RadarTrack(SubLevel sl, Level level) {
        this(sl.getUniqueId().toString(),
                sl.boundingBox() != null ? sl.boundingBox().toMojang().getCenter() : Vec3.ZERO,
                com.happysg.radar.compat.aeronautics.AeronauticsUtils.getSubLevelVelocity(level, net.minecraft.core.BlockPos.ZERO), // dummy pos for vel
                level.getGameTime(),
                TrackCategory.AERONAUTICS, "Sable:sublevel", 10.0f, com.happysg.radar.compat.aeronautics.SableUtils.getSubLevelNamespace(sl));
    }

    public Color getColor() {
        return switch (trackCategory) {
            case VS2 -> new Color(RadarConfig.client().VS2Color.get());
            case CONTRAPTION -> new Color(RadarConfig.client().contraptionColor.get());
            case PLAYER -> new Color(RadarConfig.client().playerColor.get());
            case ANIMAL -> new Color(RadarConfig.client().friendlyColor.get());
            case HOSTILE -> new Color(RadarConfig.client().hostileColor.get());
            case PROJECTILE -> new Color(RadarConfig.client().projectileColor.get());
            case ITEM-> new Color(RadarConfig.client().itemcolor.get());
            case AERONAUTICS -> new Color(RadarConfig.client().VS2Color.get()); // reuse VS2 color or add new
            default -> Color.WHITE;
        };
    }

    public MonitorSprite getSprite() {
        return switch (trackCategory) {
            case VS2, CONTRAPTION -> MonitorSprite.CONTRAPTION_HITBOX;
            case PLAYER -> MonitorSprite.PLAYER;
            case PROJECTILE -> MonitorSprite.PROJECTILE;
            default -> MonitorSprite.ENTITY_HITBOX;
        };
    }


    public static RadarTrack deserializeNBT(CompoundTag tag) {
        return new RadarTrack(tag.getString("id"),
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                new Vec3(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz")),
                tag.getLong("scannedTime"),
                TrackCategory.values()[tag.getInt("Category")],
                tag.getString("entityType"),
                tag.getFloat("eh"),
                tag.getString("name")
        );
    }


    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        tag.putDouble("vx", velocity.x);
        tag.putDouble("vy", velocity.y);
        tag.putDouble("vz", velocity.z);
        tag.putLong("scannedTime", scannedTime);
        tag.putInt("Category", trackCategory.ordinal());
        tag.putString("entityType", entityType);
        tag.putFloat("eh", entityheight );
        tag.putString("name", name);

        return tag;
    }

    public void updateRadarTrack(Entity entity) {
        position = com.happysg.radar.compat.PhysicsHandler.getWorldVec(entity.level(), entity.position());
        velocity = entity.getDeltaMovement().add(com.happysg.radar.compat.PhysicsHandler.getShipVelocity(entity.level(), entity.blockPosition()));
        scannedTime = entity.level().getGameTime();
    }

    public void updateRadarTrack(Ship ship, net.minecraft.world.level.Level level) {
        position = RadarTrackUtil.getPosition(ship);
        velocity = RadarTrackUtil.getVelocity(ship);
        scannedTime = level.getGameTime();
    }

    public void updateRadarTrack(SubLevel sl, net.minecraft.world.level.Level level) {
        if (sl.boundingBox() != null) position = sl.boundingBox().toMojang().getCenter();
        velocity = com.happysg.radar.compat.aeronautics.AeronauticsUtils.getSubLevelVelocity(level, net.minecraft.core.BlockPos.ZERO);
        scannedTime = level.getGameTime();
        // note: name is final, but if it changed we might need to handle it. 
        // For now, we assume namespace is stable.
    }

    public String getId() {
        return id;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3 velocity) {
        this.velocity = velocity;
    }

    public long getScannedTime() {
        return scannedTime;
    }

    public void setScannedTime(long scannedTime) {
        this.scannedTime = scannedTime;
    }

    public float getEnityHeight(){return entityheight;}

    public TrackCategory getTrackCategory() {
        return trackCategory;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getName() {
        return name;
    }



    // Compatibility methods for legacy record-style access
    public String id() {
        return getId();
    }
    public Vec3 position() {
        return getPosition();
    }
    public Vec3 velocity() {
        return getVelocity();
    }
    public long scannedTime() {
        return getScannedTime();
    }
    public TrackCategory trackCategory() {
        return getTrackCategory();
    }
    public String entityType() {
        return getEntityType();
    }
}