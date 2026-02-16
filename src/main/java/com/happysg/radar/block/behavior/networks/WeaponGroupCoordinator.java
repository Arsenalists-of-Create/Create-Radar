package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs ONE "aim+fire decision" tick per mount group per server tick.
 * This prevents yaw/pitch/fire from being evaluated on different ticks.
 */
@EventBusSubscriber(modid = CreateRadar.MODID)
public final class WeaponGroupCoordinator {

    private static final int REFRESH_EVERY_TICKS = 1; // keep controllers fresh (was 5)

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        WeaponNetworkData wnd = WeaponNetworkData.get(sl);
        if (wnd == null) return;

        // Ensure each mount group is processed only once per tick
        Set<String> processedMounts = new HashSet<>();

        for (WeaponNetworkData.Group g : wnd.getGroups().values()) {
            ResourceKey<Level> dim = g.key.dim();
            if (!dim.equals(sl.dimension())) continue;

            BlockPos mountPos = g.key.mountPos();
            String mountKey = dim.location() + "|" + mountPos.asLong();

            if (!processedMounts.add(mountKey)) {
                continue;
            }

            if (g.pitchPos == null) continue;

            BlockEntity be = sl.getBlockEntity(g.pitchPos);
            if (!(be instanceof AutoPitchControllerBlockEntity pitch)) continue;

            // Ensure the control object exists
            if (pitch.firingControl == null) {
                pitch.getFiringControl();
            }
            if (pitch.firingControl == null) continue;

            // Keep controller refs fresh (yaw/pitch/fire)
            if (sl.getGameTime() % REFRESH_EVERY_TICKS == 0) {
                pitch.firingControl.refreshControllers();
            }


            pitch.firingControl.tick();
        }
    }
}