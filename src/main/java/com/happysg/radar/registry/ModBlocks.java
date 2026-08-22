package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;


import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock;
import com.happysg.radar.block.controller.id.IdentificationTransponder;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;

import com.happysg.radar.block.controller.tpitch.TPitchControllerBlock;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;

import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlock;
import com.happysg.radar.block.radar.sonar.sensor.SonarSensorBlock;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlock;
import com.happysg.radar.block.radar.skyradar.SkyRadarSublevelConnectorBlock;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableAwareDataLinkBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import static com.happysg.radar.CreateRadar.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

@SuppressWarnings("removal")
public class ModBlocks {
    public static final BlockEntry<MonitorBlock> MONITOR =
            REGISTRATE.block("monitor", MonitorBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
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
            REGISTRATE.block("data_link", ModBlocks::createDataLinkBlock)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .addLayer(() -> RenderType::translucent)
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.directionalBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
                    .item(DataLinkBlockItem::new)
                    .build()
                    .register();

    private static DataLinkBlock createDataLinkBlock(BlockBehaviour.Properties properties) {
        return Mods.SABLE.<DataLinkBlock>runIfInstalled(
                        () -> () -> new SableAwareDataLinkBlock(properties))
                .orElseGet(() -> new DataLinkBlock(properties));
    }


    public static final BlockEntry<RadarBearingBlock> RADAR_BEARING_BLOCK =
            REGISTRATE.block("radar_bearing", RadarBearingBlock::new)
                    .initialProperties(SharedProperties::softMetal)
//                    .transform(BlockStressDefaults.setImpact(4))
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
                    .transform(axeOrPickaxe())
                    .item()
                    .model(AssetLookup.customBlockItemModel("_", "item"))
                    .build()
                    .register();





    @SuppressWarnings("unused")
    public static final BlockEntry<RadarReceiverBlock> RADAR_RECEIVER_BLOCK =
            REGISTRATE.block("radar_receiver_block", RadarReceiverBlock::new)
                    .initialProperties(SharedProperties::softMetal)
//                    .transform(BlockStressDefaults.setImpact(0))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 180))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> RADAR_DISH_BLOCK =
            REGISTRATE.block("radar_dish_block", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_DISH))
                    .lang("Radar Dish")
                    .initialProperties(SharedProperties::softMetal)

//                    .transform(BlockStressDefaults.setImpact(0))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .addLayer(() -> RenderType::cutoutMipped)
                    .transform(axeOrPickaxe())
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> RADAR_PLATE_BLOCK =
            REGISTRATE.block("radar_plate_block", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_PLATE))
                    .lang("Radar Plate")
                    .initialProperties(SharedProperties::softMetal)
//                    .transform(BlockStressDefaults.setImpact(0))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();

    @SuppressWarnings("unused")
    public static final BlockEntry<AbstractRadarFrame> CREATIVE_RADAR_PLATE_BLOCK =
            REGISTRATE.block("creative_radar_plate", properties -> new AbstractRadarFrame(properties, ModShapes.RADAR_PLATE))
                    .initialProperties(SharedProperties::softMetal)
//                    .transform(BlockStressDefaults.setImpact(0))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models()
                            .getExistingFile(ctx.getId()), 0))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();


    public static final BlockEntry<AutoYawControllerBlock> AUTO_YAW_CONTROLLER_BLOCK =
            REGISTRATE.block("auto_yaw_controller", AutoYawControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
//                    .transform(BlockStressDefaults.setImpact(128))
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))

                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.directionalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();

    public static final BlockEntry<AutoPitchControllerBlock> AUTO_PITCH_CONTROLLER_BLOCK =
            REGISTRATE.block("auto_pitch_controller", AutoPitchControllerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.isRedstoneConductor((pState, pLevel, pPos) -> false))
//                    .transform(BlockStressDefaults.setImpact(128))
                    .transform(BuilderTransformers.bearing("windmill", "gearbox"))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
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
                                .modelFile(provider.models().getExistingFile(ResourceLocation.fromNamespaceAndPath("create_radar", "block/fire_controller")))
                                .addModel()
                                .partialState().with(FireControllerBlock.POWERED, true)
                                .modelForState()
                                .modelFile(provider.models().getExistingFile(ResourceLocation.fromNamespaceAndPath("create_radar", "block/fire_controller_on")))
                                .addModel();
                    })          .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<NetworkFiltererBlock> NETWORK_FILTERER_BLOCK =
            REGISTRATE.block("network_filterer", NetworkFiltererBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
                            prov.models().getExistingFile(ctx.getId()), 0))
                    .simpleItem()
                    .register();




    public static final BlockEntry<StationaryRadarBlock> STATIONARY_RADAR =
            REGISTRATE.block("plane_radar", StationaryRadarBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .addLayer(() -> RenderType::cutout)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();

    public static final BlockEntry<IdentificationTransponder> ID_BLOCK =
            REGISTRATE.block("identification_transponder", IdentificationTransponder::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.standardModel(c, p)))
                    .simpleItem()
                    .register();

    public static final BlockEntry<RadarWarningReceiverBlock> RWR_BLOCK =
            REGISTRATE.block("radar_warning_receiver", RadarWarningReceiverBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .properties(p -> p.strength(0.5f))
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.getVariantBuilder(c.get())
                            .partialState().with(RadarWarningReceiverBlock.ON_SHIP, false)
                            .modelForState()
                            .modelFile(p.models().getExistingFile(ResourceLocation.fromNamespaceAndPath("create_radar", "block/radar_warning_receiver_off")))
                            .addModel()
                            .partialState().with(RadarWarningReceiverBlock.ON_SHIP, true)
                            .modelForState()
                            .modelFile(p.models().getExistingFile(ResourceLocation.fromNamespaceAndPath("create_radar", "block/radar_warning_receiver_on")))
                            .addModel())
                    .item()
                    .model((c, p) -> p.withExistingParent(c.getName(), ResourceLocation.fromNamespaceAndPath("create_radar", "block/radar_warning_receiver_off")))
                    .build()
                    .register();
    public static final BlockEntry<SkyRadarBlock> SKY_RADAR = REGISTRATE.block("sky_radar",SkyRadarBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .properties(p -> p.strength(0.8f))
            .blockstate((c, p) -> p.horizontalBlock(c.getEntry(),
                    p.models().getExistingFile(CreateRadar.asResource("block/sky_radar_mount"))))
            .transform(axeOrPickaxe())
            .item()
                    .model((c, p) -> p.withExistingParent(c.getName(),
                            CreateRadar.asResource("block/sky_radar_mount_item")))
            .build()
            .register();

    public static final BlockEntry<SkyRadarSublevelConnectorBlock> SKY_RADAR_SUBLEVEL_CONNECTOR =
            REGISTRATE.block("sky_radar_sublevel_connector", SkyRadarSublevelConnectorBlock::new)
                    .initialProperties(() -> Blocks.BARRIER)
                    .properties(p -> p.mapColor(MapColor.NONE))
                    .properties(p -> p.strength(0.0f))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .properties(BlockBehaviour.Properties::noCollission)
                    .properties(BlockBehaviour.Properties::noLootTable)
                    .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(ResourceLocation.withDefaultNamespace("block/barrier"))))
                    .register();

    public static final BlockEntry<SonarBearingBlock> SONAR_BEARING =
            REGISTRATE.block("sonar_bearing", SonarBearingBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.horizontalBlock(c.getEntry(), p.models().getExistingFile(CreateRadar.asResource("block/sonar_bearing"))))
                    .item()
                    .model((c, p) -> p.withExistingParent(c.getName(), CreateRadar.asResource("block/sonar_bearing")))
                    .build()
                    .register();

    public static final BlockEntry<SonarSensorBlock> SONAR_SENSOR =
            REGISTRATE.block("sonar_sensor", SonarSensorBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), p.models().getExistingFile(CreateRadar.asResource("block/sonar_sensor"))))
                    .item()
                    .model((c, p) -> p.withExistingParent(c.getName(), CreateRadar.asResource("block/sonar_sensor")))
                    .build()
                    .register();

    public static final BlockEntry<TPitchControllerBlock> T_PITCH =
            REGISTRATE.block("t_pitch", TPitchControllerBlock::new)
                    .lang("T-Pitch Controller")
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .blockstate((c, p) -> p.getVariantBuilder(c.get())
                            .forAllStates(state -> {
                                TPitchControllerBlock.Orientation orientation =
                                        state.getValue(TPitchControllerBlock.ORIENTATION);
                                return ConfiguredModel.builder()
                                        .modelFile(p.models().getExistingFile(
                                                CreateRadar.asResource("block/t_pitch")))
                                        .rotationX(orientation.modelRotationX())
                                        .rotationY(orientation.modelRotationY())
                                        .build();
                            }))
                    .transform(axeOrPickaxe())
                    .item()
                    .model((c, p) -> p.withExistingParent(c.getName(),
                            CreateRadar.asResource("block/track_controller")))
                    .build()
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
