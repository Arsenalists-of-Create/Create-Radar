package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.skyradar.SkyRadarContraptionEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.foundation.data.CreateEntityBuilder;
import com.tterrag.registrate.util.entry.EntityEntry;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.common.Tags;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModEntityTypes {
    public static final EntityEntry<SkyRadarContraptionEntity> SKY_RADAR_CONTRAPTION =
            ((CreateEntityBuilder<SkyRadarContraptionEntity, ?>) REGISTRATE
                    .entity("sky_radar_contraption", SkyRadarContraptionEntity::new, MobCategory.MISC)
                    .properties(builder -> builder.setTrackingRange(20)
                            .setUpdateInterval(3)
                            .setShouldReceiveVelocityUpdates(false))
                    .properties(AbstractContraptionEntity::build)
                    .properties(builder -> builder.fireImmune())
                    .tag(Tags.EntityTypes.TELEPORTING_NOT_SUPPORTED)
                    .renderer(() -> ContraptionEntityRenderer::new))
                    .visual(() -> ContraptionVisual::new)
                    .register();

    public static void register() {
        CreateRadar.getLogger().info("Registering entity types!");
    }
}
