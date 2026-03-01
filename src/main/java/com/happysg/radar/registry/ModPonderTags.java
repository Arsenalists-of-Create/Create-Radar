package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.simibubi.create.foundation.ponder.PonderRegistry;

import com.simibubi.create.foundation.ponder.PonderTag;
import com.tterrag.registrate.util.entry.RegistryEntry;


import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModPonderTags {
    public static final PonderTag RADAR_COMPONENT = create("radar_components").item(ModBlocks.RADAR_PLATE_BLOCK)
            .defaultLang("Radar Components", "Components which allow the creation of Radar Contraptions")
            .addToIndex();
    public static final PonderTag WEAPON_NETWORK = create("weapon_network").item(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK).addToIndex();
    public static final PonderTag RADAR_NETWORK = create("radar_network").item(ModBlocks.NETWORK_FILTERER_BLOCK).addToIndex();


    private static PonderTag create(String id) {
        return new PonderTag(CreateRadar.asResource(id));
    }
    public static void register() {
        // Add items to tags here


        PonderRegistry.TAGS.forTag(WEAPON_NETWORK)
                .add(ModBlocks.RADAR_LINK)
                .add(ModBlocks.FIRE_CONTROLLER_BLOCK)
                .add(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
                .add(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK)
                .add(ModBlocks.NETWORK_FILTERER_BLOCK);



    }

}
