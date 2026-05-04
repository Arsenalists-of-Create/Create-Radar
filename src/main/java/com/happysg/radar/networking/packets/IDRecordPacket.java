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

public record IDRecordPacket(String shipId, String shipSlug, String secretID, String newSlug) implements CustomPacketPayload {

    public IDRecordPacket {
        shipSlug = shipSlug == null ? "" : shipSlug;
        secretID = secretID == null ? "" : secretID;
        newSlug = newSlug == null ? "" : newSlug;
    }

    public static final Type<IDRecordPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipId,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipSlug,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::secretID,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::newSlug,
            IDRecordPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IDRecordPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer)) {
                return;
            }

            IDManager.addIDRecord(UUID.fromString(packet.shipId()), packet.secretID(), packet.newSlug());
        });
    }

    public static void send(UUID shipId, String shipSlug, String secretID, String newSlug) {
        PacketDistributor.sendToServer(new IDRecordPacket(shipId.toString(), shipSlug, secretID, newSlug));
    }
}