package com.happysg.radar.api.controller;

/**
 * Public interface for Create Radar pitch controllers.
 */
public interface RadarPitchController extends RadarAimController {

    /**
     * Checks whether the controlled mount is aligned with the current
     * pitch target.
     *
     * @param lag whether normal targeting lag/tolerance behavior should apply
     * @return {@code true} if the controlled mount is aligned with the target
     */
    boolean atTargetPitch(boolean lag);

    /**
     * Checks whether the controlled mount is aligned with the current
     * pitch target using at least the supplied tolerance.
     *
     * @param lag whether normal targeting lag/tolerance behavior should apply
     * @param minimumToleranceDegrees minimum accepted tolerance in degrees
     *
     * @return {@code true} if the controlled mount is aligned with the target
     */
    boolean atTargetPitch(boolean lag, double minimumToleranceDegrees);

    /**
     * Commands the pitch controller back to zero pitch.
     */
    void returnToZero();
}