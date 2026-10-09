package com.happysg.radar.api.monitor;

/**
 * Optional capability for radar sources that require monitor snapshots
 * to be refreshed more frequently than the default monitor cadence.
 */
public interface MonitorSyncIntervalProvider {

    /**
     * Requested monitor refresh interval in ticks.
     * Values below 1 are treated as 1.
     */
    default int getMonitorSyncIntervalTicks() {
        return 5;
    }
}