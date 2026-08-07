package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.ClientDiagnosticReport;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientDiagnosticRequestPacket(int requestId)
        implements CustomPacketPayload {
    public static final Type<ClientDiagnosticRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "client_diagnostic_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ClientDiagnosticRequestPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buffer) -> buffer.writeInt(packet.requestId),
            buffer -> new ClientDiagnosticRequestPacket(buffer.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientDiagnosticRequestPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet.requestId()));
    }

    public static void send(ServerPlayer player, int requestId) {
        PacketDistributor.sendToPlayer(player,
                new ClientDiagnosticRequestPacket(requestId));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(int requestId) {
            ClientDiagnosticResponsePacket.send(requestId,
                    ClientDiagnosticReport.capture());
        }
    }
}
