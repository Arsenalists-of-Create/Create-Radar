package com.happysg.radar.networking.packets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.networkhandlers.ListNBTHandler;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SaveListsPacket extends SimplePacketBase {
    public static final Type<SaveListsPacket> TYPE = new Type<>(CreateRadar.asResource("save_lists_packet"));
    public static final StreamCodec<FriendlyByteBuf, SaveListsPacket> STREAM_CODEC = createCodec(SaveListsPacket::new);

    private final List<String> entries;
    private final String idString;
    private final boolean isIdString;

    public SaveListsPacket(List<String> entries) {
        this.entries = new ArrayList<>(entries);
        this.idString = null;
        this.isIdString = false;
    }

    public SaveListsPacket(String idString) {
        this.entries = Collections.emptyList();
        this.idString = idString;
        this.isIdString = true;
    }

    public SaveListsPacket(FriendlyByteBuf buffer) {
        this.isIdString = buffer.readBoolean();
        if (isIdString) {
            this.idString = buffer.readUtf(32767);
            this.entries = Collections.emptyList();
        } else {
            int size = buffer.readVarInt();
            this.entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) entries.add(buffer.readUtf(32767));
            this.idString = null;
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(isIdString);
        if (isIdString) {
            buffer.writeUtf(idString, 32767);
        } else {
            buffer.writeVarInt(entries.size());
            for (String s : entries) buffer.writeUtf(s, 32767);
        }
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (isIdString) {
                ListNBTHandler.saveStringToHeldItem(player, idString);
            } else {
                ListNBTHandler.saveToHeldItem(player, entries);
            }
            player.getInventory().setChanged();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}