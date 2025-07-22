package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.config.RadarConfig;

import com.simibubi.create.foundation.utility.Color;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public record MonitorFilter(boolean player, boolean vs2, boolean contraption, boolean mob, boolean animal, boolean projectile,boolean item,
                            List<String> blacklistPlayers, List<String> whitelistPlayers, List<String> blacklistVS2,
                            List<String> whitelistVS) {

    public static final MonitorFilter DEFAULT = new MonitorFilter(true, true, true, true, true, true,true);

    public MonitorFilter(boolean player, boolean vs2, boolean contraption, boolean mob, boolean animal, boolean projectile, boolean item) {
        this(player, vs2, contraption, mob, animal, projectile, item, List.of(), List.of(), List.of(), List.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("player", player);
        tag.putBoolean("vs2", vs2);
        tag.putBoolean("contraption", contraption);
        tag.putBoolean("mob", mob);
        tag.putBoolean("projectile", projectile);
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

    public static MonitorFilter fromTag(CompoundTag tag) {
        boolean player = tag.getBoolean("player");
        boolean vs2 = tag.getBoolean("vs2");
        boolean contraption = tag.getBoolean("contraption");
        boolean mob = tag.getBoolean("mob");
        boolean animal =tag.getBoolean("animal");
        boolean projectile = tag.getBoolean("projectile");
        boolean item =tag.getBoolean("item");
        List<String> blacklistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> !tag.getCompound("playerList").getBoolean(key)).toList();
        List<String> whitelistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> tag.getCompound("playerList").getBoolean(key)).toList();
        List<String> blacklistVS2 = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> !tag.getCompound("vs2Ships").getBoolean(key)).toList();
        List<String> whitelistVS = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> tag.getCompound("vs2Ships").getBoolean(key)).toList();
        return new MonitorFilter(player, vs2, contraption, mob, animal, projectile, item, blacklistPlayers, whitelistPlayers, blacklistVS2, whitelistVS);
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
        } else if (trackCategory == TrackCategory.VS2) {
            return vs2;
        } else if (trackCategory == TrackCategory.CONTRAPTION) {
            return contraption;
        } else if (trackCategory == TrackCategory.MOB || trackCategory == TrackCategory.ANIMAL || trackCategory == TrackCategory.HOSTILE) {
            return mob;
        } else if (trackCategory == TrackCategory.PROJECTILE) {
            return projectile;
        }
        return false;
    }
}
