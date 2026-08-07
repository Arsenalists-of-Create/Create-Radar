package com.happysg.radar.debug;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Extension point for bounded, read-only global report sections. */
public interface DiagnosticContributor {
    String id();

    JsonObject collect(MinecraftServer server);
}
