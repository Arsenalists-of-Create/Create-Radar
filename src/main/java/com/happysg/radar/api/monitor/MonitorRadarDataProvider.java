package com.happysg.radar.api.monitor;

/**
 * Optional monitor extension for radar implementations that need to expose
 * additional addon-owned data to monitor renderers and interaction handlers.
 * The returned data is transported as part of the public
 * {@link MonitorRadarSnapshot}.
 */
public interface MonitorRadarDataProvider {

    /**
     * Returns addon-specific monitor data for this radar.
     * Implementations should return {@link MonitorRadarData#empty()}
     * when no additional data is available.
     */
    default MonitorRadarData getMonitorRadarData() {
        return MonitorRadarData.empty();
    }
}