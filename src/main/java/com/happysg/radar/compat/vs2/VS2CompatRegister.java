package com.happysg.radar.compat.vs2;

import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.controller.id.IdentificationTransponder;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlockEntity;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import static com.happysg.radar.CreateRadar.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

public class VS2CompatRegister {
    public static BlockEntry<StationaryRadarBlock> STATIONARY_RADAR;
    public static BlockEntry<IdentificationTransponder> ID_BLOCK;
    public static BlockEntry<RadarWarningReceiverBlock> RWR_BLOCK;


    public static BlockEntityEntry<StationaryRadarBlockEntity> STATIONARY_RADAR_BE;
    public static BlockEntityEntry<RadarWarningReceiverBlockEntity> RWR_BE;
    public static void registerVS2() {

        STATIONARY_RADAR = REGISTRATE.block("plane_radar", StationaryRadarBlock::new)
                        .initialProperties(SharedProperties::softMetal)
                        .addLayer(() -> RenderType::cutout)
                        .properties(p -> p.noOcclusion())
                        .properties(p -> p.strength(0.8f))
                        .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                        .transform(axeOrPickaxe())
                        .simpleItem()
                        .register();


        STATIONARY_RADAR_BE = REGISTRATE
                .blockEntity("plane_radar", StationaryRadarBlockEntity::new)
                .validBlocks(STATIONARY_RADAR)
                .register();


        ID_BLOCK = REGISTRATE.block("identification_transponder", IdentificationTransponder::new)
                        .initialProperties(SharedProperties::softMetal)
                        .properties(p -> p.noOcclusion())
                        .properties(p -> p.strength(0.8f))
                        .transform(axeOrPickaxe())
                        .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                        .simpleItem()
                        .register();


       RWR_BLOCK = REGISTRATE.block("radar_warning_receiver", RadarWarningReceiverBlock::new)
                        .initialProperties(SharedProperties::softMetal)
                        .properties(BlockBehaviour.Properties::noOcclusion)
                        .properties(p -> p.strength(0.5f))
                        .transform(axeOrPickaxe())
                        .simpleItem()
                        .register();
        RWR_BE = REGISTRATE
                .blockEntity("rwr_be", RadarWarningReceiverBlockEntity::new)
                .validBlocks(RWR_BLOCK)
                .register();
    }
}
