package com.happysg.radar.block.monitor;


import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
        float range = controller.getRange();
        float sizeadj = size == 1 ? 0.5f : ((size - 1) / 2f);
        if (size == 2)
            sizeadj = 0.75f;
        Vec3 selected = RadarPos.add(relative.scale(range / (sizeadj)));

        double bestDistance = 0.1f * range;
        RadarTrack bestTrack = null;
        for (RadarTrack track : controller.cachedTracks) {
            double distance = track.position().distanceTo(selected);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTrack = track;
            }
        }
        return bestTrack;
    }

    public static void monitorPlayerHovering(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();

        if (!level.isClientSide())
            return;
        if (player != Minecraft.getInstance().player)
            return;

        Vec3 hit = player.pick(5, 0.0F, false).getLocation();
        if (player.pick(5, 0.0F, false) instanceof BlockHitResult result) {
            if (level.getBlockEntity(result.getBlockPos()) instanceof MonitorBlockEntity be && level.getBlockEntity(be.getControllerPos()) instanceof MonitorBlockEntity monitor) {
                hit = PhysicsHandler.getShipVec(hit, be);
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
            RadarTrack track = findTrack(be.getLevel(), pHit.getLocation(), be.getController());
            if (track != null) {
                be.selectedEntity = track.id();
                be.setSelectedTargetServer(track);
                be.notifyUpdate();
            }
        }
        return InteractionResult.SUCCESS;
    }

}
