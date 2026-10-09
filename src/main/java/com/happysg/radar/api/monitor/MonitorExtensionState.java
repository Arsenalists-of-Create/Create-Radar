package com.happysg.radar.api.monitor;

import java.util.Objects;

/**
 * Optional addon monitor state associated with a radar display.
 * This state is synchronized by Create: Radars as part of the monitor
 * snapshot.
 */
public record MonitorExtensionState(int monitorWidth, int monitorHeight, boolean synthetic, MonitorRadarData data) {

    private static final MonitorExtensionState EMPTY =
            new MonitorExtensionState(1, 1, false, MonitorRadarData.empty());

    public MonitorExtensionState {
        monitorWidth = Math.max(1, monitorWidth);
        monitorHeight = Math.max(1, monitorHeight);

        if (data == null) {
            data = MonitorRadarData.empty();
        }
    }

    public static MonitorExtensionState empty() {
        return EMPTY;
    }

    public static MonitorExtensionState of(int monitorWidth, int monitorHeight, boolean synthetic, MonitorRadarData data) {
        return new MonitorExtensionState(
                monitorWidth,
                monitorHeight,
                synthetic,
                Objects.requireNonNullElse(data, MonitorRadarData.empty())
        );
    }
}