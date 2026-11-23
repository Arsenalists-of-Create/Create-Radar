package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

@FunctionalInterface
public interface RadarDataLinkProvider {
    @Nullable RadarDataLinkEndpoint find(Level level, BlockPos clickedPos);
}
