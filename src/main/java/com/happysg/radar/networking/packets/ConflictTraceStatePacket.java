package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.ConflictTraceRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConflictTraceStatePacket(boolean enabled, String sessionId)
        implements CustomPacketPayload {
    public static final Type<ConflictTraceStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "conflict_trace_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ConflictTraceStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buffer) -> {
                buffer.writeBoolean(packet.enabled);
                buffer.writeUtf(packet.sessionId, 16);
            }, buffer -> new ConflictTraceStatePacket(buffer.readBoolean(),
                    buffer.readUtf(16)));

    public ConflictTraceStatePacket {
        sessionId = sessionId == null ? "none" : sessionId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConflictTraceStatePacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    public static void send(ServerPlayer player, boolean enabled,
                            String sessionId) {
        PacketDistributor.sendToPlayer(player,
                new ConflictTraceStatePacket(enabled, sessionId));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(ConflictTraceStatePacket packet) {
            ConflictTraceRecorder.setEnabled(packet.enabled(),
                    Minecraft.getInstance().level, packet.sessionId());
        }
    }
}
