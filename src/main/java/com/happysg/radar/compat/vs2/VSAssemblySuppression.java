package com.happysg.radar.compat.vs2;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class VSAssemblySuppression {
    // i use WeakHashMap so worlds can unload without leaking
    private static final Map<ServerLevel, Integer> DEPTH = new WeakHashMap<>();

    private VSAssemblySuppression() {}

    public static void begin(net.minecraft.server.level.ServerLevel level) {
        DEPTH.put(level, DEPTH.getOrDefault(level, 0) + 1);
    }

    public static void end(net.minecraft.server.level.ServerLevel level) {
        int d = DEPTH.getOrDefault(level, 0) - 1;
        if (d <= 0) DEPTH.remove(level);
        else DEPTH.put(level, d);
    }

    public static boolean isSuppressed(net.minecraft.server.level.ServerLevel level) {
        return DEPTH.containsKey(level);
    }
}