package com.happysg.radar.block.radar.track;

import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.api.tracking.RadarContact;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.theme.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;


public class RadarTrack implements RadarContact {
    public static final int FRIENDLY_RADAR_COLOR = 0x3399ff;

    private final String id;
    private Vec3 position;
    private Vec3 velocity;
    private long scannedTime;
    private final TrackCategory trackCategory;
    private final String entityType;
    private final float entityheight;
    private UUID silhouetteId;
    private int silhouetteRevision = -1;
    private byte silhouetteStatus = 0;
    private boolean friendly;
    private boolean synthetic;
    private JammingData jammingData;

    private Vec3 vector;

    public record JammingData(
            String radarSourceId,
            float outerStrength,
            float directionalStrength,
            float severeStrength,
            float friendlyOutageChance,
            Vec3 guidancePositionOffset,
            Vec3 guidanceVelocityOffset,
            long sampleToken
    ) {
        public JammingData {
            radarSourceId = radarSourceId == null ? "" : radarSourceId;
            guidancePositionOffset = guidancePositionOffset == null
                    ? Vec3.ZERO : guidancePositionOffset;
            guidanceVelocityOffset = guidanceVelocityOffset == null
                    ? Vec3.ZERO : guidanceVelocityOffset;
        }

        public float priorityScore() {
            return severeStrength * 100.0F
                    + directionalStrength * 10.0F + outerStrength;
        }
    }

    public RadarTrack(String id, Vec3 position, Vec3 velocity, long scannedTime, TrackCategory trackCategory, String entityType, float entityheight) {
        this.id = id;
        this.position = position;
        this.velocity = velocity;
        this.scannedTime = scannedTime;
        this.trackCategory = trackCategory;
        this.entityType = entityType;
        this.entityheight = entityheight;

    }

    public RadarTrack(Entity entity) {
        this(entity.getUUID().toString(), entity.position(), entity.getDeltaMovement(), entity.level().getGameTime(),
                TrackCategory.get(entity), entity.getType().toString(), entity.getBbHeight());
    }

    public RadarTrack copy() {
        RadarTrack copy = new RadarTrack(id, position, velocity, scannedTime, trackCategory, entityType, entityheight);
        copy.silhouetteId = silhouetteId;
        copy.silhouetteRevision = silhouetteRevision;
        copy.silhouetteStatus = silhouetteStatus;
        copy.friendly = friendly;
        copy.synthetic = synthetic;
        copy.jammingData = jammingData;
        copy.vector = vector;
        return copy;
    }

    public void copyMutableStateFrom(RadarTrack source) {
        if (source == null) {
            return;
        }
        position = source.position;
        velocity = source.velocity;
        scannedTime = source.scannedTime;
        silhouetteId = source.silhouetteId;
        silhouetteRevision = source.silhouetteRevision;
        silhouetteStatus = source.silhouetteStatus;
        friendly = source.friendly;
        synthetic = source.synthetic;
        jammingData = source.jammingData;
        vector = source.vector;
    }

    public Color getColor() {
        return switch (trackCategory) {
            case SABLE -> friendly ? new Color(FRIENDLY_RADAR_COLOR) : new Color(RadarConfig.client().SableColor.get());
            case CONTRAPTION -> new Color(RadarConfig.client().contraptionColor.get());
            case PLAYER -> new Color(RadarConfig.client().playerColor.get());
            case ANIMAL -> new Color(RadarConfig.client().friendlyColor.get());
            case HOSTILE -> new Color(RadarConfig.client().hostileColor.get());
            case PROJECTILE -> new Color(RadarConfig.client().projectileColor.get());
            case MISSILE -> new Color(0x8000ff);
            case ITEM-> new Color(RadarConfig.client().itemcolor.get());
            default -> Color.WHITE;
        };
    }

    public MonitorSprite getSprite() {
        return switch (trackCategory) {
            case SABLE, CONTRAPTION -> MonitorSprite.CONTRAPTION_HITBOX;
            case PLAYER -> MonitorSprite.PLAYER;
            case PROJECTILE, MISSILE -> MonitorSprite.PROJECTILE;
            default -> MonitorSprite.ENTITY_HITBOX;
        };
    }


    public static RadarTrack deserializeNBT(CompoundTag tag) {
        RadarTrack track = new RadarTrack(tag.getString("id"),
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                new Vec3(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz")),
                tag.getLong("scannedTime"),
                TrackCategory.values()[tag.getInt("Category")],
                tag.getString("entityType"),
                tag.getFloat("eh")

        );
        track.friendly = tag.getBoolean("Friendly");
        track.synthetic = tag.getBoolean("Synthetic");
        if (tag.contains("Jamming", Tag.TAG_COMPOUND)) {
            CompoundTag jamming = tag.getCompound("Jamming");
            track.jammingData = new JammingData(
                    jamming.getString("RadarSource"),
                    jamming.getFloat("OuterStrength"),
                    jamming.getFloat("DirectionalStrength"),
                    jamming.getFloat("SevereStrength"),
                    jamming.getFloat("FriendlyOutageChance"),
                    readVec3(jamming, "GuidancePositionOffset"),
                    readVec3(jamming, "GuidanceVelocityOffset"),
                    jamming.getLong("SampleToken")
            );
        }
        if (tag.contains("SilhouetteId", Tag.TAG_STRING)) {
            try {
                track.silhouetteId = UUID.fromString(tag.getString("SilhouetteId"));
                track.silhouetteRevision = tag.getInt("SilhouetteRevision");
                track.silhouetteStatus = tag.getByte("SilhouetteStatus");
            } catch (IllegalArgumentException ignored) {
                track.clearSilhouette();
            }
        }
        return track;
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
        tag.putBoolean("Friendly", friendly);
        tag.putBoolean("Synthetic", synthetic);
        if (jammingData != null) {
            CompoundTag jamming = new CompoundTag();
            jamming.putString("RadarSource", jammingData.radarSourceId());
            jamming.putFloat("OuterStrength", jammingData.outerStrength());
            jamming.putFloat("DirectionalStrength",
                    jammingData.directionalStrength());
            jamming.putFloat("SevereStrength", jammingData.severeStrength());
            jamming.putFloat("FriendlyOutageChance",
                    jammingData.friendlyOutageChance());
            writeVec3(jamming, "GuidancePositionOffset",
                    jammingData.guidancePositionOffset());
            writeVec3(jamming, "GuidanceVelocityOffset",
                    jammingData.guidanceVelocityOffset());
            jamming.putLong("SampleToken", jammingData.sampleToken());
            tag.put("Jamming", jamming);
        }
        if (silhouetteId != null) {
            tag.putString("SilhouetteId", silhouetteId.toString());
            tag.putInt("SilhouetteRevision", silhouetteRevision);
            tag.putByte("SilhouetteStatus", silhouetteStatus);
        }

        return tag;
    }

    public void updateRadarTrack(Entity entity) {
        position = entity.position();
        velocity = entity.getDeltaMovement();
        scannedTime = entity.level().getGameTime();
    }

    public void updateRadarTrack(SubLevelAccess ship, Level level) {
        position = RadarTrackUtil.getPosition(ship);
        velocity = RadarTrackUtil.getVelocity(ship, level);
        scannedTime = level.getGameTime();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    @Override
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

    public UUID getSilhouetteId() {
        return silhouetteId;
    }

    public int getSilhouetteRevision() {
        return silhouetteRevision;
    }

    public byte getSilhouetteStatus() {
        return silhouetteStatus;
    }

    public boolean isFriendly() {
        return friendly;
    }

    public void setFriendly(boolean friendly) {
        this.friendly = friendly;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public JammingData getJammingData() {
        return jammingData;
    }

    public void setJammingData(JammingData jammingData) {
        this.jammingData = jammingData;
    }

    public void setSilhouette(UUID silhouetteId, int silhouetteRevision, byte silhouetteStatus) {
        this.silhouetteId = silhouetteId;
        this.silhouetteRevision = silhouetteRevision;
        this.silhouetteStatus = silhouetteStatus;
    }

    public void clearSilhouette() {
        this.silhouetteId = null;
        this.silhouetteRevision = -1;
        this.silhouetteStatus = 0;
    }



    // This is a bit of a jank quick fix, since ive migrated from a record.
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
    public boolean friendly() {
        return isFriendly();
    }

    private static void writeVec3(CompoundTag tag, String key, Vec3 value) {
        CompoundTag vector = new CompoundTag();
        vector.putDouble("X", value.x);
        vector.putDouble("Y", value.y);
        vector.putDouble("Z", value.z);
        tag.put(key, vector);
    }

    private static Vec3 readVec3(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Vec3.ZERO;
        }
        CompoundTag vector = tag.getCompound(key);
        Vec3 value = new Vec3(vector.getDouble("X"), vector.getDouble("Y"),
                vector.getDouble("Z"));
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) ? value : Vec3.ZERO;
    }
}
