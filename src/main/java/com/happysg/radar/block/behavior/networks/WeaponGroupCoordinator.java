package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Runs ONE "aim+fire decision" tick per mount group per server tick.
 * This prevents yaw/pitch/fire from being evaluated on different ticks.
 */
@EventBusSubscriber(modid = CreateRadar.MODID)
public final class WeaponGroupCoordinator {

    private static final int REFRESH_EVERY_TICKS = 1; // keep controllers fresh (was 5)
    private static final long ERROR_LOG_INTERVAL_TICKS = 200L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Long> LAST_ERROR_LOG = new HashMap<>();

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SableDataLinkRelocation.clear(level);
            WeaponNetworkRuntime.clear(level);
            LAST_ERROR_LOG.keySet().removeIf(key -> key.startsWith(level.dimension().location() + "|"));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        SableDataLinkRelocation.process(sl);
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(sl);

        // Ensure each mount group is processed only once per tick
        Set<String> processedMounts = new HashSet<>();

        for (WeaponNetworkRuntime.WeaponGroupView g : runtime.getGroups()) {
            BlockPos mountPos = g.mountPos();
            String mountKey = sl.dimension().location() + "|" + mountPos.asLong();

            if (!processedMounts.add(mountKey)) {
                continue;
            }

            if (g.pitchPos() == null) continue;

            BlockEntity be = sl.getBlockEntity(g.pitchPos());
            if (!(be instanceof AutoPitchControllerBlockEntity pitch)) continue;

            // Ensure the control object exists
            if (pitch.firingControl == null) {
                pitch.getFiringControl();
            }
            if (pitch.firingControl == null) continue;

            // Keep controller refs fresh (yaw/pitch/fire)
            try {
                if (sl.getGameTime() % REFRESH_EVERY_TICKS == 0) {
                    pitch.firingControl.refreshControllers();
                }
                pitch.firingControl.tick();
            } catch (RuntimeException exception) {
                pitch.firingControl.resetTarget();
                long now = sl.getGameTime();
                long last = LAST_ERROR_LOG.getOrDefault(mountKey, Long.MIN_VALUE);
                if (last == Long.MIN_VALUE || now - last >= ERROR_LOG_INTERVAL_TICKS) {
                    LAST_ERROR_LOG.put(mountKey, now);
                    LOGGER.error("Weapon network at {} failed; firing was stopped and other networks will continue", mountPos, exception);
                }
            }
        }
    }
}
