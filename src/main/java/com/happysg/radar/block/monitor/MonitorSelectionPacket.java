package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MonitorSelectionPacket extends SimplePacketBase {
    public static final Type<MonitorSelectionPacket> TYPE = new Type<>(CreateRadar.asResource("monitor_selection_packet"));
    public static final StreamCodec<FriendlyByteBuf, MonitorSelectionPacket> STREAM_CODEC = createCodec(MonitorSelectionPacket::new);

    private final BlockPos controllerPos;
    private final String selectedId;

    public MonitorSelectionPacket(BlockPos controllerPos, String selectedId) {
        this.controllerPos = controllerPos;
        this.selectedId = selectedId;
    }

    public static void send(BlockPos pos, String id) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload)new MonitorSelectionPacket(pos, id));
    }

    public MonitorSelectionPacket(FriendlyByteBuf buffer) {
        this.controllerPos = buffer.readBlockPos();
        this.selectedId = buffer.readBoolean() ? buffer.readUtf() : null;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(controllerPos);
        buffer.writeBoolean(selectedId != null);
        if (selectedId != null) {
            buffer.writeUtf(selectedId);
        }
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.level().getBlockEntity(controllerPos) instanceof MonitorBlockEntity be)) return;

            MonitorBlockEntity controller = be.isController() ? be : be.getController();
            if (controller == null || !controller.isLinked()) return;

            if (selectedId == null) {
                controller.activetrack = null;
                controller.selectedEntity = null;
                controller.setSelectedTargetServer(null);
                controller.notifyUpdate();
                return;
            }

            RadarTrack found = null;
            for (RadarTrack t : controller.cachedTracks) {
                if (selectedId.equals(t.id())) {
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

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}