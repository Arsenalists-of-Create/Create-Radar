package com.happysg.radar.api.controller;

/**
 * Public interface for Create Radar firing controller.
 */
public interface RadarFireController {
    /**
     * @return {@code true} while the controller is currently outputting
     * a firing signal
     */
    boolean isFiring();
}