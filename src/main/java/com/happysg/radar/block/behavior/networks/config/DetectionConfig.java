package com.happysg.radar.block.behavior.networks.config;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.config.RadarConfig;
import net.createmod.catnip.theme.Color;
import net.minecraft.nbt.CompoundTag;


import java.util.List;

public record DetectionConfig(boolean player, boolean vs2, boolean contraption, boolean mob, boolean projectile, boolean animal, boolean item,
                            List<String> blacklistPlayers, List<String> whitelistPlayers, List<String> blacklistVS2,
                            List<String> whitelistVS) {

    public static final DetectionConfig DEFAULT = new DetectionConfig(true, true, true, true, true,true,true);

    public DetectionConfig(boolean player, boolean vs2, boolean contraption, boolean mob, boolean projectile,boolean animal, boolean item) {
        this(player, vs2, contraption, mob, projectile,animal, item, List.of(), List.of(), List.of(), List.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("player", player);
        tag.putBoolean("vs2", vs2);
        tag.putBoolean("contraption", contraption);
        tag.putBoolean("mob", mob);
        tag.putBoolean("projectile", projectile);
        tag.putBoolean("animal", animal);
        tag.putBoolean("item",item);
        CompoundTag playersListTag = new CompoundTag();
        blacklistPlayers.forEach(player -> playersListTag.putBoolean(player, false));
        whitelistPlayers.forEach(player -> playersListTag.putBoolean(player, true));
        tag.put("playerList", playersListTag);
        CompoundTag vs2ListTag = new CompoundTag();
        blacklistVS2.forEach(vs2 -> vs2ListTag.putBoolean(vs2, false));
        whitelistVS.forEach(vs2 -> vs2ListTag.putBoolean(vs2, true));
        tag.put("vs2Ships", vs2ListTag);
        return tag;
    }

    public static DetectionConfig fromTag(CompoundTag tag) {
        boolean player = tag.getBoolean("player");
        boolean vs2 = tag.getBoolean("vs2");
        boolean contraption = tag.getBoolean("contraption");
        boolean mob = tag.getBoolean("mob");
        boolean projectile = tag.getBoolean("projectile");
        boolean animal = tag.getBoolean("animal");
        boolean item = tag.getBoolean("item");
        List<String> blacklistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> !tag.getCompound("playerList").getBoolean(key)).toList();
        List<String> whitelistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> tag.getCompound("playerList").getBoolean(key)).toList();
        List<String> blacklistVS2 = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> !tag.getCompound("vs2Ships").getBoolean(key)).toList();
        List<String> whitelistVS = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> tag.getCompound("vs2Ships").getBoolean(key)).toList();
        return new DetectionConfig(player, vs2, contraption, mob, projectile, animal, item, blacklistPlayers, whitelistPlayers, blacklistVS2, whitelistVS);
    }

    public boolean test(RadarTrack track) {
        return test(track.trackCategory());
    }

    public Color getColor(RadarTrack track) {
        if (track.trackCategory() == TrackCategory.PLAYER) {
            if (blacklistPlayers.contains(track.id())) {
                return new Color(RadarConfig.client().hostileColor.get());
            }
            if (whitelistPlayers.contains(track.id())) {
                return new Color(RadarConfig.client().friendlyColor.get());
            }
        }
        if (track.trackCategory() == TrackCategory.VS2) {
            if (blacklistVS2.contains(track.id())) {
                return new Color(RadarConfig.client().hostileColor.get());
            }
            if (whitelistVS.contains(track.id())) {
                return new Color(RadarConfig.client().friendlyColor.get());
            }
        }
        return track.getColor();
    }

    private boolean test(TrackCategory trackCategory) {
        if (trackCategory == TrackCategory.PLAYER) {
            return player;
        } else if (Mods.VALKYRIENSKIES.isLoaded() && trackCategory == TrackCategory.VS2) {
            return vs2;
        } else if (trackCategory == TrackCategory.CONTRAPTION) {
            return contraption;
        } else if (trackCategory == TrackCategory.MOB || trackCategory == TrackCategory.HOSTILE) {
            return mob;
        } else if (trackCategory == TrackCategory.PROJECTILE) {
            return projectile;
        } else if (trackCategory == TrackCategory.ANIMAL ){
            return animal;
        }else if (trackCategory == TrackCategory.ITEM){
            return item;
        }
        return false;
    }
}
