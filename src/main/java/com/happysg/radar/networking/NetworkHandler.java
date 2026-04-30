package com.happysg.radar.networking;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.*;
import com.happysg.radar.block.monitor.MonitorSelectionPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CreateRadar.MODID).versioned("1.0");

        registrar.playToServer(BoolListPacket.TYPE, BoolListPacket.STREAM_CODEC, BoolListPacket::handle);
        registrar.playToServer(FirePacket.TYPE, FirePacket.STREAM_CODEC, FirePacket::handle);
        registrar.playToServer(IDRecordPacket.TYPE, IDRecordPacket.STREAM_CODEC, IDRecordPacket::handle);
        registrar.playToServer(IDRecordRequestPacket.TYPE, IDRecordRequestPacket.STREAM_CODEC, IDRecordRequestPacket::handle);
        registrar.playToServer(MonitorSelectionPacket.TYPE, MonitorSelectionPacket.STREAM_CODEC, MonitorSelectionPacket::handle);
        registrar.playToServer(RadarLinkConfigurationPacket.TYPE, RadarLinkConfigurationPacket.STREAM_CODEC, RadarLinkConfigurationPacket::handle);
        registrar.playToServer(RaycastPacket.TYPE, RaycastPacket.STREAM_CODEC, RaycastPacket::handle);
        registrar.playToServer(SaveListsPacket.TYPE, SaveListsPacket.STREAM_CODEC, SaveListsPacket::handle);

        registrar.playToClient(IDRecordSyncPacket.TYPE, IDRecordSyncPacket.STREAM_CODEC, IDRecordSyncPacket::handle);
    }

    public static void init(IEventBus bus) {
        bus.addListener(NetworkHandler::register);
    }
}