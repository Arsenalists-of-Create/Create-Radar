package com.happysg.radar.api.controller;

/**
 * Public interface for Create Radar pitch controllers.
 */
public interface RadarPitchController extends RadarAimController {

    boolean atTargetPitch(boolean lag);

    boolean atTargetPitch(boolean lag, double minimumToleranceDegrees);

    /**
     * Commands the pitch controller back to zero pitch.
     */
    void returnToZero();
}