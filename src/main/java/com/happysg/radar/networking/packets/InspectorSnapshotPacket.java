package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.BlockDiagnosticSnapshot;
import com.happysg.radar.debug.client.DiagnosticInspectorClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InspectorSnapshotPacket(BlockDiagnosticSnapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<InspectorSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "inspector_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            InspectorSnapshotPacket> STREAM_CODEC = StreamCodec.ofMember(
            InspectorSnapshotPacket::encode, InspectorSnapshotPacket::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        snapshot.encode(buffer);
    }

    private static InspectorSnapshotPacket decode(
            RegistryFriendlyByteBuf buffer) {
        return new InspectorSnapshotPacket(
                BlockDiagnosticSnapshot.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InspectorSnapshotPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet.snapshot()));
    }

    public static void send(ServerPlayer player,
                            BlockDiagnosticSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player,
                new InspectorSnapshotPacket(snapshot));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(BlockDiagnosticSnapshot snapshot) {
            DiagnosticInspectorClient.acceptSnapshot(snapshot);
        }
    }
}
