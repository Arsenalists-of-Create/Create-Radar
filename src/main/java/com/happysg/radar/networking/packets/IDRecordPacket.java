package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record IDRecordPacket(String shipId, String shipSlug, String secretID, String newSlug) implements CustomPacketPayload {

    public IDRecordPacket {
        shipSlug = shipSlug == null ? "" : shipSlug;
        secretID = secretID == null ? "" : secretID;
        newSlug = newSlug == null ? "" : newSlug;
    }

    public static final Type<IDRecordPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipId,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipSlug,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::secretID,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::newSlug,
            IDRecordPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IDRecordPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            UUID sourceId = UUID.fromString(packet.shipId());
            IDManager.addIDRecord(sourceId, packet.secretID(), packet.newSlug());
            int attachedCount = applySecretIdToAttachedSublevels(sender, sourceId, packet.secretID());
            if (attachedCount > 0) {
                sender.displayClientMessage(Component.literal("Applied secret ID to " + attachedCount + " attached sublevel" + (attachedCount == 1 ? "." : "s.")), false);
            }
        });
    }

    public static void send(UUID shipId, String shipSlug, String secretID, String newSlug) {
        PacketDistributor.sendToServer(new IDRecordPacket(shipId.toString(), shipSlug, secretID, newSlug));
    }

    private static int applySecretIdToAttachedSublevels(ServerPlayer sender, UUID sourceId, String secretID) {
        SubLevelContainer container = SubLevelContainer.getContainer(sender.serverLevel());
        if (container == null) {
            return 0;
        }

        SubLevel source = container.getSubLevel(sourceId);
        if (source == null || source.boundingBox() == null) {
            return 0;
        }

        int applied = 0;
        for (SubLevel attached : SubLevelHelper.getConnectedChain(source)) {
            UUID attachedId = attached.getUniqueId();
            if (sourceId.equals(attachedId)) {
                continue;
            }

            IDManager.IDRecord existing = IDManager.getIDRecordByShipId(attachedId);
            String name = existing != null ? existing.name() : attached.getName();
            if (name == null || name.isBlank()) {
                name = attachedId.toString();
            }
            IDManager.addIDRecord(attachedId, secretID, name);
            applied++;
        }
        return applied;
    }
}
