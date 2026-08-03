package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.compat.vs2.SableUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            if (!player.level().hasChunkAt(pos)) {
                return;
            }

            if (player.level().getBlockEntity(pos)
                    instanceof ControllerLimitAccess controller
                    && controller instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                    && controller.hasAssembledControlledMount()
                    && player.distanceToSqr(SableUtils.getWorldVec(blockEntity))
                    <= MAX_INTERACTION_DISTANCE_SQR) {
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
