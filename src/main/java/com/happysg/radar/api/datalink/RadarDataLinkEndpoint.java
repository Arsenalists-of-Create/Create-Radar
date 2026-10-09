package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Public description of a block that may be linked to a Create: Radars network using the Data Link item.
 */
public record RadarDataLinkEndpoint(BlockPos endpointPos, RadarDataLinkEndpointType type) {
    public RadarDataLinkEndpoint {
        endpointPos = Objects.requireNonNull(endpointPos, "endpointPos").immutable();
        type = Objects.requireNonNull(type, "type");
    }
}