package com.happysg.radar.targeting;

import java.util.List;
import javax.annotation.Nullable;

public record TargetingResult(boolean valid, boolean hasShot, @Nullable AimSolution aimSolution, double desiredYawDeg, double desiredPitchDeg, int predictedFlightTicks, double missDistance, double confidence, String reason, List<String> debugInfo, @Nullable TargetingDebugInfo debugData) {
   public TargetingResult(boolean valid, boolean hasShot, @Nullable AimSolution aimSolution, double desiredYawDeg, double desiredPitchDeg, int predictedFlightTicks, double missDistance, double confidence, String reason, List<String> debugInfo, @Nullable TargetingDebugInfo debugData) {
      if (debugInfo == null) {
         debugInfo = List.of();
      } else {
         debugInfo = List.copyOf(debugInfo);
      }

      if (reason == null) {
         reason = "";
      }

      if (!Double.isFinite(missDistance)) {
         missDistance = Double.POSITIVE_INFINITY;
      }

      confidence = Math.max((double)0.0F, Math.min((double)1.0F, confidence));
      this.valid = valid;
      this.hasShot = hasShot;
      this.aimSolution = aimSolution;
      this.desiredYawDeg = desiredYawDeg;
      this.desiredPitchDeg = desiredPitchDeg;
      this.predictedFlightTicks = predictedFlightTicks;
      this.missDistance = missDistance;
      this.confidence = confidence;
      this.reason = reason;
      this.debugInfo = debugInfo;
      this.debugData = debugData;
   }

   public static TargetingResult invalid(String reason) {
      return new TargetingResult(false, false, null, (double)0.0F, (double)0.0F, 0, Double.POSITIVE_INFINITY, (double)0.0F, reason, List.of(), null);
   }

   public static TargetingResult noShot(String reason) {
      return noShot(reason, List.of());
   }

   public static TargetingResult noShot(String reason, List<String> debugInfo) {
      return noShot(reason, debugInfo, null);
   }

   public static TargetingResult noShot(String reason, List<String> debugInfo, @Nullable TargetingDebugInfo debugData) {
      return new TargetingResult(true, false, null, (double)0.0F, (double)0.0F, 0, Double.POSITIVE_INFINITY, (double)0.0F, reason, debugInfo, debugData);
   }

   public static TargetingResult shot(AimSolution aimSolution, double confidence, List<String> debugInfo) {
      return shot(aimSolution, confidence, debugInfo, null);
   }

   public static TargetingResult shot(AimSolution aimSolution, double confidence, List<String> debugInfo, @Nullable TargetingDebugInfo debugData) {
      return aimSolution == null ? invalid("missing aim solution") : new TargetingResult(true, true, aimSolution, aimSolution.yawDeg(), aimSolution.pitchDeg(), aimSolution.flightTicks(), aimSolution.missDistance(), confidence, "ok", debugInfo, debugData);
   }

   public String debugString() {
      String base = "TargetingResult{valid=" + this.valid + ", hasShot=" + this.hasShot + ", reason='" + this.reason + "', yaw=" + this.desiredYawDeg + ", pitch=" + this.desiredPitchDeg + ", ticks=" + this.predictedFlightTicks + ", miss=" + this.missDistance + ", confidence=" + this.confidence + ", debugInfo=" + String.valueOf(this.debugInfo) + "}";
      return this.debugData == null ? base : base + " " + this.debugData.summary();
   }
}
