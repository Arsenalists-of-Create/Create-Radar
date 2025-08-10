package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.id.IDBlock;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;
import com.happysg.radar.block.controller.track.TrackControllerBlock;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.PlaneRadarBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;

import com.simibubi.create.content.kinetics.BlockStressDefaults;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;

import static com.happysg.radar.CreateRadar.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

public class ModBlocks {
    public static final BlockEntry<MonitorBlock> MONITOR =
            REGISTRATE.block("monitor", MonitorBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .blockstate((c, p) -> p.getVariantBuilder(c.get())
                            .forAllStates(state -> {
                                String shape = state.getValue(MonitorBlock.SHAPE).toString().toLowerCase();
                                return ConfiguredModel.builder()
                                        .modelFile(p.models()
                                                .getExistingFile(CreateRadar.asResource("block/monitor/monitor_" + shape)))
                                        .rotationY(((int) state.getValue(MonitorBlock.FACING).toYRot() + 180) % 360)
                                        .build();
                            }))
                    .addLayer(() -> RenderType::cutoutMipped)
                    .transform(axeOrPickaxe())
                    .item()
                    .model((c, p) -> p.withExistingParent(c.getName(), CreateRadar.asResource("block/monitor/monitor_single")))
                    .build()
                    .register();


    public static final BlockEntry<DataLinkBlock> RADAR_LINK =
            REGISTRATE.block("data_link", DataLinkBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .addLayer(() -> RenderType::translucent)
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.directionalBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
                    .item(DataLinkBlockItem::new)
                    .build()
                    .register();


    public static final BlockEntry<RadarBearingBlock> RADAR_BEARING_BLOCK =
            REGISTRATE.block("radar_bearing", RadarBearingBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(4.0d))
                    .item()
                    .model(AssetLookup.customBlockItemModel("_", "item"))
                    .build()
                    .register();

    public static final BlockEntry<PlaneRadarBlock> PLANE_RADAR =
            REGISTRATE.block("plane_radar", PlaneRadarBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .addLayer(() -> RenderType::cutout)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();

    public static BlockEntry<IDBlock> ID_BLOCK =
            REGISTRATE.block("id_block", IDBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<RadarReceiverBlock> RADAR_RECEIVER_BLOCK =
            REGISTRATE.block("radar_receiver_block", RadarReceiverBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(0.0d))
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 180))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> RADAR_DISH_BLOCK =
            REGISTRATE.block("radar_dish_block", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_DISH))
                    .lang("Radar Dish")
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .addLayer(() -> RenderType::cutoutMipped)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(0.0d))
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> RADAR_PLATE_BLOCK =
            REGISTRATE.block("radar_plate_block", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_PLATE))
                    .lang("Radar Plate")
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(0.0d))
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> CREATIVE_RADAR_PLATE_BLOCK =
            REGISTRATE.block("creative_radar_plate", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_PLATE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .initialProperties(SharedProperties::softMetal)
                    .transform(BlockStressDefaults.setImpact(0.0d))
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();


    public static final BlockEntry<AutoYawControllerBlock> AUTO_YAW_CONTROLLER_BLOCK =
            REGISTRATE.block("auto_yaw_controller", AutoYawControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(128.0d))
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();

    public static final BlockEntry<TrackControllerBlock> TRACK_CONTROLLER_BLOCK =
            REGISTRATE.block("track_controller", TrackControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(16.0d))
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();

    public static final BlockEntry<AutoPitchControllerBlock> AUTO_PITCH_CONTROLLER_BLOCK =
            REGISTRATE.block("auto_pitch_controller", AutoPitchControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .transform(BlockStressDefaults.setImpact(128.0d))
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();
    public static final BlockEntry<FireControllerBlock> FIRE_CONTROLLER_BLOCK =
            REGISTRATE.block("fire_controller", FireControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .blockstate((context, provider) -> {
                        provider.getVariantBuilder(context.get())
                                .partialState().with(FireControllerBlock.POWERED, false)
                                .modelForState()
                                .modelFile(provider.models().cubeAll("off",new ResourceLocation("create_radar","block/fire_controller")))
                                .addModel()
                                .partialState().with(FireControllerBlock.POWERED, true)
                                .modelForState()
                                .modelFile(provider.models().cubeAll("on",new ResourceLocation("create_radar","block/fire_controller_on")))
                                .addModel();
                    })                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();



    public static void register() {
        CreateRadar.getLogger().info("Registering blocks!");
//        BlockStressValues.IMPACTS.register(RADAR_BEARING_BLOCK.get(), () -> 4d);
//        BlockStressValues.IMPACTS.register(AUTO_YAW_CONTROLLER_BLOCK.get(), () -> 128d);
//        BlockStressValues.IMPACTS.register(AUTO_PITCH_CONTROLLER_BLOCK.get(), () -> 128d);
//        BlockStressValues.IMPACTS.register(TRACK_CONTROLLER_BLOCK.get(), () -> 16d);
//
//        // zero-impact parts
//        BlockStressValues.IMPACTS.register(RADAR_RECEIVER_BLOCK.get(), () -> 0d);
//        BlockStressValues.IMPACTS.register(RADAR_DISH_BLOCK.get(), () -> 0d);
//        BlockStressValues.IMPACTS.register(RADAR_PLATE_BLOCK.get(), () -> 0d);
//        BlockStressValues.IMPACTS.register(CREATIVE_RADAR_PLATE_BLOCK.get(), () -> 0d);
    }
}
