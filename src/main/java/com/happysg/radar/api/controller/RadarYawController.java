package com.happysg.radar.api.controller;

/**
 * Public interface for Create Radar yaw controllers.
 */
public interface RadarYawController extends RadarAimController {

    boolean atTargetYaw(boolean lag);

    boolean atTargetYaw(boolean lag, double minimumToleranceDegrees);

    /**
     * Commands the yaw controller back to its mount's initial orientation.
     */
    void returnToInitialOrientation();
}