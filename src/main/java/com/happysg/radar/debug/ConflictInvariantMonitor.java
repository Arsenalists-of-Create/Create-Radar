package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/** Low-frequency checks which observe loaded state and never repair it. */
@EventBusSubscriber(modid = CreateRadar.MODID)
public final class ConflictInvariantMonitor {
    private static final int CHECK_INTERVAL = 100;
    private static final int REQUIRED_FAILURES = 3;
    private static final Map<String, Integer> CONSECUTIVE = new HashMap<>();

    private ConflictInvariantMonitor() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            checkRadarHeartbeat(level);
            checkWeaponNetworks(level);
            checkCallbackPairs(level);
        }
    }

    private static void checkRadarHeartbeat(ServerLevel level) {
        long last = ConflictTraceRecorder.lastHeartbeatTick(
                "radar_contacts", "level_tick", level);
        boolean healthy = last < 0L || level.getGameTime() - last <= 40L;
        observe(level, "radar_tick_stalled", null, healthy,
                "Radar/contact level tick heartbeat is stale");
    }

    private static void checkWeaponNetworks(ServerLevel level) {
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.peek(level);
        if (runtime == null) return;
        boolean expectsTick = false;
        for (WeaponNetworkRuntime.WeaponGroupView group : runtime.getGroups()) {
            if (group.pitchPos() != null) expectsTick = true;
            for (BlockPos endpoint : group.endpoints()) {
                if (!level.hasChunkAt(endpoint)) continue;
                observe(level, "network_endpoint_missing", endpoint,
                        level.getBlockEntity(endpoint) != null,
                        "Loaded weapon-network endpoint has no block entity");
            }
        }
        if (expectsTick) {
            long last = ConflictTraceRecorder.lastHeartbeatTick(
                    "weapon_network", "coordinator_tick", level);
            observe(level, "weapon_tick_stalled", null,
                    last >= 0L && level.getGameTime() - last <= 40L,
                    "Active weapon network has no recent coordinator heartbeat");
        }
    }

    private static void checkCallbackPairs(ServerLevel level) {
        for (String family : new String[]{"big_cannon", "autocannon",
                "cbc_at_heavy", "cbc_at_twin"}) {
            long head = ConflictTraceRecorder.heartbeatCount(
                    "mixin_callback", family + "_fire_head", level);
            long returned = ConflictTraceRecorder.heartbeatCount(
                    "mixin_callback", family + "_fire_return", level);
            observe(level, "mixin_callback_unbalanced_" + family, null,
                    head - returned <= 2L,
                    "Mixin HEAD callbacks exceed RETURN callbacks for " + family);
        }
    }

    private static void observe(ServerLevel level, String code,
                                BlockPos position, boolean healthy,
                                String message) {
        String key = level.dimension().location() + "|" + code + "|"
                + (position == null ? "" : position.asLong());
        if (healthy) {
            CONSECUTIVE.remove(key);
            return;
        }
        int failures = CONSECUTIVE.merge(key, 1, Integer::sum);
        if (failures == REQUIRED_FAILURES) {
            ConflictTraceRecorder.invariant(false, "runtime_invariant", code,
                    message, level, position);
        }
    }
}
