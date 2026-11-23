package com.happysg.radar.api.network;

import com.happysg.radar.api.datalink.RadarDataLinkEndpointType;
import net.minecraft.core.BlockPos;
import javax.annotation.Nullable;

public interface RadarContactNetworkEndpoint {
    RadarDataLinkEndpointType getContactNetworkEndpointType();
    @Nullable default BlockPos getContactWeaponMountPos() { return null; }
}
