package com.happysg.radar.block.monitor;


import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarTrack;
import com.happysg.radar.block.radar.bearing.VSRadarTracks;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class MonitorInputHandler {


    public static void monitorPlayerHovering(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = event.player.level();
        Vec3 hit = player.pick(5, 0.0F, false).getLocation();
        if (player.pick(5, 0.0F, false) instanceof BlockHitResult result) {
            if (level.getBlockEntity(result.getBlockPos()) instanceof MonitorBlockEntity be && level.getBlockEntity(be.getControllerPos()) instanceof MonitorBlockEntity monitor) {
                Direction facing = level.getBlockState(monitor.getControllerPos())
                        .getValue(MonitorBlock.FACING).getClockWise();
                Direction monitorFacing = level.getBlockState(monitor.getControllerPos())
                        .getValue(MonitorBlock.FACING);
                int size = monitor.getSize();
                Vec3 preCenter;
                if (VSGameUtilsKt.isBlockInShipyard(level, monitor.getControllerPos())) {
                    preCenter = VSGameUtilsKt.toWorldCoordinates(level, monitor.getControllerPos().getCenter());
                }
                else {
                    preCenter = Vec3.atCenterOf(monitor.getControllerPos());
                }
                Vec3 center = preCenter.add(facing.getStepX() * (size - 1) / 2.0, (size - 1) / 2.0, facing.getStepZ() * (size - 1) / 2.0);
                Vec3 relative = hit.subtract(center);
                relative = monitor.adjustRelativeVectorForFacing(relative, monitorFacing);
                if (monitor.radarPos == null)
                    return;
                Vec3 RadarPos = monitor.radarPos.getCenter();
                float range = monitor.getRadar().map(RadarBearingBlockEntity::getRange).orElse(0f);
                float sizeadj = size == 1 ? 0.5f : ((size - 1) / 2f);
                if (size == 2)
                    sizeadj = 0.75f;
                Vec3 selected = RadarPos.add(relative.scale(range / (sizeadj)));

                monitor.getRadar().ifPresent(radar -> {
                    double bestDistance = 0.1f * range;
                    for (RadarTrack track : radar.getEntityPositions()) {
                        Vec3 entityPos = track.position();
                        entityPos = entityPos.multiply(1, 0, 1);
                        Vec3 selectedNew = selected.multiply(1, 0, 1);
                        double newDistance = entityPos.distanceTo(selectedNew);
                        if (monitor.hoveredEntity != null && monitor.hoveredEntity.equals(track.entityId()) && newDistance > bestDistance) {
                            monitor.hoveredEntity = null;
                        }
                        if (newDistance < bestDistance) {
                            bestDistance = newDistance;
                            monitor.hoveredEntity = track.entityId();
                        }
                    }

                    for (VSRadarTracks track : radar.getVS2Positions()) {
                        Vec3 entityPos = track.position();
                        entityPos = entityPos.multiply(1, 0, 1);
                        Vec3 selectedNew = selected.multiply(1, 0, 1);
                        double newDistance = entityPos.distanceTo(selectedNew);
                        if (monitor.hoveredEntity != null && monitor.hoveredEntity.equals(track.id()) && newDistance > bestDistance) {
                            monitor.hoveredEntity = null;
                        }
                        if (newDistance < bestDistance) {
                            bestDistance = newDistance;
                            monitor.hoveredEntity = track.id();
                        }
                    }

                });
                monitor.notifyUpdate();

            }
        }

    }
}
