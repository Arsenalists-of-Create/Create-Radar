package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.api.monitor.client.MonitorRwrIconRegistry;
import com.happysg.radar.api.radar.rwr.RadarRwrTypes;
import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.config.RadarConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateRadar.MODID, dist = Dist.CLIENT)
public final class ModClient {
    public ModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, RadarConfig::createConfigScreen);
        registerMonitorApiDefaults();
    }

    private static void registerMonitorApiDefaults() {
        MonitorRwrIconRegistry.register(RadarRwrTypes.GROUND, MonitorSprite.RADAR_SYMBOL.getTexture());
        MonitorRwrIconRegistry.register(RadarRwrTypes.SKY, MonitorSprite.SKY_RADAR_SYMBOL.getTexture());
        MonitorRwrIconRegistry.register(RadarRwrTypes.AIRBORNE, MonitorSprite.PLANE_RADAR_SYMBOL.getTexture());
        MonitorRwrIconRegistry.register(RadarRwrTypes.GENERIC, MonitorSprite.RADAR_SYMBOL.getTexture());
    }
}