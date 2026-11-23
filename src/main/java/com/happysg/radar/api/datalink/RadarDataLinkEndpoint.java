package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import java.util.Objects;

public record RadarDataLinkEndpoint(BlockPos endpointPos, RadarDataLinkEndpointType type) {
    public RadarDataLinkEndpoint {
        endpointPos = Objects.requireNonNull(endpointPos, "endpointPos").immutable();
        type = Objects.requireNonNull(type, "type");
    }
}
