package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;


import com.happysg.radar.compat.vs2.VS2CompatRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;


import java.util.function.Supplier;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModCreativeTabs {
    public static DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateRadar.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADAR_CREATIVE_TAB = addTab("radar", "Create: Radars",
            ModBlocks.MONITOR::asStack);


    public static DeferredHolder<CreativeModeTab, CreativeModeTab> addTab(String id, String name, Supplier<ItemStack> icon) {
        String itemGroupId = "itemGroup." + CreateRadar.MODID + "." + id;
        REGISTRATE.addRawLang(itemGroupId, name);

        CreativeModeTab.Builder tabBuilder = CreativeModeTab.builder()
                .icon(icon)
                .displayItems(ModCreativeTabs::displayItems)
                .title(Component.translatable(itemGroupId))
                .withTabsBefore(getCreateTabOrFallback());
        
        return CREATIVE_TABS.register(id, tabBuilder::build);
    }

    private static ResourceKey<CreativeModeTab> getCreateTabOrFallback() {
        try {
            Class<?> clazz = Class.forName("com.simibubi.create.AllCreativeModeTabs");
            var field = clazz.getField("PALETTES_CREATIVE_TAB");
            Object palettesTab = field.get(null);

            var getKeyMethod = palettesTab.getClass().getMethod("getKey");
            @SuppressWarnings("unchecked")
            ResourceKey<CreativeModeTab> key =
                    (ResourceKey<CreativeModeTab>) getKeyMethod.invoke(palettesTab);

            return key;
        } catch (Throwable t) {
            return CreativeModeTabs.REDSTONE_BLOCKS;
        }
    }

    private static void displayItems(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        pOutput.accept(ModBlocks.MONITOR);
        pOutput.accept(ModBlocks.RADAR_LINK);
        pOutput.accept(ModBlocks.RADAR_BEARING_BLOCK);
        pOutput.accept(ModBlocks.RADAR_RECEIVER_BLOCK);
        pOutput.accept(ModBlocks.RADAR_PLATE_BLOCK);
        pOutput.accept(ModBlocks.RADAR_DISH_BLOCK);
        pOutput.accept(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK);
        pOutput.accept(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK);
        pOutput.accept(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK);
        pOutput.accept(ModBlocks.NETWORK_FILTERER_BLOCK);
        pOutput.accept(ModBlocks.FIRE_CONTROLLER_BLOCK);
        pOutput.accept(ModItems.SAFE_ZONE_DESIGNATOR);
        pOutput.accept(ModItems.IDENT_FILTER_ITEM);
        pOutput.accept(ModItems.RADAR_FILTER_ITEM);
        pOutput.accept(ModItems.TARGET_FILTER_ITEM);
        pOutput.accept(ModItems.BINOCULARS);
        pOutput.accept(ModItems.GUIDED_FUZE);
        if (Mods.TRACKWORK.isLoaded()) {

        }
        if (Mods.SABLE.isLoaded()) {
            pOutput.accept(VS2CompatRegister.ID_BLOCK);
            pOutput.accept(VS2CompatRegister.STATIONARY_RADAR);
            pOutput.accept(VS2CompatRegister.RWR_BLOCK);

        }
    }


    public static void register(IEventBus eventBus) {
        CreateRadar.getLogger().info("Registering CreativeTabs!");
        CREATIVE_TABS.register(eventBus);
    }

}
