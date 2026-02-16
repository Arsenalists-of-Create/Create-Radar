package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MonitorSelectionPayload(BlockPos controllerPos, String selectedId) implements CustomPacketPayload {
    public static final Type<MonitorSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "monitor_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MonitorSelectionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBlockPos(msg.controllerPos);
                        buf.writeBoolean(msg.selectedId != null);
                        if (msg.selectedId != null)
                            buf.writeUtf(msg.selectedId, 32767);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String id = buf.readBoolean() ? buf.readUtf(32767) : null;
                        return new MonitorSelectionPayload(pos, id);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MonitorSelectionPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sp = (ServerPlayer) ctx.player();
            if (sp == null) return;

            if (!(sp.level().getBlockEntity(msg.controllerPos) instanceof MonitorBlockEntity be))
                return;

            MonitorBlockEntity controller = be.isController() ? be : be.getController();
            if (controller == null || !controller.isLinked())
                return;

            if (msg.selectedId == null) {
                controller.activetrack = null;
                controller.selectedEntity = null;
                controller.setSelectedTargetServer(null);
                controller.notifyUpdate();
                return;
            }

            RadarTrack found = null;
            for (RadarTrack t : controller.cachedTracks) {
                if (msg.selectedId.equals(t.id())) {
                    found = t;
                    break;
                }
            }

            if (found != null) {
                controller.selectedEntity = found.id();
                controller.setSelectedTargetServer(found);
                controller.notifyUpdate();
            }
        });
    }
}
