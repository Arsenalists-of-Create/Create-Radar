package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import com.happysg.radar.networking.packets.ConflictTraceStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class ConflictTraceSessionManager {
    private static final Set<UUID> TRACED_CLIENTS =
            ConcurrentHashMap.newKeySet();

    private ConflictTraceSessionManager() {
    }

    public static ConflictTraceRecorder.State enable(MinecraftServer server,
                                                     ServerPlayer player) {
        ConflictTraceRecorder.State state = ConflictTraceRecorder.setEnabled(
                true, player == null ? null : player.level());
        if (player != null) {
            TRACED_CLIENTS.add(player.getUUID());
            ConflictTraceStatePacket.send(player, true, state.sessionId());
        }
        return state;
    }

    public static ConflictTraceRecorder.State disable(MinecraftServer server) {
        ConflictTraceRecorder.State state = ConflictTraceRecorder.setEnabled(
                false, null);
        for (UUID playerId : Set.copyOf(TRACED_CLIENTS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                ConflictTraceStatePacket.send(player, false,
                        state.sessionId());
            }
        }
        TRACED_CLIENTS.clear();
        return state;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACED_CLIENTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        EarlyDiagnosticJournal.record("SERVER", "STARTED", Map.of());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        disable(event.getServer());
        EarlyDiagnosticJournal.gracefulShutdown("server");
    }
}
