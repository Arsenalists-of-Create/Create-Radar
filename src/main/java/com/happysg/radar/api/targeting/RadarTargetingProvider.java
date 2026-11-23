package com.happysg.radar.api.targeting;

import javax.annotation.Nullable;

@FunctionalInterface
public interface RadarTargetingProvider {
    @Nullable RadarTargetingSolution resolve(RadarTargetingContext context);
}
