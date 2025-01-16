package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.bearing.RadarTrack;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public record MonitorFilter(boolean player, boolean vs2, boolean contraption, boolean mob, boolean projectile,
                            List<String> blacklistPlayers, List<String> whitelistPlayers, List<String> blacklistVS2,
                            List<String> whitelistVS) {
    public static final MonitorFilter DEFAULT = new MonitorFilter(true, true, true, true, true);

    public MonitorFilter(boolean player, boolean vs2, boolean contraption, boolean mob, boolean projectile) {
        this(player, vs2, contraption, mob, projectile, List.of(), List.of(), List.of(), List.of());
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
        boolean projectile = tag.getBoolean("projectile");
        List<String> blacklistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> !tag.getCompound("players").getBoolean(key)).toList();
        List<String> whitelistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> tag.getCompound("players").getBoolean(key)).toList();
        List<String> blacklistVS2 = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> !tag.getCompound("vs2").getBoolean(key)).toList();
        List<String> whitelistVS = tag.getCompound("vs2Ships").getAllKeys().stream().filter(key -> tag.getCompound("vs2").getBoolean(key)).toList();
        return new MonitorFilter(player, vs2, contraption, mob, projectile, blacklistPlayers, whitelistPlayers, blacklistVS2, whitelistVS);
    }

    public boolean test(RadarTrack.EntityType entityType) {
        if (entityType == RadarTrack.EntityType.PLAYER) {
            return player;
        } else if (entityType == RadarTrack.EntityType.VS2) {
            return vs2;
        } else if (entityType == RadarTrack.EntityType.CONTRAPTION) {
            return contraption;
        } else if (entityType == RadarTrack.EntityType.MOB || entityType == RadarTrack.EntityType.ANIMAL) {
            return mob;
        } else if (entityType == RadarTrack.EntityType.PROJECTILE) {
            return projectile;
        }
        return false;
    }
}
