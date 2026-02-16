package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.registry.AllDataBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadarLinkConfigurationPayload(BlockPos pos, CompoundTag configData) implements CustomPacketPayload {

    public static final Type<RadarLinkConfigurationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_link_configuration"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarLinkConfigurationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBlockPos(msg.pos);
                        buf.writeNbt(msg.configData);
                    },
                    buf -> new RadarLinkConfigurationPayload(
                            buf.readBlockPos(),
                            buf.readNbt() == null ? new CompoundTag() : buf.readNbt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RadarLinkConfigurationPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!(player.level() instanceof ServerLevel level)) return;
            var be = level.getBlockEntity(msg.pos);
            if (!(be instanceof DataLinkBlockEntity dl)) return;

            CompoundTag configData = msg.configData;
            if (configData == null || !configData.contains("Id")) {
                dl.notifyUpdate();
                return;
            }

            ResourceLocation id = ResourceLocation.parse(configData.getString("Id"));
            DataPeripheral source = AllDataBehaviors.getSource(id);
            if (source == null) {
                dl.notifyUpdate();
                return;
            }

            if (dl.activeSource == null || dl.activeSource != source) {
                dl.activeSource = source;
                dl.setSourceConfig(configData.copy());
            } else {
                dl.getSourceConfig().merge(configData);
            }

            dl.updateGatheredData();
            dl.notifyUpdate();
        });
    }
}
