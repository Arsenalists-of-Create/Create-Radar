package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;

import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererRenderer;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.track.TrackControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorRenderer;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.plane.StationaryRadarBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import com.simibubi.create.content.contraptions.bearing.BearingVisual;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities;

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
    public static final BlockEntityEntry<StationaryRadarBlockEntity> STATIONARY_RADAR = REGISTRATE
            .blockEntity("plane_radar", StationaryRadarBlockEntity::new)
            .validBlocks(ModBlocks.STATIONARY_RADAR)
            .register();

    public static final BlockEntityEntry<DataLinkBlockEntity> RADAR_LINK = REGISTRATE
            .blockEntity("data_link", DataLinkBlockEntity::new)
//            .renderer(() -> DataLinkRenderer::new)
            .validBlocks(ModBlocks.RADAR_LINK)
            .register();


    public static final BlockEntityEntry<AutoYawControllerBlockEntity> AUTO_YAW_CONTROLLER = REGISTRATE
            .blockEntity("auto_yaw_controller", AutoYawControllerBlockEntity::new)
            .visual(() -> ShaftVisual::new, true)
            .validBlocks(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK)
            .renderer(() -> ShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<AutoPitchControllerBlockEntity> AUTO_PITCH_CONTROLLER = REGISTRATE
            .blockEntity("auto_pitch_controller", AutoPitchControllerBlockEntity::new)
            .visual(() -> ShaftVisual::new, true)
            .validBlocks(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
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
    public static final BlockEntityEntry<RadarWarningReceiverBlockEntity> RWR_BE = REGISTRATE
            .blockEntity("rwr_be", RadarWarningReceiverBlockEntity::new)
            .validBlocks(ModBlocks.RWR_BLOCK)
            .register();

    public static void register() {
        CreateRadar.getLogger().info("Registering block entity types!");
    }
}
