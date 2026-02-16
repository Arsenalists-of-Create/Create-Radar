package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModPonderTags {
    public static final ResourceLocation RADAR_COMPONENT = CreateRadar.asResource("radar_components");
    public static final ResourceLocation WEAPON_NETWORK = CreateRadar.asResource("weapon_network");
    public static final ResourceLocation RADAR_NETWORK = CreateRadar.asResource("radar_network");
    private static final Logger log = LoggerFactory.getLogger(ModPonderTags.class);


    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // Add items to tags here
        PonderTagRegistrationHelper<RegistryEntry<?, ?>> entryHelper = helper.withKeyFunction(RegistryEntry::getId);
        helper.registerTag(WEAPON_NETWORK)
                .addToIndex()
                .item(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
                .title("Weapon Networks")
                .description("How to use the Weapon Networks")
                .register();
        entryHelper.addToTag(WEAPON_NETWORK)
                .add(ModBlocks.RADAR_LINK)
                .add(ModBlocks.FIRE_CONTROLLER_BLOCK)
                .add(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
                .add(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK)
                .add(ModBlocks.NETWORK_FILTERER_BLOCK);

        helper.registerTag(RADAR_NETWORK);


    }

}
