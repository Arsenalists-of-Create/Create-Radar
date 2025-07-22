package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.ponder.PonderScenes;
import com.simibubi.create.foundation.ponder.PonderRegistrationHelper;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.resources.ResourceLocation;

public class ModPonderIndex {
    static final PonderRegistrationHelper HELPER = new PonderRegistrationHelper(CreateRadar.MODID);

    public static void register() {
        CreateRadar.getLogger().info("Registering Ponder!");
        HELPER.forComponents(ModBlocks.RADAR_BEARING_BLOCK)
                .addStoryBoard("radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT)
                .addStoryBoard("radar_linking", PonderScenes::radarLinking, ModPonderTags.RADAR_COMPONENT);

        HELPER.addStoryBoard(ModBlocks.RADAR_RECEIVER_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);
        HELPER.addStoryBoard(ModBlocks.RADAR_DISH_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);
        HELPER.addStoryBoard(ModBlocks.RADAR_PLATE_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);

        HELPER.addStoryBoard(ModBlocks.MONITOR, "radar_linking", PonderScenes::radarLinking, ModPonderTags.RADAR_COMPONENT);
    }
}