package com.happysg.radar.api.monitor.client;

/**
 * Client-side extension point for custom radar rendering on Create: Radars
 * monitors.
 * Returning {@code true} means the renderer completely handled that
 * radar for the requested surface and the default renderer should not run.
 * Returning {@code false} allows the normal Create: Radars renderer
 * to handle the radar.
 */
public interface MonitorRadarRenderer {

    /**
     * Render this radar on an in-world monitor.
     *
     * @return true if default rendering should be skipped
     */
    default boolean renderWorld(MonitorRadarWorldRenderContext context) {
        return false;
    }

    /**
     * Render this radar in the full-screen monitor GUI.
     *
     * @return true if default rendering should be skipped
     */
    default boolean renderScreen(MonitorRadarScreenRenderContext context) {
        return false;
    }
}