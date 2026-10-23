
package com.happysg.radar.api.network;

import com.happysg.radar.api.datalink.RadarDataLinkEndpointType;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * For block entities that support direct
 * physical contact connections to radar networks.
 */
public interface RadarContactNetworkEndpoint {
    RadarDataLinkEndpointType getContactNetworkEndpointType();

    @Nullable
    default BlockPos getContactWeaponMountPos() {
        return null;
    }
}
