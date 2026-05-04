package com.happysg.radar.block.monitor;


import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3d;

public class MonitorInputHandler {

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
        if (controller.getRadarCenterPos() == null)
            return null;
        SubLevelAccess ship = null;
        if(Mods.SABLE.isLoaded()){
            ship = controller.getShip();
        }
        if (ship != null) {
            // Work in ship-local coordinates when the monitor is ship-managed.
            hit = PhysicsHandler.getShipVec(hit, controller);
        }


        Direction facing = level.getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING).getClockWise();
        Direction monitorFacing = level.getBlockState(controller.getControllerPos())
                .getValue(MonitorBlock.FACING);

        int size = controller.getSize();
        Vec3 center = Vec3.atCenterOf(controller.getControllerPos())
                .add(facing.getStepX() * (size - 1) / 2.0, (size - 1) / 2.0, facing.getStepZ() * (size - 1) / 2.0);

        Vec3 relative = hit.subtract(center);
        relative = adjustRelativeVectorForFacing(relative, monitorFacing);

        Vec3 RadarPos = controller.getRadarCenterPos();
        if (ship != null) {
            RadarPos = PhysicsHandler.getShipVec(RadarPos, controller);
        }
        float range = controller.getRange();
        float sizeadj = size == 1 ? 0.5f : ((size - 1) / 2f);
        if (size == 2)
            sizeadj = 0.75f;
        Vec3 selectedRelative = relative.scale(range / sizeadj);
        // Renderer rotates track vectors into a ship-relative frame when enabled; invert that here.
        var radarOpt = controller.getRadar();
        if (radarOpt.isPresent() && radarOpt.get().renderRelativeToMonitor()) {
            if (ship != null) {
                selectedRelative = rotateAroundY(selectedRelative, getShipYawRad(ship) + Math.PI);
            }
        }
        Vec3 selected = RadarPos.add(selectedRelative);

        double bestDistance = 0.1f * range;
        RadarTrack bestTrack = null;
        for (RadarTrack track : controller.cachedTracks) {
            Vec3 trackPos = track.position();
            if (ship != null) {
                trackPos = PhysicsHandler.getShipVec(trackPos, controller);
            }
            double distance = trackPos.distanceTo(selected);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTrack = track;
            }
        }
        return bestTrack;
    }

    private static Vec3 rotateAroundY(Vec3 v, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    private static double getShipYawRad(SubLevelAccess ship) {
        Vector3d fwd = ship.logicalPose().transformNormal(new Vector3d(0, 0, 1));
        return Math.atan2(fwd.x(), -fwd.z());
    }

    public static void monitorPlayerHovering(PlayerTickEvent.Post event) {

        Player player = event.getEntity();
        Level level = player.level();
        if (!level.isClientSide())
            return;
        Vec3 hit = player.pick(5, 0.0F, false).getLocation();
        if (player.pick(5, 0.0F, false) instanceof BlockHitResult result) {
            if (level.getBlockEntity(result.getBlockPos()) instanceof MonitorBlockEntity be && level.getBlockEntity(be.getControllerPos()) instanceof MonitorBlockEntity monitor) {
                RadarTrack track = findTrack(level, hit, monitor);
                String oldHovered = monitor.hoveredEntity;
                String newHovered = (track != null) ? track.id() : null;

                if ((oldHovered == null && newHovered != null) ||
                        (oldHovered != null && !oldHovered.equals(newHovered))) {

                    monitor.hoveredEntity = newHovered;
                    monitor.notifyUpdate();
                }

            }
        }

    }

    public static InteractionResult onUse(MonitorBlockEntity be, Player pPlayer, InteractionHand pHand, BlockHitResult pHit, Direction facing) {
        if (!be.getController().isLinked())
            return InteractionResult.FAIL;


        if (pPlayer.isShiftKeyDown()) {
            be.setSelectedTargetServer(null);
            be.notifyUpdate();
        } else {
            Vec3 hit = pHit.getLocation();
            var pick = pPlayer.pick(5, 0.0F, false);
            if (pick instanceof BlockHitResult pickHit) {
                hit = pickHit.getLocation();
            }
            RadarTrack track = findTrack(be.getLevel(), hit, be.getController());
            if (track != null) {
                be.selectedEntity = track.id();
                be.setSelectedTargetServer(track);
                be.notifyUpdate();
            }
        }
        return InteractionResult.SUCCESS;
    }

}
