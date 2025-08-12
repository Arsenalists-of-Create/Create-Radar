package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.track.TrackControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.monitor.MonitorRenderer;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.plane.PlaneRadarBlockEntity;

import com.simibubi.create.content.contraptions.bearing.BearingInstance;
import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import com.simibubi.create.content.kinetics.base.ShaftInstance;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
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
        .instance(() -> BearingInstance::new)
        .validBlocks(ModBlocks.RADAR_BEARING_BLOCK)
        .renderer(() -> BearingRenderer::new)
        .register();
    public static final BlockEntityEntry<PlaneRadarBlockEntity> PLANE_RADAR = REGISTRATE
            .blockEntity("plane_radar", PlaneRadarBlockEntity::new)
            .validBlocks(ModBlocks.PLANE_RADAR)
            .register();

    public static final BlockEntityEntry<DataLinkBlockEntity> RADAR_LINK = REGISTRATE
            .blockEntity("data_link", DataLinkBlockEntity::new)
//            .renderer(() -> DataLinkRenderer::new)
            .validBlocks(ModBlocks.RADAR_LINK)
            .register();


    public static final BlockEntityEntry<AutoYawControllerBlockEntity> AUTO_YAW_CONTROLLER = REGISTRATE
            .blockEntity("auto_yaw_controller", AutoYawControllerBlockEntity::new)
            .instance(() -> ShaftInstance::new)
            .validBlocks(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK)
            .renderer(() -> ShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<AutoPitchControllerBlockEntity> AUTO_PITCH_CONTROLLER = REGISTRATE
            .blockEntity("auto_pitch_controller", AutoPitchControllerBlockEntity::new)
            .instance(() -> ShaftInstance::new)
            .validBlocks(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK)
            .renderer(() -> ShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<TrackControllerBlockEntity> TRACK_CONTROLLER = REGISTRATE
            .blockEntity("track_controller", TrackControllerBlockEntity::new)
            .validBlocks(ModBlocks.TRACK_CONTROLLER_BLOCK)
            .register();
    public static final BlockEntityEntry<TrackControllerBlockEntity> FIRE_CONTROLLER = REGISTRATE
            .blockEntity("fire_controller", TrackControllerBlockEntity::new)
            .validBlocks(ModBlocks.FIRE_CONTROLLER_BLOCK)
            .register();

    public static void register() {
        CreateRadar.getLogger().info("Registering block entity types!");
    }
}
