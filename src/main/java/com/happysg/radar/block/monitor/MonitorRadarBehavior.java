package com.happysg.radar.block.monitor;

import com.happysg.radar.block.datalink.DataController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MonitorRadarBehavior extends DataController {

    @Override
    public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            if (monitor.getControllerPos() == null)
                return new AABB(pos);
            if (!level.getBlockState(monitor.getControllerPos()).hasProperty(MonitorBlock.FACING))
                return new AABB(pos);
            Direction facing = level.getBlockState(monitor.getControllerPos())
                    .getValue(MonitorBlock.FACING).getClockWise();
            VoxelShape shape = level.getBlockState(monitor.getControllerPos())
                    .getShape(level, monitor.getControllerPos());
            return shape.bounds()
                    .move(monitor.getControllerPos()).expandTowards(facing.getStepX() * (monitor.radius - 1), monitor.radius - 1, facing.getStepZ() * (monitor.radius - 1));
        }
        return super.getMultiblockBounds(level, pos);
    }


}
