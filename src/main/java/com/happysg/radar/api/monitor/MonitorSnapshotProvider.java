package com.happysg.radar.api.monitor;

import java.util.List;

public interface MonitorSnapshotProvider {

    /**
     * All radar display snapshots currently known to this monitor.
     */
    List<MonitorRadarSnapshot> getRadarSnapshots();

    /**
     * Radar snapshots that should currently be rendered as active.
     */
    List<MonitorRadarSnapshot> getRunningRadarSnapshots();
}