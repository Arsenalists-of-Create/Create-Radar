package com.happysg.radar.targeting;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public record Trajectory(List<Sample> samples) {
   public Trajectory(List<Sample> samples) {
      samples = samples == null ? List.of() : List.copyOf(samples);
      this.samples = samples;
   }

   public int endTick() {
      return this.samples.isEmpty() ? 0 : this.samples.get(this.samples.size() - 1).tick();
   }

   public Vec3 endPosition() {
      return this.samples.isEmpty() ? Vec3.ZERO : this.samples.get(this.samples.size() - 1).position();
   }

   public Vec3 endVelocity() {
      return this.samples.isEmpty() ? Vec3.ZERO : this.samples.get(this.samples.size() - 1).velocity();
   }

   public static record Sample(int tick, Vec3 position, Vec3 velocity) {
   }
}
