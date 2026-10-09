package com.happysg.radar.api.radar.rwr;

import com.happysg.radar.api.arad.ARADTargeting;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class RadarRwrApi {
    private RadarRwrApi() {}

    public static void heartbeat(ServerLevel level, RadarRwrEmitter emitter) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(emitter, "emitter");

        ARADTargeting.heartbeatRwrEmitter(level, emitter);
    }
}