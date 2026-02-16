package com.happysg.radar.config;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.config.client.RadarClientConfig;
import com.happysg.radar.config.server.RadarServerConfig;
import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class RadarConfig {
    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

    private static RadarClientConfig client;
    private static RadarServerConfig server;

    public static RadarClientConfig client() {
        return client;
    }
    public static boolean DEBUG_BEAMS = false;



    public static RadarServerConfig server() {
        return server;
    }

    public static ConfigBase byType(ModConfig.Type type) {
        return CONFIGS.get(type);
    }

    private static <T extends ConfigBase> T register(Supplier<T> factory, ModConfig.Type side) {
        Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });

        T config = specPair.getLeft();
        config.specification = specPair.getRight();
        CONFIGS.put(side, config);
        return config;
    }

    public static void register(ModContainer container) {
        client = register(RadarClientConfig::new, ModConfig.Type.CLIENT);
        server = register(RadarServerConfig::new, ModConfig.Type.SERVER);

        for (Map.Entry<ModConfig.Type, ConfigBase> pair : CONFIGS.entrySet())
            container.registerConfig(pair.getKey(), pair.getValue().specification);
        }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        for (ConfigBase config : CONFIGS.values())
            if (config.specification == event.getConfig().getSpec())
                config.onLoad();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        for (ConfigBase config : CONFIGS.values())
            if (config.specification == event.getConfig().getSpec())
                config.onReload();
    }

    public static BaseConfigScreen createConfigScreen(ModContainer mc, Screen parent) {
        BaseConfigScreen.setDefaultActionFor(CreateRadar.MODID, (base) -> base
                .withSpecs(RadarConfig.client().specification,
                        null,
                        RadarConfig.server().specification));

        return new BaseConfigScreen(parent, CreateRadar.MODID);
    }
}
