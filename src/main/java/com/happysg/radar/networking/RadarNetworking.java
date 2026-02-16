package com.happysg.radar.networking;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorSelectionPayload;
import com.happysg.radar.networking.packets.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class RadarNetworking {
    public static final String CHANNEL = CreateRadar.MODID + ":main";
    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(CHANNEL).versioned(VERSION);

        // DataLink config
        r.playToServer(
                RadarLinkConfigurationPayload.TYPE,
                RadarLinkConfigurationPayload.STREAM_CODEC,
                RadarLinkConfigurationPayload::handle
        );

        // Lists / filters
        r.playToServer(
                SaveListsPayload.TYPE,
                SaveListsPayload.STREAM_CODEC,
                SaveListsPayload::handle
        );

        r.playToServer(
                BoolListPayload.TYPE,
                BoolListPayload.STREAM_CODEC,
                BoolListPayload::handle
        );

        // Binoculars actions
        r.playToServer(
                RaycastPayload.TYPE,
                RaycastPayload.STREAM_CODEC,
                RaycastPayload::handle
        );

        r.playToServer(
                FirePayload.TYPE,
                FirePayload.STREAM_CODEC,
                FirePayload::handle
        );

        // ID records
        r.playToServer(
                IDRecordPayload.TYPE,
                IDRecordPayload.STREAM_CODEC,
                IDRecordPayload::handle
        );

        // Monitor selection (convert MonitorSelectionPacket -> MonitorSelectionPayload)
        r.playToServer(
                MonitorSelectionPayload.TYPE,
                MonitorSelectionPayload.STREAM_CODEC,
                MonitorSelectionPayload::handle
        );
    }
}
