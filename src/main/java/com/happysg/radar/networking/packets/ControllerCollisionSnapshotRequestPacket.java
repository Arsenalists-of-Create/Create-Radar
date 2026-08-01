package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionSnapshot;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionSnapshotBuilder;
import com.happysg.radar.compat.vs2.SableUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record ControllerCollisionSnapshotRequestPacket(
        BlockPos controllerPos, int sessionNonce,
        Direction capturedPlayerDirection
) implements CustomPacketPayload {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0;
    private static final long REQUEST_COOLDOWN_TICKS = 10L;
    private static final Map<UUID, Long> LAST_REQUEST_TICKS =
            new ConcurrentHashMap<>();

    public static final Type<ControllerCollisionSnapshotRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateRadar.MODID, "controller_collision_snapshot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ControllerCollisionSnapshotRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(
                    ControllerCollisionSnapshotRequestPacket::encode,
                    ControllerCollisionSnapshotRequestPacket::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(controllerPos);
        buffer.writeInt(sessionNonce);
        buffer.writeEnum(capturedPlayerDirection);
    }

    private static ControllerCollisionSnapshotRequestPacket decode(
            RegistryFriendlyByteBuf buffer
    ) {
        return new ControllerCollisionSnapshotRequestPacket(
                buffer.readBlockPos(), buffer.readInt(),
                buffer.readEnum(Direction.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControllerCollisionSnapshotRequestPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos position = packet.controllerPos();
            Direction direction = packet.capturedPlayerDirection();
            if (!direction.getAxis().isHorizontal()
                    || !player.level().hasChunkAt(position)) {
                send(player, packet, ControllerCollisionSnapshot.error(
                        ControllerCollisionSnapshot.Status.INVALID_REQUEST,
                        CannonAxis.PITCH));
                return;
            }

            BlockEntity blockEntity = player.level().getBlockEntity(position);
            if (!(blockEntity instanceof ControllerLimitAccess controller)) {
                send(player, packet, ControllerCollisionSnapshot.error(
                        ControllerCollisionSnapshot.Status.NO_CONTROLLER,
                        CannonAxis.PITCH));
                return;
            }
            if (player.distanceToSqr(SableUtils.getWorldVec(blockEntity))
                    > MAX_INTERACTION_DISTANCE_SQR) {
                send(player, packet, ControllerCollisionSnapshot.error(
                        ControllerCollisionSnapshot.Status.INVALID_REQUEST,
                        controller.getControlledAxis()));
                return;
            }

            long now = player.level().getGameTime();
            Long last = LAST_REQUEST_TICKS.put(player.getUUID(), now);
            if (last != null && now >= last
                    && now - last < REQUEST_COOLDOWN_TICKS) {
                send(player, packet, ControllerCollisionSnapshot.error(
                        ControllerCollisionSnapshot.Status.RATE_LIMITED,
                        controller.getControlledAxis()));
                return;
            }

            send(player, packet, ControllerCollisionSnapshotBuilder.build(
                    player, blockEntity, direction));
        });
    }

    private static void send(ServerPlayer player,
                             ControllerCollisionSnapshotRequestPacket request,
                             ControllerCollisionSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player,
                new ControllerCollisionSnapshotPacket(
                        request.controllerPos(), request.sessionNonce(),
                        snapshot));
    }

    public static void send(BlockPos position, int nonce,
                            Direction capturedDirection) {
        PacketDistributor.sendToServer(
                new ControllerCollisionSnapshotRequestPacket(
                        position, nonce, capturedDirection));
    }
}
