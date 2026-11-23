package com.happysg.radar.api.controller;

public interface RadarPitchController extends RadarAimController {
    boolean atTargetPitch(boolean lag);
    boolean atTargetPitch(boolean lag, double minimumToleranceDegrees);
    void returnToZero();
}
