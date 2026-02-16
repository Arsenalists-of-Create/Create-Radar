package com.happysg.radar.block.datalink;

import com.happysg.radar.block.datalink.screens.AbstractDataLinkScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public abstract class DataPeripheral extends DataLinkBehavior {

    @Nullable
    @OnlyIn(value = Dist.CLIENT)
    protected abstract AbstractDataLinkScreen getScreen(DataLinkBlockEntity be);

    protected abstract void transferData(@NotNull DataLinkContext context, @NotNull DataController activeTarget);
}
