package com.happysg.radar.api.controller;

/**
 * Public interface for Create Radar yaw controllers.
 */
public interface RadarYawController extends RadarAimController {

    /**
     * Checks whether the controlled mount is aligned with the current
     * yaw target.
     *
     * @param lag whether normal targeting lag/tolerance behavior should apply
     * @return {@code true} if the controlled mount is aligned with the target
     */
    boolean atTargetYaw(boolean lag);

    /**
     * Checks whether the controlled mount is aligned with the current
     * yaw target using at least the supplied tolerance.
     *
     * @param lag whether normal targeting lag/tolerance behavior should apply
     * @param minimumToleranceDegrees minimum allowed alignment tolerance in degrees
     *
     * @return {@code true} if the controlled mount is aligned with the target
     */
    boolean atTargetYaw(boolean lag, double minimumToleranceDegrees);

    /**
     * Commands the yaw controller back to its mount's initial orientation.
     */
    void returnToInitialOrientation();
}