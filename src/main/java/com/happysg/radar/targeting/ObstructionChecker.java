package com.happysg.radar.targeting;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SubLevelAimResolver;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.List;
import java.util.UUID;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
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

   boolean isEnabled() {
      return this.enabled;
   }

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory) {
      return this.check(level, trajectory, Integer.MAX_VALUE);
   }

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory, int maxTick) {
      return this.check(level, trajectory, maxTick, null);
   }

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory, int maxTick, Vec3 lastSegmentEnd) {
      return check(level, trajectory, maxTick, lastSegmentEnd, false);
   }

   public ObstructionResult check(
           Level level,
           ProjectileSimulator.SimulationResult trajectory,
           int maxTick,
           Vec3 lastSegmentEnd,
           boolean cbcSweep) {
      if (this.enabled && level != null && trajectory != null && !level.isClientSide()) {
         List<Trajectory.Sample> samples = trajectory.trajectory().samples();

         for(int i = 0; i + 1 < samples.size(); ++i) {
            Trajectory.Sample fromSample = samples.get(i);
            Trajectory.Sample toSample = samples.get(i + 1);
            if (fromSample.tick() >= maxTick) {
               break;
            }

            Vec3 from = fromSample.position();

            boolean clippedAtTarget = lastSegmentEnd != null && toSample.tick() == maxTick;
            Vec3 to = clippedAtTarget ? lastSegmentEnd : cbcSweep ? cbcSweepEnd(fromSample, toSample) : toSample.position();
            if (from.distanceToSqr(to) >= 1.0E-10) {
               SegmentHit hit = nearestBlockHit(level, from, to);

               if (hit != null) {
                  return ObstructionResult.blocked(toSample.tick(), hit.worldPosition(), hit.blockPosition(), hit.sublevelId());
               }
            }
         }

         return ObstructionResult.clearPath();
      } else {
         return ObstructionResult.clearPath();
      }
   }

   private record SegmentHit(Vec3 worldPosition, BlockPos blockPosition, UUID sublevelId) {}

   private static SegmentHit nearestBlockHit(Level level, Vec3 from, Vec3 to) {
      SegmentHit nearest = null;
      double nearestDistance = from.distanceToSqr(to) + 1.0E-8;
      ClipContext worldContext = new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, CollisionContext.empty());

      HitResult worldHit = level.clip(worldContext);

      if (worldHit instanceof BlockHitResult blockHit && worldHit.getType() == Type.BLOCK) {
         Vec3 hitPosition = worldHit.getLocation();
         BlockPos hitBlock = blockHit.getBlockPos();
         UUID sublevelId = null;

         if (Mods.SABLE.isLoaded()) {
            SubLevelAccess hitShip =
                    SableCompanion.INSTANCE.getContaining(level, hitBlock);

            if (hitShip != null) {
               hitPosition = SubLevelAimResolver.toWorld(hitShip, hitPosition);
               sublevelId = hitShip.getUniqueId();
            }
         }

         double distance = from.distanceToSqr(hitPosition);

         if (distance <= nearestDistance) {
            nearestDistance = distance;
            nearest = new SegmentHit(hitPosition, hitBlock, sublevelId);
         }
      }

      if (!Mods.SABLE.isLoaded()) {
         return nearest;
      }

      AABB bounds = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z), Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0E-4);
      for (SubLevel ship : SableUtils.getLoadedShips(level, bounds)) {
         Vec3 localFrom = SubLevelAimResolver.toLocal(ship, from);
         Vec3 localTo = SubLevelAimResolver.toLocal(ship, to);

         ClipContext localContext = new ClipContext(localFrom, localTo, Block.COLLIDER, Fluid.NONE, CollisionContext.empty());
         HitResult localHit = ship.getLevel().clip(localContext);

         if (!(localHit instanceof BlockHitResult blockHit) || localHit.getType() != Type.BLOCK) {
            continue;
         }

         Vec3 worldHitPosition = SubLevelAimResolver.toWorld(ship, localHit.getLocation());
         double distance = from.distanceToSqr(worldHitPosition);

         if (distance > nearestDistance) {
            continue;
         }

         nearestDistance = distance;

         nearest = new SegmentHit(worldHitPosition, blockHit.getBlockPos(), ship.getUniqueId());
      }

      return nearest;
   }

   static Vec3 cbcSweepEnd(Trajectory.Sample from, Trajectory.Sample to) {
      return from.position().add(to.velocity());
   }

}
