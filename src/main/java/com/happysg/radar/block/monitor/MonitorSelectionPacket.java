package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.networking.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MonitorSelectionPacket {
    private final BlockPos controllerPos;
    private final String selectedId;

    public MonitorSelectionPacket(BlockPos controllerPos, String selectedId) {
        this.controllerPos = controllerPos;
        this.selectedId = selectedId;
    }

    public static void encode(MonitorSelectionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.controllerPos);
        buf.writeBoolean(msg.selectedId != null);
        if (msg.selectedId != null) {
            buf.writeUtf(msg.selectedId, 256);
        }
    }

    public static MonitorSelectionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String id = buf.readBoolean() ? buf.readUtf(256) : null;
        return new MonitorSelectionPacket(pos, id);
    }

    public static void handle(MonitorSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.controllerPos == null || !player.serverLevel().hasChunkAt(msg.controllerPos)) return;
            if (!(player.serverLevel().getBlockEntity(msg.controllerPos) instanceof MonitorBlockEntity monitor)) return;

            MonitorBlockEntity controller = monitor.isController() ? monitor : monitor.getController();
            if (controller == null || controller.isRemoved() || !controller.isController() || !controller.isLinked()) return;

            Vec3 worldPos = PhysicsHandler.getWorldVec(controller);
            if (worldPos == null || !Double.isFinite(worldPos.x) || !Double.isFinite(worldPos.y)
                    || !Double.isFinite(worldPos.z) || player.distanceToSqr(worldPos) > 256.0) return;

            if (msg.selectedId == null) {
                controller.activetrack = null;
                controller.selectedEntity = null;
                controller.setSelectedTargetServer(null);
                controller.notifyUpdate();
                return;
            }

            if (msg.selectedId.isBlank() || msg.selectedId.length() > 256) return;
            controller.updateCacheServerOrClient();
            for (RadarTrack track : controller.getTracks()) {
                if (track != null && msg.selectedId.equals(track.getId())) {
                    controller.setSelectedTargetServer(track);
                    controller.notifyUpdate();
                    return;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(BlockPos controllerPos, String selectedId) {
        NetworkHandler.CHANNEL.sendToServer(new MonitorSelectionPacket(controllerPos, selectedId));
    }
}
