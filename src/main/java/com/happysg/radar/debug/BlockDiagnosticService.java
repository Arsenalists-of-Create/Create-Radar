package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

public final class BlockDiagnosticService {
    public static final double MAX_INSPECTION_DISTANCE = 16.0D;

    private BlockDiagnosticService() {
    }

    public static BlockDiagnosticSnapshot inspect(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return BlockDiagnosticSnapshot.status(
                    BlockDiagnosticSnapshot.Status.DENIED,
                    "Permission level 2 is required");
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return BlockDiagnosticSnapshot.status(
                    BlockDiagnosticSnapshot.Status.ERROR,
                    "No server level is available");
        }
        HitResult hit = player.pick(MAX_INSPECTION_DISTANCE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return BlockDiagnosticSnapshot.status(
                    BlockDiagnosticSnapshot.Status.MISS,
                    "Look at a Create Radar block");
        }
        return inspect(player, level, blockHit.getBlockPos());
    }

    public static BlockDiagnosticSnapshot inspect(ServerPlayer player,
                                                   ServerLevel level,
                                                   BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return BlockDiagnosticSnapshot.status(
                    BlockDiagnosticSnapshot.Status.UNLOADED,
                    "The target chunk is not loaded");
        }
        BlockState state = level.getBlockState(position);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                state.getBlock());
        BlockEntity blockEntity = level.getBlockEntity(position);
        boolean ownedBlock = CreateRadar.MODID.equals(blockId.getNamespace());
        if (!ownedBlock && !(blockEntity instanceof DebugInspectable)) {
            return BlockDiagnosticSnapshot.status(
                    BlockDiagnosticSnapshot.Status.NOT_CREATE_RADAR,
                    "Look at a Create Radar block");
        }

        String blockEntityType = blockEntity == null ? "none" :
                String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(
                        blockEntity.getType()));
        DiagnosticSnapshotBuilder builder = new DiagnosticSnapshotBuilder(
                level, position, blockId, blockEntityType);
        builder.add("Block", "state", state)
                .add("Block", "block entity",
                        blockEntity == null ? "none"
                                : blockEntity.getClass().getName())
                .add("Block", "removed",
                        blockEntity != null && blockEntity.isRemoved())
                .add("Block", "game tick", level.getGameTime());

        DiagnosticContext context = new DiagnosticContext(level, player);
        try {
            if (blockEntity instanceof DebugInspectable inspectable) {
                inspectable.appendDebugInfo(builder, context);
            }
            appendBuiltInInfo(builder, level, position, blockEntity);
            appendRecentFailures(builder, level, position);
        } catch (RuntimeException | LinkageError failure) {
            DiagnosticRecorder.error("block_inspector", "collect",
                    "diagnostic_provider_failed", failure, level, position);
            builder.error("Inspector", "provider failure",
                    failure.getClass().getSimpleName() + ": " +
                            DiagnosticRecorder.sanitize(failure.getMessage(),
                                    120));
        }
        return builder.build();
    }

    private static void appendBuiltInInfo(DiagnosticSnapshotBuilder builder,
                                          ServerLevel level,
                                          BlockPos position,
                                          @Nullable BlockEntity blockEntity) {
        NetworkData networkData = NetworkData.get(level);
        if (blockEntity instanceof DataLinkBlockEntity link) {
            builder.add("Data link", "source", link.getSourcePosition())
                    .add("Data link", "target", link.getTargetPosition())
                    .add("Data link", "endpoint type",
                            link.getWeaponEndpointType())
                    .add("Data link", "source adapter",
                            link.activeSource == null ? "missing"
                                    : link.activeSource.id)
                    .add("Data link", "target adapter",
                            link.activeTarget == null ? "missing"
                                    : link.activeTarget.id)
                    .add("Network", "filterer",
                            valueOrMissing(networkData.getFiltererForDataLink(
                                    level.dimension(), position)));
        }
        if (blockEntity instanceof NetworkFiltererBlockEntity filterer) {
            NetworkData.Group group = networkData.getGroup(level.dimension(),
                    position);
            if (group == null) {
                builder.warn("Network", "group", "missing");
            } else {
                builder.add("Network", "monitors",
                                group.monitorEndpoints.size())
                        .add("Network", "radars",
                                group.radarEndpoints.size())
                        .add("Network", "weapon endpoints",
                                group.weaponEndpoints.size())
                        .add("Network", "weapon mounts",
                                group.usedWeaponMounts.size())
                        .add("Network", "data links",
                                group.dataLinks.size())
                        .add("Network", "selected target",
                                shortIdentifier(group.selectedTargetId));
            }
            builder.add("Filterer", "inventory slots",
                            filterer.getItemHandler().getSlots())
                    .add("Filterer", "active track",
                            filterer.activeTrackCache == null ? "none"
                                    : filterer.activeTrackCache.trackCategory()
                                    + ":" + shortIdentifier(
                                    filterer.activeTrackCache.id()));
        }
        if (blockEntity instanceof MonitorBlockEntity monitor) {
            builder.add("Monitor", "controller", monitor.getControllerPos())
                    .add("Monitor", "controller block", monitor.isController())
                    .add("Monitor", "size", monitor.getSize())
                    .add("Monitor", "linked", monitor.isLinked())
                    .add("Monitor", "ARAD linked", monitor.isAradLinked())
                    .add("Monitor", "radars",
                            monitor.getRadarInfos().size())
                    .add("Monitor", "tracks", monitor.getTracks().size())
                    .add("Monitor", "safe zones", monitor.safeZones.size())
                    .add("Monitor", "selected", shortIdentifier(
                            monitor.getSelectedEntity()));
        }
        if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
            builder.add("Pitch controller", "target",
                            pitch.getTargetAngle())
                    .add("Pitch controller", "requested target",
                            pitch.getRequestedTargetAngle())
                    .add("Pitch controller", "limits",
                            formatRange(pitch.getMinAngleDeg(),
                                    pitch.getMaxAngleDeg()))
                    .add("Pitch controller", "generated speed",
                            pitch.getGeneratedSpeed())
                    .add("Pitch controller", "available input",
                            pitch.getAvailableInputSpeed())
                    .add("Pitch controller", "assembled mount",
                            pitch.hasAssembledControlledMount())
                    .add("Pitch controller", "track",
                            pitch.track == null ? "none"
                                    : pitch.track.trackCategory() + ":" +
                                    shortIdentifier(pitch.track.id()));
            appendWeaponGroup(builder, level, position);
            if (pitch.firingControl != null) {
                pitch.firingControl.appendDiagnosticInfo(builder);
            } else {
                builder.warn("Weapon control", "instance", "not resolved");
            }
        } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
            builder.add("Yaw controller", "target", yaw.getTargetAngle())
                    .add("Yaw controller", "requested target",
                            yaw.getRequestedTargetAngle())
                    .add("Yaw controller", "limits",
                            formatRange(yaw.getMinAngleDeg(),
                                    yaw.getMaxAngleDeg()))
                    .add("Yaw controller", "generated speed",
                            yaw.getGeneratedSpeed())
                    .add("Yaw controller", "available input",
                            yaw.getAvailableInputSpeed())
                    .add("Yaw controller", "assembled mount",
                            yaw.hasAssembledControlledMount())
                    .add("Yaw controller", "upside down",
                            yaw.isUpsideDown());
            appendWeaponGroup(builder, level, position);
        }
        if (blockEntity instanceof FireControllerBlockEntity firing) {
            builder.add("Fire controller", "powered", firing.isPowered());
            appendWeaponGroup(builder, level, position);
        }
        if (blockEntity instanceof RadarWarningReceiverBlockEntity receiver) {
            builder.add("Radar warning receiver", "redstone signal",
                    receiver.getRedstoneSignal());
        }
        if (blockEntity != null
                && !(blockEntity instanceof DebugInspectable)
                && !(blockEntity instanceof DataLinkBlockEntity)
                && !(blockEntity instanceof NetworkFiltererBlockEntity)
                && !(blockEntity instanceof MonitorBlockEntity)
                && !(blockEntity instanceof AutoPitchControllerBlockEntity)
                && !(blockEntity instanceof AutoYawControllerBlockEntity)
                && !(blockEntity instanceof FireControllerBlockEntity)
                && !(blockEntity instanceof RadarWarningReceiverBlockEntity)) {
            builder.warn("Inspector", "specialized provider", "none");
        }
    }

    private static void appendWeaponGroup(DiagnosticSnapshotBuilder builder,
                                          ServerLevel level,
                                          BlockPos endpoint) {
        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
        WeaponNetworkRuntime.WeaponGroupView view =
                runtime.getWeaponGroupViewFromEndpoint(endpoint);
        if (view == null) {
            builder.warn("Weapon network", "group", "missing");
            return;
        }
        builder.add("Weapon network", "mount", view.mountPos())
                .add("Weapon network", "yaw", valueOrMissing(view.yawPos()))
                .add("Weapon network", "pitch",
                        valueOrMissing(view.pitchPos()))
                .add("Weapon network", "firing",
                        valueOrMissing(view.firingPos()))
                .add("Weapon network", "data links",
                        view.dataLinks().size())
                .add("Weapon network", "origin",
                        valueOrMissing(runtime.getEndpointOrigin(endpoint)));
    }

    private static void appendRecentFailures(
            DiagnosticSnapshotBuilder builder, ServerLevel level,
            BlockPos position) {
        String dimension = level.dimension().location().toString();
        String positionText = position.toShortString();
        List<DiagnosticEvent> matching = DiagnosticRecorder.snapshot().stream()
                .filter(event -> event.dimension().equals(dimension)
                        && event.position().equals(positionText)
                        && event.severity() != DiagnosticSeverity.INFO)
                .toList();
        int start = Math.max(0, matching.size() - 3);
        for (int index = start; index < matching.size(); index++) {
            DiagnosticEvent event = matching.get(index);
            builder.add("Recent failures", event.subsystem(),
                    event.reason() + " ×" + event.occurrences(),
                    event.severity());
        }
    }

    private static Object valueOrMissing(@Nullable Object value) {
        return value == null ? "missing" : value;
    }

    private static String formatRange(double minimum, double maximum) {
        return String.format(java.util.Locale.ROOT, "%.2f..%.2f",
                minimum, maximum);
    }

    private static String shortIdentifier(@Nullable String value) {
        if (value == null || value.isBlank()) return "none";
        return "id#" + Integer.toUnsignedString(value.hashCode(), 16);
    }
}
