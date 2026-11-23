package com.happysg.radar.api.controller;

public interface RadarYawController extends RadarAimController {
    boolean atTargetYaw(boolean lag);
    boolean atTargetYaw(boolean lag, double minimumToleranceDegrees);
    void returnToInitialOrientation();
}
