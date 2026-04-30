package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;


import com.happysg.radar.block.arad.jammer.shield.ShieldJammerBlock;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;

import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.monitor.MonitorBlock;
import com.happysg.radar.block.mount.SmartMountBlock;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.radome.CannonMountRadomeBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;

import com.happysg.radar.block.radar.skyradar.SkyRadarBlock;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlock;
import com.happysg.radar.block.radar.sonar.panel.SonarPanel;
import com.happysg.radar.block.siren.SirenBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
            REGISTRATE.block("data_link", DataLinkBlock::new)
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
                                .modelFile(provider.models().cubeAll("off",net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_radar", "block/off")))
                                .addModel()
                                .partialState().with(FireControllerBlock.POWERED, true)
                                .modelForState()
                                .modelFile(provider.models().cubeAll("on",net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_radar", "block/on")))
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
    public static final BlockEntry<CannonMountRadomeBlock> RADOME =
            REGISTRATE.block("radome",CannonMountRadomeBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.noOcclusion())
                    .properties(p -> p.strength(0.8f))

                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<SmartMountBlock> SMART_MOUNT =
            REGISTRATE.block("smart_mount",SmartMountBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<SirenBlock> SIREN =
            REGISTRATE.block("siren", SirenBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .addLayer(() -> RenderType::cutoutMipped)
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<SkyRadarBlock> SKY_RADAR =
            REGISTRATE.block("sky_radar", SkyRadarBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<SonarBearingBlock> SONAR_BEARING =
            REGISTRATE.block("sonar_bearing", SonarBearingBlock::new)
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
    public static final BlockEntry<SonarPanel> SONAR_PANEL =
            REGISTRATE.block("sonar_panel", SonarPanel::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();
    public static final BlockEntry<ShieldJammerBlock> SHIELD_JAMMER =
            REGISTRATE.block("shield_jammer", ShieldJammerBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.noOcclusion())
                    .properties(p -> p.strength(0.8f))
                    .transform(axeOrPickaxe())
                    .simpleItem()
                    .register();










    public static void register() {
        CreateRadar.getLogger().info("Registering blocks!");
    }
}