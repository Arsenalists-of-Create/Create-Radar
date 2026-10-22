package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.api.monitor.MonitorContactSelectionHandler;
import com.happysg.radar.api.monitor.MonitorContactSelectionRegistry;
import com.happysg.radar.api.monitor.MonitorContactSnapshot;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record MonitorSelectionPacket(BlockPos controllerPos, @Nullable String selectedId, @Nullable String sourceType) implements CustomPacketPayload {
    public static final Type<MonitorSelectionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "monitor_selection"));
    public static final StreamCodec<FriendlyByteBuf, MonitorSelectionPacket> STREAM_CODEC = StreamCodec.ofMember(MonitorSelectionPacket::encode, MonitorSelectionPacket::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(controllerPos);

        buf.writeBoolean(selectedId != null);
        if (selectedId != null) {
            buf.writeUtf(selectedId);
        }

        buf.writeBoolean(sourceType != null);
        if (sourceType != null) {
            buf.writeUtf(sourceType, 128);
        }
    }

    public MonitorSelectionPacket(BlockPos controllerPos, @Nullable String selectedId) {
        this(controllerPos, selectedId, null);
    }

    private static MonitorSelectionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String id = buf.readBoolean() ? buf.readUtf() : null;
        String sourceType = buf.readBoolean() ? buf.readUtf(128) : null;
        return new MonitorSelectionPacket(pos, id, sourceType);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MonitorSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }

            if (!(sp.level().getBlockEntity(packet.controllerPos()) instanceof MonitorBlockEntity be)) {
                return;
            }

            MonitorBlockEntity controller = be.isController() ? be : be.getController();
            if (controller == null || !controller.isLinked()) {
                return;
            }

            if (!controller.isController() || controller.isRemoved()) {
                return;
            }

            Vec3 monitorWorldPos = PhysicsHandler.getWorldVec(controller);
            if (monitorWorldPos == null
                    || !Double.isFinite(monitorWorldPos.x)
                    || !Double.isFinite(monitorWorldPos.y)
                    || !Double.isFinite(monitorWorldPos.z)
                    || sp.distanceToSqr(monitorWorldPos) > 16.0 * 16.0) {
                return;
            }

            if (packet.selectedId() == null) {
                controller.activetrack = null;
                controller.selectedEntity = null;
                controller.setSelectedTargetServer(null);
                controller.notifyUpdate();
                return;
            }

            String sourceType = packet.sourceType();

            if (sourceType != null && !"create_radar".equals(sourceType)) {
                if (sourceType.isBlank()
                        || packet.selectedId().isBlank()
                        || packet.selectedId().length() > 256
                        || !controller.isController()
                        || controller.isRemoved()
                        || controller.isAradLinked()
                        || controller.getRunningRadarSnapshots().isEmpty()) {
                    return;
                }

                MonitorContactSelectionHandler handler = MonitorContactSelectionRegistry.get(sourceType).orElse(null);
                if (handler == null) {
                    return;
                }

                MonitorContactSnapshot contact;

                try {
                    contact = handler.resolveServerContact(sp, controller.getBlockPos(), packet.selectedId());
                } catch (RuntimeException exception) {
                    MonitorAddonCallbackSafety.warnOnce("resolveServerContact", handler, exception);
                    return;
                }

                if (contact == null || !contact.selectable() || !packet.selectedId().equals(contact.id()) || !sourceType.equals(contact.sourceType())) {
                    return;
                }

                Vec3 position = contact.position();
                if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                    return;
                }

                try {
                    if (handler.onSelect(sp, controller.getBlockPos(), contact)) {
                        controller.notifyUpdate();
                    }
                } catch (RuntimeException exception) {
                    MonitorAddonCallbackSafety.warnOnce("packetOnSelect", handler, exception);
                }

                return;
            }

            RadarTrack found = null;
            for (RadarTrack track : controller.cachedTracks) {
                if (packet.selectedId().equals(track.id())) {
                    found = track;
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

    public static void send(BlockPos controllerPos, @Nullable String selectedId) {
        PacketDistributor.sendToServer(new MonitorSelectionPacket(controllerPos, selectedId));
    }

    public static void send(BlockPos controllerPos, String selectedId, String sourceType) {
        if (selectedId == null
                || sourceType == null
                || selectedId.isBlank()
                || sourceType.isBlank()
                || selectedId.length() > 256
                || sourceType.length() > 128) {
            return;
        }

        PacketDistributor.sendToServer(new MonitorSelectionPacket(controllerPos, selectedId, sourceType));
    }
}