package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.ControllerLimitsScreen;
import com.happysg.radar.compat.vs2.SableUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authorized opening of an assembled controller's limits screen. */
public record OpenControllerLimitsScreenPacket(
        BlockPos controllerPos
) implements CustomPacketPayload {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0;

    public static final Type<OpenControllerLimitsScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateRadar.MODID, "open_controller_limits"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            OpenControllerLimitsScreenPacket> STREAM_CODEC =
            StreamCodec.ofMember(
                    OpenControllerLimitsScreenPacket::encode,
                    OpenControllerLimitsScreenPacket::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(controllerPos);
    }

    private static OpenControllerLimitsScreenPacket decode(
            RegistryFriendlyByteBuf buffer
    ) {
        return new OpenControllerLimitsScreenPacket(buffer.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void openIfAssembled(
            Level level, BlockPos position, Player player
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !level.hasChunkAt(position)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof ControllerLimitAccess controller)
                || !controller.hasAssembledControlledMount()
                || serverPlayer.distanceToSqr(
                SableUtils.getWorldVec(blockEntity))
                > MAX_INTERACTION_DISTANCE_SQR) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer,
                new OpenControllerLimitsScreenPacket(position));
    }

    public static void handle(OpenControllerLimitsScreenPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.open(packet.controllerPos()));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void open(BlockPos position) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null
                    && minecraft.level.getBlockEntity(position)
                    instanceof ControllerLimitAccess) {
                minecraft.setScreen(new ControllerLimitsScreen(position));
            }
        }
    }
}
