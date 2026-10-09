package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

@FunctionalInterface
public interface MonitorScreenFactory {
    /**
     * Creates a custom monitor screen.
     *
     * @return the custom screen, or null to fall back to the native monitor screen
     */
    @Nullable
    Screen create(ClientLevel level, BlockPos monitorPos, List<MonitorRadarSnapshot> radars);
}