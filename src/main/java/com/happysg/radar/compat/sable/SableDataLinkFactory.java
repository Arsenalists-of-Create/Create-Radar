package com.happysg.radar.compat.sable;

import com.happysg.radar.block.datalink.DataLinkBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class SableDataLinkFactory {
    private SableDataLinkFactory() {}

    public static DataLinkBlock create(BlockBehaviour.Properties properties) {
        return new SableAwareDataLinkBlock(properties);
    }
}