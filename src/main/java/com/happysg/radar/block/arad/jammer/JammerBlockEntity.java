package com.happysg.radar.block.arad.jammer;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class JammerBlockEntity  extends SmartBlockEntity {

    public int range = 128;
    public boolean enabled = true;

    public JammerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> list) {

    }

    public boolean affects(BlockPos radarPos) {
        if (!enabled) return false;
        return radarPos.closerThan(worldPosition, range);
    }
}
