package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.ponder.PonderScenes;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;


public class ModPonderIndex implements PonderPlugin {
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        CreateRadar.getLogger().info("Registering Ponder!");
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);
        HELPER.forComponents(ModBlocks.RADAR_BEARING_BLOCK)
                .addStoryBoard("radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT)
                .addStoryBoard("radar_network", PonderScenes::networkSetup, ModPonderTags.RADAR_COMPONENT);

        HELPER.addStoryBoard(ModBlocks.RADAR_RECEIVER_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);
        HELPER.addStoryBoard(ModBlocks.RADAR_DISH_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);
        HELPER.addStoryBoard(ModBlocks.RADAR_PLATE_BLOCK, "radar_contraption", PonderScenes::radarContraption, ModPonderTags.RADAR_COMPONENT);

        HELPER.forComponents(ModBlocks.NETWORK_FILTERER_BLOCK)
                .addStoryBoard("radar_network",PonderScenes::networkSetup,ModPonderTags.RADAR_NETWORK)
                .addStoryBoard("weapon_setup", PonderScenes::weaponSimpleWeaponSetup, ModPonderTags.WEAPON_NETWORK);


        HELPER.addStoryBoard(ModBlocks.MONITOR, "radar_network", PonderScenes::networkSetup, ModPonderTags.RADAR_COMPONENT);
        HELPER.addStoryBoard(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK, "weapon_setup", PonderScenes::weaponSimpleWeaponSetup, ModPonderTags.WEAPON_NETWORK);
        HELPER.addStoryBoard(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK, "weapon_setup", PonderScenes::weaponSimpleWeaponSetup, ModPonderTags.WEAPON_NETWORK);
        HELPER.addStoryBoard(ModBlocks.RADAR_LINK,"weapon_setup", PonderScenes::weaponSimpleWeaponSetup, ModPonderTags.WEAPON_NETWORK);

//        HELPER.addStoryBoard(ModBlocks.RWR_BLOCK,"rwr_ponder",PonderScenes::controllerLinking,ModPonderTags.RADAR_COMPONENT);

    }

    @Override
    public String getModId() {
        return CreateRadar.MODID;
    }
}
