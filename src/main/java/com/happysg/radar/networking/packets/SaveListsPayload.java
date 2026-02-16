package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.networkhandlers.ListNBTHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SaveListsPayload(List<String> entries, String idString, boolean isIdString) implements CustomPacketPayload {

    public static final Type<SaveListsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "save_lists"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveListsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg.isIdString);
                        if (msg.isIdString) {
                            buf.writeUtf(msg.idString == null ? "" : msg.idString, 32767);
                        } else {
                            List<String> e = msg.entries == null ? Collections.emptyList() : msg.entries;
                            buf.writeVarInt(e.size());
                            for (String s : e) buf.writeUtf(s == null ? "" : s, 32767);
                        }
                    },
                    buf -> {
                        boolean isId = buf.readBoolean();
                        if (isId) return new SaveListsPayload(Collections.emptyList(), buf.readUtf(32767), true);

                        int es = buf.readVarInt();
                        List<String> entries = new ArrayList<>(es);
                        for (int i = 0; i < es; i++) entries.add(buf.readUtf(32767));
                        return new SaveListsPayload(entries, null, false);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveListsPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (pkt.isIdString) {
                ListNBTHandler.saveStringToHeldItem(player, pkt.idString);
            } else {
                ListNBTHandler.saveToHeldItem(player, pkt.entries);
            }
            player.getInventory().setChanged();
        });
    }
}
