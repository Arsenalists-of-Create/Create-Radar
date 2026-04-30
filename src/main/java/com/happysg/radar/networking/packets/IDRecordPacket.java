package com.happysg.radar.networking.packets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class IDRecordPacket extends SimplePacketBase {
    public static final Type<IDRecordPacket> TYPE = new Type<>(CreateRadar.asResource("id_record_packet"));
    public static final StreamCodec<FriendlyByteBuf, IDRecordPacket> STREAM_CODEC = createCodec(IDRecordPacket::new);

    long shipId;
    String shipSlug;
    String secretID;
    String newSlug;

    public IDRecordPacket(long shipId, String shipSlug, String secretID, String newName) {
        this.shipId = shipId;
        this.shipSlug = shipSlug == null ? "" : shipSlug;
        this.secretID = secretID == null ? "" : secretID;
        this.newSlug = newName == null ? "" : newName;
    }

    public IDRecordPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
        this.shipSlug = buffer.readUtf(32767);
        this.secretID = buffer.readUtf(32767);
        this.newSlug = buffer.readUtf(32767);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
        buffer.writeUtf(shipSlug, 32767);
        buffer.writeUtf(secretID, 32767);
        buffer.writeUtf(newSlug, 32767);
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            IDManager.addIDRecord(shipId, secretID, newSlug);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}