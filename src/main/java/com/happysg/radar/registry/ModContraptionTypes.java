package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarContraption;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ModContraptionTypes {

    public static ContraptionType RADAR_BEARING;

    public static void register(RegisterEvent event) {
        event.register(CreateBuiltInRegistries.CONTRAPTION_TYPE.key(), helper -> {
            ResourceLocation id = CreateRadar.asResource("radar_bearing");
            RADAR_BEARING = new ContraptionType(RadarContraption::new);
            helper.register(id, RADAR_BEARING);
        });
    }
}
