package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.networking.ModMessages;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class IDRecordRequestPacket extends SimplePacketBase {
    public static final Type<IDRecordRequestPacket> TYPE = new Type<>(CreateRadar.asResource("id_record_request_packet"));
    public static final StreamCodec<FriendlyByteBuf, IDRecordRequestPacket> STREAM_CODEC = createCodec(IDRecordRequestPacket::new);

    private final long shipId;

    public IDRecordRequestPacket(long shipId) {
        this.shipId = shipId;
    }

    public IDRecordRequestPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ModMessages.sendToPlayer(new IDRecordSyncPacket(IDManager.getRecords()), player);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}