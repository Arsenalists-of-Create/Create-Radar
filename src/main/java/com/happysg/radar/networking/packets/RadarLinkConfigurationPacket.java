package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RadarLinkConfigurationPacket extends SimplePacketBase {

    public static final Type<RadarLinkConfigurationPacket> TYPE = new Type<>(CreateRadar.asResource("radar_link_config"));
    public static final StreamCodec<FriendlyByteBuf, RadarLinkConfigurationPacket> STREAM_CODEC = createCodec(RadarLinkConfigurationPacket::new);

    private final BlockPos pos;
    private final CompoundTag configData;

    public RadarLinkConfigurationPacket(BlockPos pos, CompoundTag configData) {
        this.pos = pos;
        this.configData = configData;
    }

    public RadarLinkConfigurationPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.configData = buffer.readNbt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeNbt(configData);
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) context.player().level();
            if (level.getBlockEntity(pos) instanceof DataLinkBlockEntity be) {
                applySettings(be);
            }
        });
    }

    protected void applySettings(DataLinkBlockEntity be) {
        if (!configData.contains("Id")) {
            be.notifyUpdate();
            return;
        }

        ResourceLocation id = ResourceLocation.parse(configData.getString("Id"));
        DataPeripheral source = AllDataBehaviors.getSource(id);
        if (source == null) {
            be.notifyUpdate();
            return;
        }

        if (be.activeSource == null || be.activeSource != source) {
            be.activeSource = source;
            be.setSourceConfig(configData.copy());
        } else {
            be.getSourceConfig().merge(configData);
        }

        be.updateGatheredData();
        be.notifyUpdate();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}