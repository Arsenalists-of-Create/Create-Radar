package com.happysg.radar.block.monitor;


import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarTrack;
import com.happysg.radar.block.radar.bearing.VSRadarTracks;
import com.happysg.radar.util.MonitorUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
public class MonitorInputHandler {
    public static void monitorPlayerHovering(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = event.player.level();
        Vec3 hit = player.pick(5, 0.0F, false).getLocation();

        if (player.pick(5, 0.0F, false) instanceof BlockHitResult result) {
            if (level.getBlockEntity(result.getBlockPos()) instanceof MonitorBlockEntity be &&
                    level.getBlockEntity(be.getControllerPos()) instanceof MonitorBlockEntity monitor) {

                Direction facing = level.getBlockState(monitor.getControllerPos())
                        .getValue(MonitorBlock.FACING).getClockWise();
                Direction monitorFacing = level.getBlockState(monitor.getControllerPos())
                        .getValue(MonitorBlock.FACING);
                int size = monitor.getSize();
                Vec3 center = MonitorUtils.calculateCenter(level, monitor, facing, size);
                Vec3 relative = hit.subtract(center);
                relative = monitor.adjustRelativeVectorForFacing(relative, monitorFacing);

                if (monitor.radarPos == null) {
                    return;
                }

                Vec3 selected = MonitorUtils.calculateSelectedPosition(
                        relative, monitor.radarPos.getCenter(),
                        monitor.getRadar().map(RadarBearingBlockEntity::getRange).orElse(0f), size);

                monitor.getRadar().ifPresent(radar -> {
                    monitor.hoveredEntity = MonitorUtils.findClosestEntity(selected, radar.getRange(),
                            radar.getEntityPositions(), radar.getVS2Positions()).orElse(null);
                });

                monitor.notifyUpdate();
            }
        }
    }

}
