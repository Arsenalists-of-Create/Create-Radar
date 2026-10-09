package com.happysg.radar.block.monitor;


import com.happysg.radar.api.monitor.*;
import com.happysg.radar.block.radar.track.RadarTrack;
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

    public static RadarTrack findTrack(Level level, Vec3 hit, MonitorBlockEntity controller) {
        if (controller.getRunningRadarInfos().isEmpty())
            return null;

        MonitorProjection projection = MonitorProjection.create(controller);

        Direction facing = level.getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING).getClockWise();
        Direction monitorFacing = level.getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING);

        int size = controller.getSize();
        Vec3 center = Vec3.atCenterOf(controller.getControllerPos())
                .add(facing.getStepX() * (size - 1) / 2.0, (size - 1) / 2.0, facing.getStepZ() * (size - 1) / 2.0);

        Vec3 relative = hit.subtract(center);
        relative = adjustRelativeVectorForFacing(relative, monitorFacing);

        float sizeadj = size == 1 ? 0.5f : ((size - 1) / 2f);
        if (size == 2)
            sizeadj = 0.75f;

        float hitX = (float) (relative.x / sizeadj) * 0.5f;
        float hitZ = (float) (relative.z / sizeadj) * 0.5f;
        double bestDistance = 0.04f;
        RadarTrack bestTrack = null;
        for (RadarTrack track : controller.cachedTracks) {
            MonitorProjection.DisplayPoint point = projection.project(track.position());
            if (point.outside())
                continue;

            double dx = point.xOffset() - hitX;
            double dz = point.zOffset() - hitZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTrack = track;
            }
        }
        return bestTrack;
    }

    public static @Nullable MonitorContactSnapshot findCustomContact(Level level, Vec3 hit, MonitorBlockEntity controller) {
        List<MonitorRadarSnapshot> radars = controller.getRunningRadarSnapshots();

        if (radars.isEmpty()) {
            return null;
        }

        Direction widthDirection = level
                .getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING)
                .getClockWise();

        Direction monitorFacing = level
                .getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING);

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

            Vec3 center = Vec3.atCenterOf(controller.getControllerPos()).add(
                    widthDirection.getStepX() * (width - 1) / 2.0,
                    (height - 1) / 2.0,
                    widthDirection.getStepZ() * (width - 1) / 2.0
            );

            Vec3 relative = adjustRelativeVectorForFacing(hit.subtract(center), monitorFacing);

            double widthAdj = dimensionAdjustment(width);
            double heightAdj = dimensionAdjustment(height);

            double normalizedX = relative.x / widthAdj;
            double normalizedZ = relative.z / heightAdj;

            double displayXOffset = normalizedX * 0.5;
            double displayZOffset = normalizedZ * 0.5;

            MonitorWorldHitTestContext context = new MonitorWorldHitTestContext(
                    level,
                    controller.getControllerPos(),
                    monitorFacing,
                    hit,

                    normalizedX,
                    normalizedZ,

                    displayXOffset,
                    displayZOffset,

                    width,
                    height,

                    radars,
                    controller.getMonitorContacts()
            );

            MonitorContactSnapshot contact = handler.findContact(context);

            if (contact != null && contact.selectable()) {
                return contact;
            }
        }

        return null;
    }

    private static double dimensionAdjustment(int dimension) {
        if (dimension <= 1) {
            return 0.5;
        }

        if (dimension == 2) {
            return 0.75;
        }

        return (dimension - 1) / 2.0;
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
                    MonitorContactSnapshot customContact = findCustomContact(level, hit, monitor);
                    RadarTrack track = customContact == null ? findTrack(level, hit, monitor) : null;

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
        if (!controller.isLinked())
            return InteractionResult.FAIL;

        if (controller.isAradLinked()) {
            if (controller.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                if (pPlayer.isShiftKeyDown()) {
                    ARADTargetDesignationHandler.clearFromPlayer(serverLevel, controller);
                } else {
                    MonitorBlockEntity.RwrDisplayInfo contact = findRwrContact(serverLevel, pHit, controller);
                    if (contact != null) {
                        ARADTargetDesignationHandler.assign(serverLevel, controller, contact.sourceId());
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }


        if (pPlayer.isShiftKeyDown()) {
            be.setSelectedTargetServer(null);
            be.notifyUpdate();
        } else {
            Vec3 hit = pHit.getLocation();
            var pick = pPlayer.pick(5, 0.0F, false);

            if (pick instanceof BlockHitResult pickHit) {
                hit = pickHit.getLocation();
            }

            MonitorContactSnapshot customContact = findCustomContact(be.getLevel(), hit, controller);

            if (customContact != null) {
                RadarTrack nativeTrack = findTrackByContactId(controller, customContact.id());

                if (nativeTrack != null) {
                    be.selectedEntity = nativeTrack.id();
                    be.setSelectedTargetServer(nativeTrack);
                    be.notifyUpdate();
                    return InteractionResult.SUCCESS;
                }

                MonitorContactSelectionHandler handler = MonitorContactSelectionRegistry.get(customContact.sourceType()).orElse(null);

                if (handler != null && handler.onSelect(pPlayer, controller.getControllerPos(), customContact)) {
                    be.notifyUpdate();
                    return InteractionResult.SUCCESS;
                }

                return InteractionResult.SUCCESS;
            }

            RadarTrack track = findTrack(be.getLevel(), hit, controller);

            if (track != null) {
                be.selectedEntity = track.id();
                be.setSelectedTargetServer(track);
                be.notifyUpdate();
            }
        }
        return InteractionResult.SUCCESS;
    }
}
