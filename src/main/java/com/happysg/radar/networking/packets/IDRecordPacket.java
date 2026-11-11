package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableIdRecordAccess;
import com.happysg.radar.compat.vs2.SableUtils;
import net.minecraft.core.BlockPos;
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

public record IDRecordPacket(String shipId, String shipSlug, String secretID, String newSlug, long transponderPos) implements CustomPacketPayload {
    public IDRecordPacket {
        shipSlug = shipSlug == null ? "" : shipSlug;
        secretID = secretID == null ? "" : secretID;
        newSlug = newSlug == null ? "" : newSlug;
    }

    public static final Type<IDRecordPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipId,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::shipSlug,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::secretID,
            ByteBufCodecs.STRING_UTF8,
            IDRecordPacket::newSlug,
            ByteBufCodecs.VAR_LONG,
            IDRecordPacket::transponderPos,
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

            UUID sourceId;
            try {
                sourceId = UUID.fromString(packet.shipId());
            } catch (IllegalArgumentException exception) {
                return;
            }

            BlockPos pos = BlockPos.of(packet.transponderPos());
            var level = sender.serverLevel();
            if (!level.hasChunkAt(pos)) {
                return;
            }

            if (!(level.getBlockState(pos).getBlock() instanceof com.happysg.radar.block.controller.id.IdentificationTransponder)) {
                return;
            }

            var containing = SableUtils.getShipManagingPos(level, pos);
            if (containing == null || !sourceId.equals(containing.getUniqueId())) {
                return;
            }

            if (sender.distanceToSqr(SableUtils.getWorldVec(level, pos)) > 64.0 * 64.0) {
                return;
            }

            IDManager.registerTransponder(sourceId, pos, packet.newSlug(), packet.secretID());
            IDManager.addIDRecord(sourceId, packet.secretID(), packet.newSlug());

            if (Mods.SABLE.isLoaded()) {
                int attachedCount = SableIdRecordAccess.applySecretIdToAttachedSublevels(sender, sourceId, packet.secretID());
                if (attachedCount > 0) {
                    sender.displayClientMessage(
                            Component.literal("Applied secret ID to " + attachedCount + " attached sublevel" + (attachedCount == 1 ? "." : "s.")),
                            false
                    );
                }
            }
        });
    }

    public static void send(UUID shipId, String shipSlug, String secretID, String newSlug, BlockPos transponderPos) {
        PacketDistributor.sendToServer(new IDRecordPacket(shipId.toString(), shipSlug, secretID, newSlug, transponderPos.asLong()));
    }
}
