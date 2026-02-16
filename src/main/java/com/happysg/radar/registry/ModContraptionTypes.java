package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarContraption;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

public class ModContraptionTypes {

    public static final ResourceLocation RADAR_BEARING_ID = CreateRadar.asResource("radar_bearing");
    public static ContraptionType RADAR_BEARING;

    @SuppressWarnings("unchecked")
    public static void register(RegisterEvent event) {
        // Cast the registry key to the proper generic type for RegisterEvent
        ResourceKey<Registry<ContraptionType>> key =
                (ResourceKey<Registry<ContraptionType>>) CreateBuiltInRegistries.CONTRAPTION_TYPE.key();

        if (!event.getRegistryKey().equals(key))
            return;

        RADAR_BEARING = new ContraptionType(RadarContraption::new);
        event.register(key, RADAR_BEARING_ID, () -> RADAR_BEARING);

        CreateRadar.getLogger().info("Registered contraption type '{}'", RADAR_BEARING_ID);
    }
}
