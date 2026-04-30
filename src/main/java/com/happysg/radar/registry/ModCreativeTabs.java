package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.happysg.radar.compat.cbc.CBCCompatRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateRadar.MODID + "_tabs");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADAR_CREATIVE_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("Create: Radars"))
            .icon(() -> new ItemStack(ModBlocks.MONITOR.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModBlocks.MONITOR.get());
                output.accept(ModItems.SAFE_ZONE_DESIGNATOR.get());
                output.accept(ModBlocks.RADAR_LINK.get());
                output.accept(ModBlocks.RADAR_BEARING_BLOCK.get());
                output.accept(ModBlocks.RADAR_RECEIVER_BLOCK.get());
                output.accept(ModBlocks.RADAR_PLATE_BLOCK.get());
                output.accept(ModBlocks.RADAR_DISH_BLOCK.get());
                output.accept(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.get());
                output.accept(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get());
                output.accept(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get());
                output.accept(ModBlocks.NETWORK_FILTERER_BLOCK.get());
                output.accept(ModBlocks.FIRE_CONTROLLER_BLOCK.get());
                output.accept(ModItems.IDENT_FILTER_ITEM.get());
                output.accept(ModItems.RADAR_FILTER_ITEM.get());
                output.accept(ModItems.TARGET_FILTER_ITEM.get());
                output.accept(ModItems.BINOCULARS.get());
                if (CBCCompatRegister.GUIDED_FUZE != null) {
                    output.accept(CBCCompatRegister.GUIDED_FUZE.get());
                }
            })
            .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}