package com.happysg.radar.debug;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record DiagnosticContext(ServerLevel level, ServerPlayer viewer) {
}
