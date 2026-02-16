package com.happysg.radar;

import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.monitor.MonitorInputHandler;
import com.happysg.radar.compat.cbcwpf.CBCWPFCompatRegister;
import com.happysg.radar.compat.computercraft.CCCompatRegister;
import com.happysg.radar.networking.RadarNetworking;
import com.happysg.radar.ponder.RadarPonderPlugin;
import com.happysg.radar.registry.ModCommands;
import com.happysg.radar.registry.ModCapabilities;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CBCCompatRegister;
import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.*;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.api.stress.BlockStressValues;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Mod(CreateRadar.MODID)
public class CreateRadar {

    public static final String MODID = "create_radar";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    public CreateRadar(IEventBus modEventBus, ModContainer container) {
        getLogger().info("Initializing Create Radar!");

        REGISTRATE.registerEventListeners(modEventBus);

        ModItems.register();
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModCreativeTabs.register(modEventBus);
        ModLang.register();
        ModPartials.init();
        RadarConfig.register(container);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        modEventBus.addListener(ModCapabilities::registerCaps);
        modEventBus.addListener(ModContraptionTypes::register);
        modEventBus.addListener(RadarNetworking::register);
        modEventBus.addListener(CreateRadar::init);
        modEventBus.addListener(CreateRadar::clientInit);
        modEventBus.addListener(CreateRadar::onLoadComplete);


        container.registerExtensionPoint(IConfigScreenFactory.class, (Supplier<IConfigScreenFactory>) () -> RadarConfig::createConfigScreen);

        NeoForge.EVENT_BUS.addListener(CreateRadar::clientTick);
        NeoForge.EVENT_BUS.addListener(CreateRadar::onLoadWorld);
        ModSounds.register(modEventBus);

        // Compat modules
        if (Mods.CREATEBIGCANNONS.isLoaded())
            CBCCompatRegister.registerCBC();
        if (Mods.CBCMODERNWARFARE.isLoaded())
            CBCMWCompatRegister.registerCBCMW();
        if (Mods.COMPUTERCRAFT.isLoaded())
            CCCompatRegister.registerPeripherals();
        if (Mods.SHUPAPIUM.isLoaded())
            CBCWPFCompatRegister.registerCBCWPF();
    }

    private static void clientTick(ClientTickEvent.Post event) {
        DataLinkBlockItem.clientTick();
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static String toHumanReadable(String key) {
        String s = key.replace("_", " ");
        s = Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(s))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
        return StringUtils.normalizeSpace(s);
    }

    public static void clientInit(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new RadarPonderPlugin());
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(MonitorInputHandler::monitorPlayerHovering);
        }
    }

    public static void onLoadComplete(FMLLoadCompleteEvent event) {

    }

    public static void onLoadWorld(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        if (world.getServer() != null) {
            IDManager.load(world.getServer());
        }
    }
    public static void init(final FMLCommonSetupEvent event) {

        event.enqueueWork(() -> {
            // Must be registered after registries open
            // Stress values
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_BEARING_BLOCK.get(), () -> 4d);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get(), () -> 128d);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get(), () -> 128d);
          //  BlockStressValues.IMPACTS.register(ModBlocks.TRACK_CONTROLLER_BLOCK.get(), () -> 16d);

            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_RECEIVER_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_DISH_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_PLATE_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.get(), () -> 0d);
        });

        ModDisplayBehaviors.register();
        AllDataBehaviors.registerDefaults();
    }


}
