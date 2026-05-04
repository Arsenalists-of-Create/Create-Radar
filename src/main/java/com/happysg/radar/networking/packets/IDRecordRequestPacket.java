package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record IDRecordRequestPacket(String shipId) implements CustomPacketPayload {
    public static final Type<IDRecordRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            IDRecordRequestPacket::shipId,
            IDRecordRequestPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IDRecordRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            IDManager.IDRecord record = IDManager.getIDRecordByShipId(UUID.fromString(packet.shipId()));

            if (record == null) {
                PacketDistributor.sendToPlayer(
                        sender,
                        (CustomPacketPayload) new IDRecordSyncPacket(packet.shipId(), false, "", "")
                );
                return;
            }

            PacketDistributor.sendToPlayer(
                    sender,
                    (CustomPacketPayload) new IDRecordSyncPacket(packet.shipId(), true, record.name(), record.secretID())
            );
        });
    }

    public static void send(UUID shipId) {
        PacketDistributor.sendToServer(new IDRecordRequestPacket(shipId.toString()));
    }
}