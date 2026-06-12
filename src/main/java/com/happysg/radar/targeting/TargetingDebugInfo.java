package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

public record TargetingDebugInfo(Vec3 currentTargetPosition, Vec3 predictedTargetPosition, Vec3 closestApproachPosition, Vec3 targetVelocity, Vec3 targetAcceleration, Vec3 inheritedVelocity, List<Trajectory.Sample> trajectorySamples, int flightTicks, double missDistance, double desiredYawDeg, double desiredPitchDeg, @Nullable Double currentYawDeg, @Nullable Double currentPitchDeg, double confidence, ObstructionResult obstructionResult) {
   private static final int MAX_TRAJECTORY_DEBUG_SAMPLES = 16;

   public TargetingDebugInfo(Vec3 currentTargetPosition, Vec3 predictedTargetPosition, Vec3 closestApproachPosition, Vec3 targetVelocity, Vec3 targetAcceleration, Vec3 inheritedVelocity, List<Trajectory.Sample> trajectorySamples, int flightTicks, double missDistance, double desiredYawDeg, double desiredPitchDeg, @Nullable Double currentYawDeg, @Nullable Double currentPitchDeg, double confidence, ObstructionResult obstructionResult) {
      currentTargetPosition = currentTargetPosition == null ? Vec3.ZERO : currentTargetPosition;
      predictedTargetPosition = predictedTargetPosition == null ? Vec3.ZERO : predictedTargetPosition;
      closestApproachPosition = closestApproachPosition == null ? Vec3.ZERO : closestApproachPosition;
      targetVelocity = targetVelocity == null ? Vec3.ZERO : targetVelocity;
      targetAcceleration = targetAcceleration == null ? Vec3.ZERO : targetAcceleration;
      inheritedVelocity = inheritedVelocity == null ? Vec3.ZERO : inheritedVelocity;
      trajectorySamples = compactSamples(trajectorySamples);
      if (flightTicks < 0) {
         flightTicks = 0;
      }

      if (!Double.isFinite(missDistance)) {
         missDistance = Double.POSITIVE_INFINITY;
      }

      if (!Double.isFinite(desiredYawDeg)) {
         desiredYawDeg = (double)0.0F;
      }

      if (!Double.isFinite(desiredPitchDeg)) {
         desiredPitchDeg = (double)0.0F;
      }

      if (currentYawDeg != null && !Double.isFinite(currentYawDeg)) {
         currentYawDeg = null;
      }

      if (currentPitchDeg != null && !Double.isFinite(currentPitchDeg)) {
         currentPitchDeg = null;
      }

      confidence = Math.max((double)0.0F, Math.min((double)1.0F, confidence));
      if (obstructionResult == null) {
         obstructionResult = ObstructionResult.clearPath();
      }

      this.currentTargetPosition = currentTargetPosition;
      this.predictedTargetPosition = predictedTargetPosition;
      this.closestApproachPosition = closestApproachPosition;
      this.targetVelocity = targetVelocity;
      this.targetAcceleration = targetAcceleration;
      this.inheritedVelocity = inheritedVelocity;
      this.trajectorySamples = trajectorySamples;
      this.flightTicks = flightTicks;
      this.missDistance = missDistance;
      this.desiredYawDeg = desiredYawDeg;
      this.desiredPitchDeg = desiredPitchDeg;
      this.currentYawDeg = currentYawDeg;
      this.currentPitchDeg = currentPitchDeg;
      this.confidence = confidence;
      this.obstructionResult = obstructionResult;
   }

   public String summary() {
      return "target=" + String.valueOf(this.currentTargetPosition) + " predicted=" + String.valueOf(this.predictedTargetPosition) + " closest=" + String.valueOf(this.closestApproachPosition) + " miss=" + this.missDistance + " ticks=" + this.flightTicks + " yaw=" + this.desiredYawDeg + " pitch=" + this.desiredPitchDeg + " currentYaw=" + this.currentYawDeg + " currentPitch=" + this.currentPitchDeg + " confidence=" + this.confidence + " targetVel=" + String.valueOf(this.targetVelocity) + " targetAccel=" + String.valueOf(this.targetAcceleration) + " inheritedVel=" + String.valueOf(this.inheritedVelocity) + " obstruction=" + this.obstructionSummary() + " samples=" + this.trajectorySampleSummary();
   }

   private String obstructionSummary() {
      if (this.obstructionResult != null && !this.obstructionResult.clear()) {
         return "blocked tick=" + this.obstructionResult.blockedTick() + " pos=" + String.valueOf(this.obstructionResult.blockedPosition()) + " block=" + String.valueOf(this.obstructionResult.blockPosition());
      } else {
         return "clear";
      }
   }

   private String trajectorySampleSummary() {
      if (this.trajectorySamples.isEmpty()) {
         return "[]";
      } else {
         StringBuilder builder = new StringBuilder("[");

         for(int i = 0; i < this.trajectorySamples.size(); ++i) {
            Trajectory.Sample sample = this.trajectorySamples.get(i);
            if (i > 0) {
               builder.append(", ");
            }

            builder.append("{t=").append(sample.tick()).append(", p=").append(sample.position()).append(", v=").append(sample.velocity()).append('}');
         }

         return builder.append(']').toString();
      }
   }

   private static List<Trajectory.Sample> compactSamples(@Nullable List<Trajectory.Sample> samples) {
      if (samples != null && !samples.isEmpty()) {
         if (samples.size() <= 16) {
            return List.copyOf(samples);
         } else {
            List<Trajectory.Sample> compact = new ArrayList<>(16);
            double step = ((double)samples.size() - (double)1.0F) / (double)15.0F;
            int lastIndex = -1;

            for(int i = 0; i < 16; ++i) {
               int index = (int)Math.round((double)i * step);
               if (index != lastIndex) {
                  compact.add(samples.get(index));
                  lastIndex = index;
               }
            }

            return List.copyOf(compact);
         }
      } else {
         return List.of();
      }
   }
}
