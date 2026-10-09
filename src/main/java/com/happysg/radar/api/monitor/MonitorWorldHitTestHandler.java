package com.happysg.radar.api.monitor;

import javax.annotation.Nullable;

@FunctionalInterface
public interface MonitorWorldHitTestHandler {

    /**
     * Performs custom contact hit-testing for a physical monitor.
     *
     * @return the selected public monitor contact, or null when this handler
     * does not select anything at this hit position
     */
    @Nullable
    MonitorContactSnapshot findContact(MonitorWorldHitTestContext context);
}