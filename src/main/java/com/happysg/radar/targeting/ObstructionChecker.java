package com.happysg.radar.targeting;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

public class ObstructionChecker {
   public static final ObstructionChecker NONE = new ObstructionChecker(false);
   private final boolean enabled;

   public ObstructionChecker() {
      this(true);
   }

   private ObstructionChecker(boolean enabled) {
      this.enabled = enabled;
   }

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory) {
      return this.check(level, trajectory, Integer.MAX_VALUE);
   }

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory, int maxTick) {
      if (this.enabled && level != null && trajectory != null && !level.isClientSide()) {
         List<Trajectory.Sample> samples = trajectory.trajectory().samples();

         for(int i = 0; i + 1 < samples.size(); ++i) {
            Trajectory.Sample fromSample = samples.get(i);
            Trajectory.Sample toSample = samples.get(i + 1);
            if (fromSample.tick() >= maxTick) {
               break;
            }

            Vec3 from = fromSample.position();
            Vec3 to = toSample.position();
            if (!(from.distanceToSqr(to) < 1.0E-10)) {
               ClipContext context = new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, CollisionContext.empty());
               HitResult hit = level.clip(context);
               if (hit.getType() != Type.MISS) {
                  Vec3 hitPosition = hit.getLocation();
                  if (!(hitPosition.distanceToSqr(from) > to.distanceToSqr(from) + 1.0E-8)) {
                     BlockPos blockPos = hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : null;
                     return ObstructionResult.blocked(toSample.tick(), hitPosition, blockPos);
                  }
               }
            }
         }

         return ObstructionResult.clearPath();
      } else {
         return ObstructionResult.clearPath();
      }
   }
}
