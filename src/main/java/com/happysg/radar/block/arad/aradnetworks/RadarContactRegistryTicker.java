package com.happysg.radar.block.arad.aradnetworks;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.debug.ConflictTraceRecorder;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class RadarContactRegistryTicker {
    private RadarContactRegistryTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            ConflictTraceRecorder.Scope trace = ConflictTraceRecorder.begin(
                    "radar_contacts", "level_tick", sl, null,
                    java.util.Map.of());
            try {
                ARADTargeting.tickNativeRadars(sl);
                RadarContactRegistry.tickDecay(sl);
                ExternalRwrEmitterRegistry.tickDecay(sl);
            } catch (RuntimeException exception) {
                trace.failed(exception.getClass().getSimpleName());
                throw exception;
            } finally {
                trace.close();
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            ARADTargeting.clearNativeRadars(sl);
            ExternalRwrEmitterRegistry.clear(sl);
        }
    }
}
