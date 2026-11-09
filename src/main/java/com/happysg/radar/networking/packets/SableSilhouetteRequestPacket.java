package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableAssemblyRegistry;
import com.happysg.radar.compat.sable.SableSilhouetteServerCache;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
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

            SableSilhouetteServerCache.Snapshot snapshot = SableSilhouetteServerCache.getSnapshot(level, packet.sublevelId());
            SubLevelSilhouette silhouette = snapshot.silhouette();
            int revision = snapshot.revision();
            byte status = snapshot.status();

            if (silhouette == null || !SableSilhouetteStatus.drawable(status)) {
                return;
            }

            PacketDistributor.sendToPlayer(player, new SableSilhouetteSyncPacket(packet.sublevelId(), revision, status, silhouette.localBoxes(), SableAssemblyRegistry.get(level).groupOf(packet.sublevelId())));

            int sentMembers = 0;
            int sentBoxes = silhouette.localBoxes().size();

            for (var member : SableSilhouetteServerCache.getConnectedSilhouettes(level, packet.sublevelId())) {
                if (member.sublevelId().equals(packet.sublevelId())) {
                    continue;
                }

                int boxCount = member.silhouette().localBoxes().size();
                if (sentMembers >= 32 || sentBoxes + boxCount > 16384) {
                    break;
                }

                PacketDistributor.sendToPlayer(player, new SableSilhouetteSyncPacket(member.sublevelId(), member.revision(), member.status(), member.silhouette().localBoxes(), SableAssemblyRegistry.get(level).groupOf(packet.sublevelId())));

                sentMembers++;
                sentBoxes += boxCount;
            }
        });
    }

    public static void send(BlockPos controllerPos, UUID sublevelId, int revision) {
        PacketDistributor.sendToServer(new SableSilhouetteRequestPacket(controllerPos, sublevelId, revision));
    }
}
