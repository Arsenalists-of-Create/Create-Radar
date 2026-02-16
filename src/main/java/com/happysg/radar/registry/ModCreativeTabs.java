package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CBCCompatRegister;
import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;

import java.util.function.Supplier;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModCreativeTabs {
    public static DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateRadar.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADAR_CREATIVE_TAB =
            addTab("radar", "Create: Radars", ModBlocks.MONITOR::asStack);


    public static DeferredHolder<CreativeModeTab, CreativeModeTab> addTab(String id, String name, Supplier<ItemStack> icon) {
        String itemGroupId = "itemGroup." + CreateRadar.MODID + "." + id;
        REGISTRATE.addRawLang(itemGroupId, name);

        CreativeModeTab.Builder tabBuilder = CreativeModeTab.builder()
                .icon(icon)
                .displayItems(ModCreativeTabs::displayItems) // ← RIGHT HERE
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

    private static void displayItems(CreativeModeTab.ItemDisplayParameters p, CreativeModeTab.Output out) {
        out.accept(ModBlocks.MONITOR.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModItems.SAFE_ZONE_DESIGNATOR.get(), TabVisibility.PARENT_TAB_ONLY);

        out.accept(ModBlocks.RADAR_LINK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.RADAR_BEARING_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.RADAR_RECEIVER_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.RADAR_PLATE_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.RADAR_DISH_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);

        out.accept(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.NETWORK_FILTERER_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModBlocks.FIRE_CONTROLLER_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);

        out.accept(ModItems.IDENT_FILTER_ITEM.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModItems.RADAR_FILTER_ITEM.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModItems.TARGET_FILTER_ITEM.get(), TabVisibility.PARENT_TAB_ONLY);
        out.accept(ModItems.BINOCULARS.get(), TabVisibility.PARENT_TAB_ONLY);

        if (Mods.CBCMODERNWARFARE.isLoaded()) {
            out.accept(CBCMWCompatRegister.RADAR_GUIDANCE_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
        }

        if (Mods.VALKYRIENSKIES.isLoaded()) {
            out.accept(ModBlocks.ID_BLOCK.get(), TabVisibility.PARENT_TAB_ONLY);
            out.accept(ModBlocks.STATIONARY_RADAR.get(), TabVisibility.PARENT_TAB_ONLY);
        }
    }


    public static void register(IEventBus eventBus) {
        CreateRadar.getLogger().info("Registering CreativeTabs!");
        CREATIVE_TABS.register(eventBus);
    }

}
