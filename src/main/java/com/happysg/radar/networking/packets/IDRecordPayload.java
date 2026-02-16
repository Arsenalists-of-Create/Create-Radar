package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record IDRecordPayload(long shipId, String shipSlug, String secretID, String newSlug) implements CustomPacketPayload {
    public static final Type<IDRecordPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "id_record"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IDRecordPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeLong(msg.shipId);
                        buf.writeUtf(msg.shipSlug == null ? "" : msg.shipSlug, 32767);
                        buf.writeUtf(msg.secretID == null ? "" : msg.secretID, 32767);
                        buf.writeUtf(msg.newSlug == null ? "" : msg.newSlug, 32767);
                    },
                    buf -> new IDRecordPayload(
                            buf.readLong(),
                            buf.readUtf(32767),
                            buf.readUtf(32767),
                            buf.readUtf(32767)
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(IDRecordPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> IDManager.addIDRecord(msg.shipId, msg.secretID, msg.newSlug));
    }
}
