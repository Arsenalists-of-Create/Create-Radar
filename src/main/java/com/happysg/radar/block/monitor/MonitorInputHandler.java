package com.happysg.radar.block.monitor;


import com.happysg.radar.api.monitor.*;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MonitorInputHandler {
    private static @Nullable MonitorBlockEntity lastHoveredAradMonitor;
    private static @Nullable Level lastHoveredAradLevel;

    static Vec3 adjustRelativeVectorForFacing(Vec3 relative, Direction monitorFacing) {
        return switch (monitorFacing) {
            case NORTH -> new Vec3( relative.x(), 0,  relative.y());
            case SOUTH -> new Vec3(relative.x(), 0,  -relative.y());
            case WEST -> new Vec3(relative.y(), 0, relative.z());
            case EAST -> new Vec3(-relative.y(), 0, relative.z());
            default    -> relative;
        };
    }

    public static RadarTrack findTrack(Level level, BlockHitResult hit, MonitorBlockEntity controller) {

        if (controller.getRunningRadarInfos().isEmpty()) return null;
        MonitorProjection.DisplayPoint hitPoint = AradMonitorGeometry.hitPoint(level, controller, hit);
        if (hitPoint == null) return null;
        MonitorProjection projection = MonitorProjection.create(controller);
        double bestDistance = 0.04;
        RadarTrack bestTrack = null;
        for (RadarTrack track : controller.cachedTracks) {
            MonitorProjection.DisplayPoint point = projection.project(track.position());
            if (point.outside()) continue;
            double dx = point.xOffset() - hitPoint.xOffset();
            double dz = point.zOffset() - hitPoint.zOffset();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTrack = track;
            }
        }
        return bestTrack;
    }

    public static @Nullable MonitorContactSnapshot findCustomContact(Level level, BlockHitResult hit, MonitorBlockEntity controller) {
        List<MonitorRadarSnapshot> radars = controller.getRunningRadarSnapshots();
        if (radars.isEmpty()) {
            return null;
        }

        MonitorProjection.DisplayPoint hitPoint = AradMonitorGeometry.hitPoint(level, controller, hit);
        if (hitPoint == null) {
            return null;
        }

        Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);

        double displayXOffset = hitPoint.xOffset();
        double displayZOffset = hitPoint.zOffset();

        double normalizedX = displayXOffset * 2.0;
        double normalizedZ = displayZOffset * 2.0;

        Set<ResourceLocation> visitedTypes = new HashSet<>();

        for (MonitorRadarSnapshot radar : radars) {
            ResourceLocation typeId = radar.displayProfile().typeId();

            if (!visitedTypes.add(typeId)) {
                continue;
            }

            MonitorWorldHitTestHandler handler = MonitorWorldHitTestRegistry.get(typeId).orElse(null);
            if (handler == null) {
                continue;
            }

            MonitorExtensionState extension = radar.extensionState();

            int width = Math.max(1, extension.monitorWidth());
            int height = Math.max(1, extension.monitorHeight());

            MonitorWorldHitTestContext context =
                    new MonitorWorldHitTestContext(
                            level,
                            controller.getControllerPos(),
                            monitorFacing,
                            hit.getLocation(),
                            normalizedX,
                            normalizedZ,
                            displayXOffset,
                            displayZOffset,
                            width,
                            height,
                            radars,
                            controller.getMonitorContacts()
                    );

            MonitorContactSnapshot contact;

            try {
                contact = handler.findContact(context);
            } catch (RuntimeException exception) {
                MonitorAddonCallbackSafety.warnOnce("findContact", handler, exception);
                continue;
            }

            if (contact == null || !contact.selectable()) {
                continue;
            }

            Vec3 position = contact.position();

            if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                continue;
            }

            return contact;
        }

        return null;
    }

    private static @Nullable RadarTrack findTrackByContactId(MonitorBlockEntity controller, String contactId) {
        if (contactId == null || contactId.isBlank()) {
            return null;
        }

        for (RadarTrack track : controller.cachedTracks) {
            if (track == null) {
                continue;
            }

            if (contactId.equals(track.id()) || contactId.equals(track.getId())) {
                return track;
            }
        }

        return null;
    }

    public static @Nullable MonitorBlockEntity.RwrDisplayInfo findRwrContact(Level level, BlockHitResult hit, MonitorBlockEntity controller) {
        MonitorProjection.DisplayPoint hitPoint = AradMonitorGeometry.hitPoint(level, controller, hit);
        if (hitPoint == null) {
            return null;
        }

        long gameTime = level.getGameTime();
        double bestDistanceSqr = AradMonitorGeometry.PICK_RADIUS * AradMonitorGeometry.PICK_RADIUS;
        MonitorBlockEntity.RwrDisplayInfo bestContact = null;
        for (MonitorBlockEntity.RwrDisplayInfo contact : controller.getRwrInfos()) {
            if (!MonitorBlockEntity.shouldRenderRwrContact(contact, gameTime)) {
                continue;
            }
            MonitorProjection.DisplayPoint point = AradMonitorGeometry.point(controller, contact);
            double dx = point.xOffset() - hitPoint.xOffset();
            double dz = point.zOffset() - hitPoint.zOffset();
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                bestContact = contact;
            }
        }
        return bestContact;
    }

    public static void monitorPlayerHovering(PlayerTickEvent.Post event) {

        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide() || !player.isLocalPlayer())
            return;
        if (lastHoveredAradLevel != level) {
            lastHoveredAradMonitor = null;
            lastHoveredAradLevel = level;
        }
        var picked = player.pick(5, 0.0F, false);
        Vec3 hit = picked.getLocation();
        MonitorBlockEntity hoveredAradMonitor = null;
        String hoveredAradSource = null;
        if (picked instanceof BlockHitResult result) {
            if (level.getBlockEntity(result.getBlockPos()) instanceof MonitorBlockEntity be && level.getBlockEntity(be.getControllerPos()) instanceof MonitorBlockEntity monitor) {
                if (monitor.isAradLinked()) {
                    MonitorBlockEntity.RwrDisplayInfo contact = findRwrContact(level, result, monitor);
                    hoveredAradMonitor = monitor;
                    hoveredAradSource = contact == null ? null : contact.sourceId();
                } else {
                    MonitorContactSnapshot customContact = findCustomContact(level, result, monitor);
                    RadarTrack track = customContact == null ? findTrack(level, result, monitor) : null;

                    String oldHovered = monitor.hoveredEntity;
                    String newHovered = customContact != null ? customContact.id() : track != null ? track.id() : null;

                    if ((oldHovered == null && newHovered != null) || (oldHovered != null && !oldHovered.equals(newHovered))) {
                        monitor.hoveredEntity = newHovered;
                        monitor.notifyUpdate();
                    }
                }
            }
        }

        if (lastHoveredAradMonitor != null && lastHoveredAradMonitor != hoveredAradMonitor) {
            lastHoveredAradMonitor.setHoveredRwrSource(null);
        }
        if (hoveredAradMonitor != null) {
            hoveredAradMonitor.setHoveredRwrSource(hoveredAradSource);
        }
        lastHoveredAradMonitor = hoveredAradMonitor;

    }

    public static InteractionResult onUse(MonitorBlockEntity be, Player pPlayer, InteractionHand pHand, BlockHitResult pHit, Direction facing) {
        MonitorBlockEntity controller = be.getController();

        if (controller == null || !controller.isLinked()) {
            return InteractionResult.FAIL;
        }

        if (controller.isAradLinked()) {
            if (controller.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                if (pPlayer.isShiftKeyDown()) {
                    ARADTargetDesignationHandler.clearFromPlayer(serverLevel, controller);
                } else {
                    MonitorBlockEntity.RwrDisplayInfo contact = findRwrContact(serverLevel, pHit, controller);

                    if (contact == null || contact.sourceId().equals(controller.getSelectedRwrSource())) {
                        ARADTargetDesignationHandler.clearFromPlayer(serverLevel, controller);
                    } else {
                        ARADTargetDesignationHandler.assign(serverLevel, controller, contact.sourceId());
                    }
                }
            }

            return InteractionResult.SUCCESS;
        }

        if (!pPlayer.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Vec3 hit = pHit.getLocation();
        MonitorContactSnapshot customContact = findCustomContact(pPlayer.level(), pHit, controller);
        if (customContact != null) {
            String id = customContact.id();

            if (id.equals(controller.selectedEntity)) {
                MonitorSelectionPacket.send(controller.getControllerPos(), null);
            } else if ("create_radar".equals(customContact.sourceType())) {
                MonitorSelectionPacket.send(controller.getControllerPos(), id);
            } else {
                MonitorSelectionPacket.send(controller.getControllerPos(), id, customContact.sourceType());
            }

            return InteractionResult.SUCCESS;
        }

        RadarTrack track = findTrack(pPlayer.level(), pHit, controller);

        if (track == null || track.id().equals(controller.selectedEntity)) {
            MonitorSelectionPacket.send(controller.getControllerPos(), null);
        } else {
            MonitorSelectionPacket.send(controller.getControllerPos(), track.id());
        }

        return InteractionResult.SUCCESS;
    }
}
