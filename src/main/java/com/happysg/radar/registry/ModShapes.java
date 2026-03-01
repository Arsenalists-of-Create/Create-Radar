package com.happysg.radar.registry;


import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.utility.VoxelShaper;
import net.minecraft.world.level.block.Block;

public class ModShapes {
    public static final VoxelShaper RADAR_DISH = new AllShapes.Builder(Block.box(0, 7, 0, 16, 9, 16)).forDirectional();

    public static final VoxelShaper RADAR_PLATE = new AllShapes.Builder(Block.box(0, 4, 0, 16, 12, 16)).forDirectional();

}
