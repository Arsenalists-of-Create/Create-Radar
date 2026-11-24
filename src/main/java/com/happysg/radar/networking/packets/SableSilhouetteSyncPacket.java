package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.sable.SableSilhouetteClientCache;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
import com.happysg.radar.compat.sable.SubLevelSilhouette;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SableSilhouetteSyncPacket(UUID sublevelId, int revision, byte status, List<SubLevelSilhouette.LocalBox> boxes, UUID assemblyId, List<UUID> memberIds) implements CustomPacketPayload {
    private static final int MAX_PACKET_BOXES = 16_384;

    public static final Type<SableSilhouetteSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "sable_silhouette_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SableSilhouetteSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(SableSilhouetteSyncPacket::encode, SableSilhouetteSyncPacket::decode);

    public SableSilhouetteSyncPacket {
        boxes = List.copyOf(boxes);
        memberIds = List.copyOf(memberIds);

        if (boxes.size() > MAX_PACKET_BOXES) {
            throw new IllegalArgumentException("Too many silhouette boxes");
        }

        if (memberIds.isEmpty() || memberIds.size() > 33 || !memberIds.contains(sublevelId)) {
            throw new IllegalArgumentException("Invalid silhouette membership");
        }
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(sublevelId);
        buf.writeUUID(assemblyId);
        buf.writeVarInt(revision);
        buf.writeByte(status);
        buf.writeVarInt(boxes.size());
        for (SubLevelSilhouette.LocalBox box : boxes) {
            buf.writeDouble(box.minX());
            buf.writeDouble(box.minY());
            buf.writeDouble(box.minZ());
            buf.writeDouble(box.maxX());
            buf.writeDouble(box.maxY());
            buf.writeDouble(box.maxZ());
        }

        buf.writeVarInt(memberIds.size());
        for (UUID memberId : memberIds) {
            buf.writeUUID(memberId);
        }
    }

    private static SableSilhouetteSyncPacket decode(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        UUID assemblyId = buf.readUUID();
        int revision = buf.readVarInt();
        byte status = buf.readByte();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PACKET_BOXES) {
            throw new IllegalArgumentException("Invalid silhouette box count " + count);
        }
        ArrayList<SubLevelSilhouette.LocalBox> boxes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double minX = buf.readDouble();
            double minY = buf.readDouble();
            double minZ = buf.readDouble();
            double maxX = buf.readDouble();
            double maxY = buf.readDouble();
            double maxZ = buf.readDouble();
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || !(maxX > minX) || !(maxY > minY) || !(maxZ > minZ)) {
                throw new IllegalArgumentException("Invalid silhouette box");
            }
            boxes.add(new SubLevelSilhouette.LocalBox(minX, minY, minZ, maxX, maxY, maxZ));
        }
        int memberCount = buf.readVarInt();
        if (memberCount < 1 || memberCount > 33) {
            throw new IllegalArgumentException("Invalid silhouette member count");
        }

        List<UUID> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(buf.readUUID());
        }

        return new SableSilhouetteSyncPacket(id, revision, status, boxes, assemblyId, members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SableSilhouetteSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(SableSilhouetteSyncPacket packet) {
            SableSilhouetteClientCache.setAssemblyMembers(packet.assemblyId(), packet.memberIds());
            if (SableSilhouetteStatus.drawable(packet.status())) {
                SableSilhouetteClientCache.put(packet.sublevelId(), packet.revision(), SubLevelSilhouette.of(packet.boxes()));
            }
        }
    }
}
