package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModLang {

    public static void register() {
        CreateRadar.getLogger().info("Registering Lang!");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.header", "Creating a Radar!");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_1", "Place Radar Bearing");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_2", "Place Radar Receiver");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_3", "Add Radar Dishes");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_4", "Radar Dishes can be used interchangeably with Radar Plates");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_5", "Additional dishes/plates extend range");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_contraption.text_6", "Power Radar Bearing");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.tag.radar_components", "Radars");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.tag.radar_components.description", "Components which allow the creation of Radar Tracking System");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_linking.header", "Linking a Radar to a Monitor!");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_linking.text_1", "Place Monitor");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_linking.text_2", "Build Radar Contraption");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_linking.text_3", "Link using Data Link");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.radar_linking.text_4", "Right click Radar Link to configure");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".display_source.radar", "Radar");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".display_source.yaw_controller", "Auto Yaw Controller");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".display_source.pitch_controller", "Auto Pitch Controller");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.show", "Show");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.all_entities", "All Entities");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.no_mobs", "No Mobs");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.only_mobs", "Only Mobs");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.players_only", "Players Only");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.projectiles_only", "Projectiles Only");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.vs2_only", "VS2 Only");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".filter.mob_bosses_only", "Mob Bosses Only");


        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.controller_linking.header", "Linking a Controller to a Monitor!");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.controller_linking.text_1", "Place Yaw Controller under turret mount");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.controller_linking.text_2", "Link using Data Links");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.controller_linking.text_3", "Repeat for pitch Controller");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".ponder.controller_linking.text_4", "Select Target on monitor for turret to aim/fire at");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".pitch.powder_charge", "Powder Charge");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".guided_fuze.no_monitor", "No Monitor Linked");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar.no_receiver", "No Receiver Found");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".data_link.fail", "Data Link has no valid target, try placing it again");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".data_link.no_source", "Not a Data Source");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".data_link.no_target", "Not a Monitor");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".data_link.title", "Data Link");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.player", "Player");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.vs2", "VS2");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.contraption", "Contraption");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.mob", "Mob");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.hostile", "Hostile");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.projectile", "Projectile");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.animal", "Animal");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.auto_target", "Auto Target");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar_button.auto_fire", "Auto Fire");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar.dish_count", "Dish Count: %s");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".radar.range", "Range: %s");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.set", "Monitor Set");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".guided_fuze.linked_monitor", "Linked Monitor: %s");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", "No Monitor Linked");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.start", "Start Position Set");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.reset", "Start Position Clear");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.end", "Safe Zone Set");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".item.safe_zone_designator.remove", "Safe Zone Removed");

        REGISTRATE.addRawLang(CreateRadar.MODID + ".id_block.not_on_vs2", "VS2 not found");
        REGISTRATE.addRawLang(CreateRadar.MODID + ".id_block.not_on_ship", "Not on a ship");
    }
}
