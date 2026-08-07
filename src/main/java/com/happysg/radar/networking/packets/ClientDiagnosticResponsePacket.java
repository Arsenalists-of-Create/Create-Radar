package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.ClientDiagnosticReport;
import com.happysg.radar.debug.DiagnosticReportCoordinator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientDiagnosticResponsePacket(
        int requestId, ClientDiagnosticReport report)
        implements CustomPacketPayload {
    public static final Type<ClientDiagnosticResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "client_diagnostic_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ClientDiagnosticResponsePacket> STREAM_CODEC = StreamCodec.ofMember(
            ClientDiagnosticResponsePacket::encode,
            ClientDiagnosticResponsePacket::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(requestId);
        report.encode(buffer);
    }

    private static ClientDiagnosticResponsePacket decode(
            RegistryFriendlyByteBuf buffer) {
        return new ClientDiagnosticResponsePacket(buffer.readInt(),
                ClientDiagnosticReport.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientDiagnosticResponsePacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DiagnosticReportCoordinator.acceptClientReport(player,
                        packet.requestId(), packet.report());
            }
        });
    }

    public static void send(int requestId, ClientDiagnosticReport report) {
        PacketDistributor.sendToServer(
                new ClientDiagnosticResponsePacket(requestId, report));
    }
}
