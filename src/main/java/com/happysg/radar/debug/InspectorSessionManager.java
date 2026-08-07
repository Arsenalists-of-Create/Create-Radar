package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.InspectorStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class InspectorSessionManager {
    public static final long REQUEST_COOLDOWN_TICKS = 5L;
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Long> LAST_REQUEST =
            new ConcurrentHashMap<>();

    private InspectorSessionManager() {
    }

    public static boolean toggle(ServerPlayer player) {
        return setEnabled(player, !isEnabled(player));
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        if (!player.hasPermissions(2)) {
            enabled = false;
        }
        if (enabled) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
            LAST_REQUEST.remove(player.getUUID());
        }
        InspectorStatePacket.send(player, enabled);
        return enabled;
    }

    public static boolean isEnabled(ServerPlayer player) {
        return player.hasPermissions(2) && ENABLED.contains(player.getUUID());
    }

    public static boolean allowRequest(ServerPlayer player) {
        if (!isEnabled(player)) return false;
        long now = player.level().getGameTime();
        Long previous = LAST_REQUEST.put(player.getUUID(), now);
        return previous == null || now < previous
                || now - previous >= REQUEST_COOLDOWN_TICKS;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        ENABLED.remove(playerId);
        LAST_REQUEST.remove(playerId);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            setEnabled(player, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ENABLED.clear();
        LAST_REQUEST.clear();
        DiagnosticRecorder.clear();
    }
}
