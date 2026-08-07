package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
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

public record InspectorStatePacket(boolean enabled)
        implements CustomPacketPayload {
    public static final Type<InspectorStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "inspector_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            InspectorStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buffer) -> buffer.writeBoolean(packet.enabled),
            buffer -> new InspectorStatePacket(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InspectorStatePacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet.enabled()));
    }

    public static void send(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player,
                new InspectorStatePacket(enabled));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(boolean enabled) {
            DiagnosticInspectorClient.setEnabled(enabled);
        }
    }
}
