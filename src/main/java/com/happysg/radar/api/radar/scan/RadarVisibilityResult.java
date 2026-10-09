package com.happysg.radar.api.radar.scan;

public enum RadarVisibilityResult {
    /**
     * Use Create: Radars' normal visibility/occlusion logic.
     */
    PASS,

    /**
     * Force the target to be visible.
     */
    VISIBLE,

    /**
     * Force the target to be blocked.
     */
    BLOCKED
}