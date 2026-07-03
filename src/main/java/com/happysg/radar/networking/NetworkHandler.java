package com.happysg.radar.networking;

import com.happysg.radar.block.monitor.MonitorSelectionPacket;
import com.happysg.radar.networking.packets.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static com.happysg.radar.CreateRadar.MODID;

@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkHandler {
    private NetworkHandler() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                SaveListsPacket.TYPE,
                SaveListsPacket.STREAM_CODEC,
                SaveListsPacket::handle
        );

        registrar.playToServer(
                BoolListPacket.TYPE,
                BoolListPacket.STREAM_CODEC,
                BoolListPacket::handle
        );

        registrar.playToServer(
                RaycastPacket.TYPE,
                RaycastPacket.STREAM_CODEC,
                RaycastPacket::handle
        );

        registrar.playToServer(
                FirePacket.TYPE,
                FirePacket.STREAM_CODEC,
                FirePacket::handle
        );

        registrar.playToServer(
                MonitorSelectionPacket.TYPE,
                MonitorSelectionPacket.STREAM_CODEC,
                MonitorSelectionPacket::handle
        );
        registrar.playToServer(
                IDRecordPacket.TYPE,
                IDRecordPacket.STREAM_CODEC,
                IDRecordPacket::handle
        );

        registrar.playToServer(
                IDRecordRequestPacket.TYPE,
                IDRecordRequestPacket.STREAM_CODEC,
                IDRecordRequestPacket::handle
        );

        registrar.playToClient(
                IDRecordSyncPacket.TYPE,
                IDRecordSyncPacket.STREAM_CODEC,
                IDRecordSyncPacket::handle
        );

        registrar.playToServer(
                SableSilhouetteRequestPacket.TYPE,
                SableSilhouetteRequestPacket.STREAM_CODEC,
                SableSilhouetteRequestPacket::handle
        );

        registrar.playToClient(
                SableSilhouetteSyncPacket.TYPE,
                SableSilhouetteSyncPacket.STREAM_CODEC,
                SableSilhouetteSyncPacket::handle
        );
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToClients(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}
