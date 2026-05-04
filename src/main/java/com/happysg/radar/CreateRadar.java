package com.happysg.radar;

import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.monitor.MonitorInputHandler;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2CompatRegister;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.ponder.RadarPonderPlugin;
import com.happysg.radar.registry.*;
import com.mojang.logging.LogUtils;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

@Mod(CreateRadar.MODID)
public class CreateRadar {

    public static final String MODID = "create_radar";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateRadar(IEventBus modEventBus, ModContainer container) {
        getLogger().info("Initializing Create Radar!");

        NeoForge.EVENT_BUS.register(this);
        REGISTRATE.registerEventListeners(modEventBus);

        ModItems.register();
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModCreativeTabs.register(modEventBus);
        ModLang.register();
        ModPartials.init();
        ModSounds.register(modEventBus);



        RadarConfig.register(container);

        modEventBus.addListener(CreateRadar::init);
        modEventBus.addListener(CreateRadar::clientInit);
        modEventBus.addListener(CreateRadar::onLoadComplete);
        modEventBus.addListener(CreateRadar::registerCapabilities);

        NeoForge.EVENT_BUS.addListener(CreateRadar::clientTick);
        NeoForge.EVENT_BUS.addListener(CreateRadar::onLoadWorld);

        if (Mods.SABLE.isLoaded()) {
            VS2CompatRegister.registerVS2();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
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

    public static void clientInit(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new RadarPonderPlugin());
        NeoForge.EVENT_BUS.addListener(MonitorInputHandler::monitorPlayerHovering);

    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get(),
                (be, side) -> be.getItemHandler()
        );
    }

    public static void onLoadComplete(FMLLoadCompleteEvent event) {
    }

    public static void onLoadWorld(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        if (world.getServer() != null) {
            IDManager.load(world.getServer());
        }
    }

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModDisplayBehaviors.register();
            AllDataBehaviors.registerDefaults();

            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_BEARING_BLOCK.get(), () -> 4d);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get(), () -> 64d);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get(), () -> 64d);

            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_RECEIVER_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_DISH_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_PLATE_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.get(), () -> 0d);
        });
    }

    static {
        REGISTRATE.setTooltipModifierFactory(item ->
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }
}
