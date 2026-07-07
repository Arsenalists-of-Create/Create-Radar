package com.happysg.radar.block.arad.aradnetworks;

import com.happysg.radar.CreateRadar;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class RadarContactRegistryTicker {
    private RadarContactRegistryTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            RadarContactRegistry.tickDecay(sl);
        }
    }
}
