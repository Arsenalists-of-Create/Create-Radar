package com.happysg.radar.api.monitor;

/**
 * Optional capability for radar sources that need to provide additional
 * synchronized monitor state.
 */
public interface MonitorExtensionStateProvider
        extends MonitorRadarDataProvider {

    /**
     * Returns the complete addon state that should be attached
     * to this radar's monitor snapshot.
     */
    default MonitorExtensionState getMonitorExtensionState() {
        return new MonitorExtensionState(1, 1, false, getMonitorRadarData());
    }

    @Override
    default MonitorRadarData getMonitorRadarData() {
        return MonitorRadarData.empty();
    }
}