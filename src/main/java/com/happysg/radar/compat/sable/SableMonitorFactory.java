
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.monitor.MonitorBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class SableMonitorFactory {
    private SableMonitorFactory() {}

    public static MonitorBlock create(BlockBehaviour.Properties properties) {
        return new SableAwareMonitorBlock(properties);
    }

    private static final class SableAwareMonitorBlock extends MonitorBlock implements BlockSubLevelAssemblyListener {
        private SableAwareMonitorBlock(BlockBehaviour.Properties properties) {
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
            SableMonitorRelocation.capture(originLevel, resultingLevel, oldPos, newPos);
        }
    }
}
