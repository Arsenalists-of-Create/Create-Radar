package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarContraption;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ModContraptionTypes {

    public static ContraptionType RADAR_BEARING;

    public static void register() {
        RADAR_BEARING = new ContraptionType(RadarContraption::new);
        ResourceLocation id = CreateRadar.asResource("radar_bearing");
        Registry.register(CreateBuiltInRegistries.CONTRAPTION_TYPE, id, RADAR_BEARING);
        CreateRadar.getLogger().info("Registered contraption type '{}'", id);
    }

    public static void onRegister(RegisterEvent event) {
        CreateRadar.getLogger().info("RegisterEvent for: {}", event.getRegistryKey().location());
        if (event.getRegistryKey().location().getPath().contains("contraption_type")) {
            register();
        }
    }
}
