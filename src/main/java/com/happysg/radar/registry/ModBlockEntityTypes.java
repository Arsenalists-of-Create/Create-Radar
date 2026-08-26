package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;


import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.controller.kinetic.ControllerShaftRenderer;
import com.happysg.radar.block.controller.kinetic.ControllerShaftVisual;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererRenderer;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;

import com.happysg.radar.block.controller.tpitch.TPitchControllerBlockEntity;
import com.happysg.radar.block.controller.tpitch.TPitchControllerShaftRenderer;
import com.happysg.radar.block.controller.tpitch.TPitchControllerShaftVisual;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorRenderer;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.plane.StationaryRadarBlockEntity;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.block.radar.skyradar.SkyRadarRenderer;
import com.happysg.radar.block.radar.skyradar.SkyRadarVisual;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlockEntity;

import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import com.simibubi.create.content.contraptions.bearing.BearingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;


import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModBlockEntityTypes {

    public static final BlockEntityEntry<MonitorBlockEntity> MONITOR = REGISTRATE
            .blockEntity("monitor", MonitorBlockEntity::new)
            .validBlocks(ModBlocks.MONITOR)
            .renderer(() -> MonitorRenderer::new)
            .register();

     public static final BlockEntityEntry<RadarBearingBlockEntity> RADAR_BEARING = REGISTRATE
            .blockEntity("radar_bearing", RadarBearingBlockEntity::new)
            .visual(() -> BearingVisual::new, true)
            .validBlocks(ModBlocks.RADAR_BEARING_BLOCK)
            .renderer(() -> BearingRenderer::new)
            .register();


    public static final BlockEntityEntry<DataLinkBlockEntity> RADAR_LINK = REGISTRATE
            .blockEntity("data_link", DataLinkBlockEntity::new)
//            .renderer(() -> DataLinkRenderer::new)
            .validBlocks(ModBlocks.RADAR_LINK)
            .register();


    public static final BlockEntityEntry<AutoYawControllerBlockEntity> AUTO_YAW_CONTROLLER = REGISTRATE
            .blockEntity("auto_yaw_controller", AutoYawControllerBlockEntity::new)
            .visual(() -> ControllerShaftVisual::new, true)
            .validBlocks(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK)
            .renderer(() -> ControllerShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<AutoPitchControllerBlockEntity> AUTO_PITCH_CONTROLLER = REGISTRATE
            .blockEntity("auto_pitch_controller", AutoPitchControllerBlockEntity::new)
            .visual(() -> ControllerShaftVisual::new, true)
            .validBlocks(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
            .renderer(() -> ControllerShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<TPitchControllerBlockEntity> T_PITCH_CONTROLLER = REGISTRATE
            .blockEntity("t_pitch", TPitchControllerBlockEntity::new)
            .visual(() -> TPitchControllerShaftVisual::new, true)
            .validBlocks(ModBlocks.T_PITCH)
            .renderer(() -> TPitchControllerShaftRenderer::new)
            .register();


    public static final BlockEntityEntry<FireControllerBlockEntity> FIRE_CONTROLLER = REGISTRATE
            .blockEntity("fire_controller", FireControllerBlockEntity::new)
            .validBlocks(ModBlocks.FIRE_CONTROLLER_BLOCK)
            .register();
    public static final BlockEntityEntry<NetworkFiltererBlockEntity> NETWORK_FILTER_BLOCK_ENTITY = REGISTRATE
            .blockEntity("network_filterer_block_entity", NetworkFiltererBlockEntity::new)
            .validBlocks(ModBlocks.NETWORK_FILTERER_BLOCK)
            .renderer(()-> NetworkFiltererRenderer::new)
            .register();

    public static final BlockEntityEntry<StationaryRadarBlockEntity> STATIONARY_RADAR_BE = REGISTRATE
            .blockEntity("plane_radar", StationaryRadarBlockEntity::new)
            .validBlocks(ModBlocks.STATIONARY_RADAR)
            .register();

    public static final BlockEntityEntry<SkyRadarBlockEntity> SKY_RADAR_BE = REGISTRATE
            .blockEntity("sky_radar", SkyRadarBlockEntity::new)
            .visual(() -> SkyRadarVisual::new, true)
            .renderer(() -> SkyRadarRenderer::new)
            .validBlocks(ModBlocks.SKY_RADAR)
            .register();

    public static final BlockEntityEntry<SonarBearingBlockEntity> SONAR_BEARING_BE = REGISTRATE
            .blockEntity("sonar_bearing", SonarBearingBlockEntity::new)
            .validBlocks(ModBlocks.SONAR_BEARING)
            .register();

    public static final BlockEntityEntry<RadarWarningReceiverBlockEntity> RWR_BE = REGISTRATE
            .blockEntity("rwr_be", RadarWarningReceiverBlockEntity::new)
            .validBlocks(ModBlocks.RWR_BLOCK)
            .register();


    public static void register() {
        CreateRadar.getLogger().info("Registering block entity types!");
    }
}
