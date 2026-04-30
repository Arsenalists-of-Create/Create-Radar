package com.happysg.radar.registry;


import com.simibubi.create.AllShapes;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.world.level.block.Block;

public class ModShapes {
    public static final VoxelShaper RADAR_DISH = new AllShapes.Builder(Block.box(0, 7, 0, 16, 9, 16)).forDirectional();

    public static final VoxelShaper RADAR_PLATE = new AllShapes.Builder(Block.box(0, 4, 0, 16, 12, 16)).forDirectional();
    public static final AllShapes.Builder RADOME = new AllShapes.Builder(Block.box(0,0,0,16,1,16)).add(1,1,1, 15,24,15);
}