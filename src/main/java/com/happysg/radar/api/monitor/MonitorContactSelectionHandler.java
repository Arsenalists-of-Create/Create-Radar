package com.happysg.radar.api.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public interface MonitorContactSelectionHandler {

    /**
     * Called when a monitor contact is selected.
     *
     * @return true if this handler fully handled the selection
     */
    boolean onSelect(Player player, BlockPos monitorPos, MonitorContactSnapshot contact);

    /**
     * Resolve and validate a contact using live server state.
     */
    @Nullable
    default MonitorContactSnapshot resolveServerContact(ServerPlayer player, BlockPos monitorPos, String contactId) {
        return null;
    }
}