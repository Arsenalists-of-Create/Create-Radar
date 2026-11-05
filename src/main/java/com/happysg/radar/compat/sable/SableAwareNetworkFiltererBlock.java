package com.happysg.radar.compat.sable;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class SableAwareNetworkFiltererBlock extends NetworkFiltererBlock implements BlockSubLevelAssemblyListener {
    public SableAwareNetworkFiltererBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void afterMove(
            ServerLevel originLevel,
            ServerLevel resultingLevel,
            BlockState newState,
            BlockPos oldPos,
            BlockPos newPos
    ) {
        try {
            SableDataLinkRelocation.recordEndpointMove(originLevel, resultingLevel, oldPos, newPos);
            if (originLevel == resultingLevel && resultingLevel.getBlockEntity(newPos) instanceof NetworkFiltererBlockEntity filterer) {
                filterer.onSableMovedFrom(oldPos);
            }
        } finally {
            SableDataLinkRelocation.finishPreparedFiltererMove(resultingLevel, newPos);
        }
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState oldState, BlockPos oldPos, BlockPos newPos) {
        SableDataLinkRelocation.prepareFiltererMove(originLevel, resultingLevel, oldPos, newPos);
    }
}