package com.happysg.radar.api.network;
import net.minecraft.core.BlockPos;
import java.util.Collection;

public interface RadarNetworkView {

    BlockPos controllerPos();
    Collection<BlockPos> radarPositions();
    Collection<BlockPos> monitorPositions();
    Collection<BlockPos> weaponEndpointPositions();

    default boolean hasRadar() {
        return !radarPositions().isEmpty();
    }

    default boolean hasMonitor() {
        return !monitorPositions().isEmpty();
    }
}