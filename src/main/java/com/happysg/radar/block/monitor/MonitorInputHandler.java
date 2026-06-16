package com.happysg.radar.block.monitor;


import com.happysg.radar.block.radar.track.RadarTrack;
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
