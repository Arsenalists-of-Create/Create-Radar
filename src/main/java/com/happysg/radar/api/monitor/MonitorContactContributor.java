package com.happysg.radar.api.monitor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.Collection;

@FunctionalInterface
public interface MonitorContactContributor {
    Collection<MonitorContactSnapshot> getContacts(ClientLevel level, BlockPos monitorPos);
}