package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetControllerMovementLimitsPacket(
        BlockPos controllerPos, double minDegrees, double maxDegrees
) implements CustomPacketPayload {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0;

    public static final Type<SetControllerMovementLimitsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateRadar.MODID, "set_controller_movement_limits")
    );

    public static final StreamCodec<FriendlyByteBuf,
            SetControllerMovementLimitsPacket> STREAM_CODEC =
            StreamCodec.ofMember(
                    SetControllerMovementLimitsPacket::encode,
                    SetControllerMovementLimitsPacket::decode);

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(controllerPos);
        buffer.writeDouble(minDegrees);
        buffer.writeDouble(maxDegrees);
    }

    private static SetControllerMovementLimitsPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new SetControllerMovementLimitsPacket(
                buffer.readBlockPos(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetControllerMovementLimitsPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !Double.isFinite(packet.minDegrees())
                    || !Double.isFinite(packet.maxDegrees())) {
                return;
            }

            BlockPos pos = packet.controllerPos();
            if (!player.level().hasChunkAt(pos)
                    || player.distanceToSqr(Vec3.atCenterOf(pos))
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }

            if (player.level().getBlockEntity(pos)
                    instanceof ControllerLimitAccess controller) {
                controller.setMovementLimits(
                        packet.minDegrees(), packet.maxDegrees());
            }
        });
    }

    public static void send(BlockPos controllerPos,
                            double minDegrees, double maxDegrees) {
        PacketDistributor.sendToServer(new SetControllerMovementLimitsPacket(
                controllerPos, minDegrees, maxDegrees));
    }
}
