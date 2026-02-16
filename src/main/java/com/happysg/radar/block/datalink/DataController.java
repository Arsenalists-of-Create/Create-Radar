package com.happysg.radar.block.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

//todo monitor hardcoded for now
public class DataController extends DataLinkBehavior {
    public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos)
                .getShape(level, pos);
        if (shape.isEmpty())
            return new AABB(pos);
        return shape.bounds()
                .move(pos);
    }
}