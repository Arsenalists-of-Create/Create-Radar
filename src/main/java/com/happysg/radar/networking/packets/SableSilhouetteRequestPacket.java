package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableSilhouetteServerCache;
import com.happysg.radar.compat.sable.SubLevelSilhouette;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SableSilhouetteRequestPacket(BlockPos controllerPos, UUID sublevelId, int revision) implements CustomPacketPayload {
    public static final Type<SableSilhouetteRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "sable_silhouette_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SableSilhouetteRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(SableSilhouetteRequestPacket::encode, SableSilhouetteRequestPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(controllerPos);
        buf.writeUUID(sublevelId);
        buf.writeVarInt(revision);
    }

    private static SableSilhouetteRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new SableSilhouetteRequestPacket(buf.readBlockPos(), buf.readUUID(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SableSilhouetteRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!Mods.SABLE.isLoaded() || !(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!(level.getBlockEntity(packet.controllerPos()) instanceof MonitorBlockEntity monitor)) {
                return;
            }
            MonitorBlockEntity controller = monitor.isController() ? monitor : monitor.getController();
            if (controller == null || !controller.isLinked()) {
                return;
            }

            boolean visibleTrack = false;
            for (RadarTrack track : controller.getTracks()) {
                if (packet.sublevelId().equals(track.getSilhouetteId())
                        && packet.revision() == track.getSilhouetteRevision()) {
                    visibleTrack = true;
                    break;
                }
            }
            if (!visibleTrack) {
                return;
            }

            SubLevelSilhouette silhouette = SableSilhouetteServerCache.getSilhouette(level, packet.sublevelId());
            int revision = SableSilhouetteServerCache.getRevision(level, packet.sublevelId());
            byte status = SableSilhouetteServerCache.getStatus(level, packet.sublevelId());
            if (silhouette == null || revision != packet.revision()) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new SableSilhouetteSyncPacket(packet.sublevelId(), revision, status, silhouette.localBoxes()));
        });
    }

    public static void send(BlockPos controllerPos, UUID sublevelId, int revision) {
        PacketDistributor.sendToServer(new SableSilhouetteRequestPacket(controllerPos, sublevelId, revision));
    }
}
