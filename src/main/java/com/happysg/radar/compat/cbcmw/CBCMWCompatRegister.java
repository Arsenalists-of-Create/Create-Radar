package com.happysg.radar.compat.cbcmw;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.guidance.RadarGuidanceBlock;
import com.happysg.radar.block.guidance.RadarGuidanceBlockEntity;
import com.happysg.radar.block.guidance.RadarGuidanceBlockItem;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CBCMWCompatRegister {
    public static BlockEntry<?> RADAR_GUIDANCE_BLOCK;
    public static BlockEntityEntry<?> RADAR_GUIDANCE_BLOCK_ENTITY;
    public static void registerCBCMW() {
        RADAR_GUIDANCE_BLOCK = CreateRadar.REGISTRATE.block("radar_guidance_block", RadarGuidanceBlock::new)
                .lang("Radar Command Guidance")
                .initialProperties(SharedProperties::softMetal)
                .properties(BlockBehaviour.Properties::noOcclusion)
                .properties(p -> p.strength(0.8f))
                .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                        .getExistingFile(ctx.getId()), 0))
                .item(RadarGuidanceBlockItem::new)
                .build()
                .register();

        RADAR_GUIDANCE_BLOCK_ENTITY = CreateRadar.REGISTRATE
                .blockEntity("radar_guidance_block", RadarGuidanceBlockEntity::new)
                .validBlock(RADAR_GUIDANCE_BLOCK)
                .register();
    }
}