package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.BlockDiagnosticService;
import com.happysg.radar.debug.InspectorSessionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InspectorRequestPacket() implements CustomPacketPayload {
    public static final Type<InspectorRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID,
                    "inspector_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            InspectorRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new InspectorRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InspectorRequestPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!InspectorSessionManager.isEnabled(player)) {
                InspectorStatePacket.send(player, false);
                return;
            }
            if (!InspectorSessionManager.allowRequest(player)) return;
            InspectorSnapshotPacket.send(player,
                    BlockDiagnosticService.inspect(player));
        });
    }

    public static void send() {
        PacketDistributor.sendToServer(new InspectorRequestPacket());
    }
}
