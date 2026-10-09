package com.happysg.radar.api.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public interface MonitorContactSelectionHandler {

    /**
     * Called when a monitor contact is selected.
     *
     * @return true if this handler fully handled the selection
     */
    boolean onSelect(Player player, BlockPos monitorPos, MonitorContactSnapshot contact);
}