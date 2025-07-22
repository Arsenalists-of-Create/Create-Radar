package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarContraption;

import com.simibubi.create.content.contraptions.ContraptionType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings({"rawtypes", "unchecked"}) // needed for raw ContraptionType
public class ModContraptionTypes {
    public static final ContraptionType RADAR_BEARING =
            ContraptionType.register("radar_bearing", RadarContraption::new);

    public static void register() {
        CreateRadar.getLogger().info("Registering ContraptionType!");
    }
}