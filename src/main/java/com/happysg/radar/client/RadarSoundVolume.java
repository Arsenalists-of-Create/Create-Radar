package com.happysg.radar.client;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.config.RadarConfig;
import net.minecraft.resources.ResourceLocation;

public final class RadarSoundVolume {
    private RadarSoundVolume() {}

    public static float multiplier(ResourceLocation soundId) {
        if (RadarConfig.client() == null || !CreateRadar.MODID.equals(soundId.getNamespace())) {
            return 1.0f;
        }

        var config = RadarConfig.client();

        int individual = switch (soundId.getPath()) {
            case "rwr.lock" -> config.rwrLockVolume.get();
            case "rwr.in_range" -> config.rwrInRangeVolume.get();
            case "rwr.sweep" -> config.rwrSweepVolume.get();
            case "rwr.engaged" -> config.rwrEngagedVolume.get();
            case "sonar.ping" -> config.sonarPingVolume.get();
            case "sonar.hum" -> config.sonarHumVolume.get();
            default -> 100;
        };

        float master = Math.clamp(config.modSoundsVolume.get(), 0, 100) / 100.0f;
        float volume = Math.clamp(individual, 0, 100) / 100.0f;
        return master * volume;
    }
}
