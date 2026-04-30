package com.happysg.radar.networking.packets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.id.IDRecord;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class IDRecordSyncPacket extends SimplePacketBase {
    public static final Type<IDRecordSyncPacket> TYPE = new Type<>(CreateRadar.asResource("id_record_sync_packet"));
    public static final StreamCodec<FriendlyByteBuf, IDRecordSyncPacket> STREAM_CODEC = createCodec(IDRecordSyncPacket::new);

    private final List<IDRecord> records;

    public IDRecordSyncPacket(List<IDRecord> records) {
        this.records = records;
    }

    public IDRecordSyncPacket(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        this.records = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            records.add(new IDRecord(buffer.readLong(), buffer.readUtf(), buffer.readUtf()));
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(records.size());
        for (IDRecord record : records) {
            buffer.writeLong(record.shipId());
            buffer.writeUtf(record.secretID());
            buffer.writeUtf(record.shipSlug());
        }
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            IDManager.setRecords(records);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}