package com.happysg.radar.compat.vs2;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import org.valkyrienskies.mod.common.assembly.VSAssemblyEvents;

import java.util.Map;
import java.util.WeakHashMap;

public final class VSAssemblySuppression {
    // i use WeakHashMap so worlds can unload without leaking
    private static final Map<ServerLevel, Integer> DEPTH = new WeakHashMap<>();

    private VSAssemblySuppression() {}

    public static void begin(ServerLevel level) {
        DEPTH.put(level, DEPTH.getOrDefault(level, 0) + 1);
    }

    public static void end(ServerLevel level) {
        int d = DEPTH.getOrDefault(level, 0) - 1;
        if (d <= 0) DEPTH.remove(level);
        else DEPTH.put(level, d);
    }

    public static boolean isSuppressed(ServerLevel level) {
        return DEPTH.containsKey(level);
    }
}
