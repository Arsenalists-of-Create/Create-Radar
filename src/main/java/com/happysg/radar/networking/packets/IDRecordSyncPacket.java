package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record IDRecordSyncPacket(String shipId, boolean hasRecord, String name, String secretID) implements CustomPacketPayload {

    public IDRecordSyncPacket {
        name = name == null ? "" : name;
        secretID = secretID == null ? "" : secretID;
    }

    public static final Type<IDRecordSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            IDRecordSyncPacket::shipId,
            ByteBufCodecs.BOOL,
            IDRecordSyncPacket::hasRecord,
            ByteBufCodecs.STRING_UTF8,
            IDRecordSyncPacket::name,
            ByteBufCodecs.STRING_UTF8,
            IDRecordSyncPacket::secretID,
            IDRecordSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IDRecordSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(
                packet.shipId(),
                packet.hasRecord(),
                packet.name(),
                packet.secretID()
        ));
    }

    public static void send(ServerPlayer player, String shipId, boolean hasRecord, String name, String secretID) {
        PacketDistributor.sendToPlayer(player, new IDRecordSyncPacket(shipId, hasRecord, name, secretID));
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        private static void handle(String shipId, boolean hasRecord, String name, String secretID) {
            if (hasRecord) {
                IDManager.addIDRecord(UUID.fromString(shipId), secretID, name);
            } else {
                IDManager.ID_RECORDS.remove(UUID.fromString(shipId));
            }

            if (Minecraft.getInstance().screen instanceof com.happysg.radar.block.controller.id.IDBlockScreen screen
                    && screen.isForShip(UUID.fromString(shipId))) {
                screen.applyLoadedRecord(name, secretID);
            }
        }
    }
}