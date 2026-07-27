package com.happysg.radar.compat.sable;

import com.happysg.radar.block.datalink.DataLinkBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loaded only when Sable is present. Keeping the optional interface on this
 * subclass prevents the ordinary DataLink block from acquiring a hard Sable
 * class-loading dependency.
 */
public final class SableAwareDataLinkBlock extends DataLinkBlock
        implements BlockSubLevelAssemblyListener {

    public SableAwareDataLinkBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel,
                          BlockState newState, BlockPos oldPos, BlockPos newPos) {
        SableDataLinkRelocation.capture(originLevel, resultingLevel, oldPos, newPos);
    }
}
