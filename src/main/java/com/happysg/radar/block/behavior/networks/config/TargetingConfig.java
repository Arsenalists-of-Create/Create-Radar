package com.happysg.radar.block.behavior.networks.config;

import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import net.minecraft.nbt.CompoundTag;

public record TargetingConfig(boolean player, boolean contraption, boolean mob, boolean animal, boolean projectile,
                              boolean autoTarget, boolean autoFire, boolean lineOfSight) {

    public static final TargetingConfig DEFAULT = new TargetingConfig(false, false, true, true, false, false, true, false);

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("player", player);
        tag.putBoolean("contraption", contraption);
        tag.putBoolean("mob", mob);
        tag.putBoolean("animal", animal);
        tag.putBoolean("projectile", projectile);
        tag.putBoolean("autoTarget", autoTarget);
        tag.putBoolean("autoFire", autoFire);
        tag.putBoolean("lineSight",lineOfSight);
        return tag;
    }

    public static TargetingConfig fromTag(CompoundTag tag) {
        if (tag == null) return DEFAULT;

        CompoundTag targeting = null;

        if (tag.contains("targeting", CompoundTag.TAG_COMPOUND)) {
            targeting = tag.getCompound("targeting");
        } else if (tag.contains("Filters", CompoundTag.TAG_COMPOUND)) {
            CompoundTag filters = tag.getCompound("Filters");
            if (filters.contains("targeting", CompoundTag.TAG_COMPOUND))
                targeting = filters.getCompound("targeting");
        }

        if (targeting == null) return DEFAULT;

        return new TargetingConfig(
                targeting.getBoolean("player"),
                targeting.getBoolean("contraption"),
                targeting.getBoolean("mob"),
                targeting.getBoolean("animal"),
                targeting.getBoolean("projectile"),
                targeting.getBoolean("autoTarget"),
                targeting.getBoolean("autoFire"),
                targeting.getBoolean("lineSight")
        );
    }

    public boolean test(TrackCategory trackCategory) {
        if(Mods.VALKYRIENSKIES.isLoaded()){
            return switch (trackCategory) {
                case PLAYER -> player;
                case VS2 -> contraption;
                case CONTRAPTION -> false;
                case HOSTILE -> mob;
                case ANIMAL -> animal;
                case PROJECTILE -> projectile;
                default -> false;
            };
        }else {
            return switch (trackCategory) {
                case PLAYER -> player;
                case CONTRAPTION -> contraption;
                case HOSTILE -> mob;
                case ANIMAL -> animal;
                case PROJECTILE -> projectile;
                default -> false;
            };
        }
    }
}
