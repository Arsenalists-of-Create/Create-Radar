package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.limits.ControllerLimitsScreen;
import com.happysg.radar.block.controller.limits.collision.ControllerCollisionSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record ControllerCollisionSnapshotPacket(
        BlockPos controllerPos, int sessionNonce,
        ControllerCollisionSnapshot snapshot
) implements CustomPacketPayload {
    public static final Type<ControllerCollisionSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateRadar.MODID, "controller_collision_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            ControllerCollisionSnapshotPacket> STREAM_CODEC =
            StreamCodec.ofMember(ControllerCollisionSnapshotPacket::encode,
                    ControllerCollisionSnapshotPacket::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(controllerPos);
        buffer.writeInt(sessionNonce);
        buffer.writeEnum(snapshot.status());
        buffer.writeEnum(snapshot.axis());
        buffer.writeFloat(snapshot.halfSpan());
        buffer.writeFloat(snapshot.depth());
        buffer.writeFloat(snapshot.dialCenterU());
        buffer.writeFloat(snapshot.dialCenterV());
        buffer.writeFloat(snapshot.dialZeroDegrees());
        buffer.writeDouble(snapshot.minDegrees());
        buffer.writeDouble(snapshot.maxDegrees());
        buffer.writeBoolean(snapshot.spanClipped());
        buffer.writeBoolean(snapshot.scanTruncated());
        buffer.writeVarInt(snapshot.boxes().size());
        for (ControllerCollisionSnapshot.OrientedBox box : snapshot.boxes()) {
            buffer.writeFloat(box.centerU());
            buffer.writeFloat(box.centerV());
            buffer.writeFloat(box.centerDepth());
            buffer.writeFloat(box.axisXU());
            buffer.writeFloat(box.axisXV());
            buffer.writeFloat(box.axisXDepth());
            buffer.writeFloat(box.axisYU());
            buffer.writeFloat(box.axisYV());
            buffer.writeFloat(box.axisYDepth());
            buffer.writeFloat(box.axisZU());
            buffer.writeFloat(box.axisZV());
            buffer.writeFloat(box.axisZDepth());
            buffer.writeEnum(box.category());
        }
    }

    private static ControllerCollisionSnapshotPacket decode(
            RegistryFriendlyByteBuf buffer
    ) {
        BlockPos position = buffer.readBlockPos();
        int nonce = buffer.readInt();
        ControllerCollisionSnapshot.Status status = buffer.readEnum(
                ControllerCollisionSnapshot.Status.class);
        CannonAxis axis = buffer.readEnum(CannonAxis.class);
        float halfSpan = buffer.readFloat();
        float depth = buffer.readFloat();
        float dialCenterU = buffer.readFloat();
        float dialCenterV = buffer.readFloat();
        float dialZeroDegrees = buffer.readFloat();
        double minDegrees = buffer.readDouble();
        double maxDegrees = buffer.readDouble();
        boolean spanClipped = buffer.readBoolean();
        boolean scanTruncated = buffer.readBoolean();
        if (!Float.isFinite(halfSpan) || halfSpan < 5.0f
                || halfSpan > 256.0f || !Float.isFinite(depth)
                || depth <= 0.0f || depth > 32.0f) {
            throw new IllegalArgumentException(
                    "Invalid controller collision view dimensions");
        }
        int count = buffer.readVarInt();
        if (count < 0
                || count > ControllerCollisionSnapshot.MAX_PACKET_BOXES) {
            throw new IllegalArgumentException(
                    "Invalid controller collision box count " + count);
        }
        ArrayList<ControllerCollisionSnapshot.OrientedBox> boxes =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ControllerCollisionSnapshot.OrientedBox box =
                    new ControllerCollisionSnapshot.OrientedBox(
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readEnum(
                                    ControllerCollisionSnapshot.Category.class));
            if (!box.isFinite()) {
                throw new IllegalArgumentException(
                        "Non-finite controller collision box");
            }
            boxes.add(box);
        }
        return new ControllerCollisionSnapshotPacket(position, nonce,
                new ControllerCollisionSnapshot(status, axis, halfSpan,
                        depth, dialCenterU, dialCenterV, dialZeroDegrees,
                        minDegrees, maxDegrees,
                        spanClipped, scanTruncated, boxes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControllerCollisionSnapshotPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(ControllerCollisionSnapshotPacket packet) {
            if (Minecraft.getInstance().screen
                    instanceof ControllerLimitsScreen screen) {
                screen.acceptCollisionSnapshot(packet.controllerPos(),
                        packet.sessionNonce(), packet.snapshot());
            }
        }
    }
}
