package com.happysg.radar.api.monitor;

import net.minecraft.core.BlockPos;

public interface MonitorStateProvider {
    BlockPos getMonitorControllerPos();
    int getMonitorSize();
    boolean isMonitorController();
}