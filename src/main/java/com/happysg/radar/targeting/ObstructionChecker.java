package com.happysg.radar.targeting;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2Utils;
import java.util.List;
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
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;

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

   public ObstructionResult check(Level level, ProjectileSimulator.SimulationResult trajectory,
                                  int maxTick, Vec3 lastSegmentEnd, boolean cbcSweep) {
      if (this.enabled && level != null && trajectory != null && !level.isClientSide()) {
         List<Trajectory.Sample> samples = trajectory.trajectory().samples();
         for (int i = 0; i + 1 < samples.size(); ++i) {
            Trajectory.Sample fromSample = samples.get(i);
            Trajectory.Sample toSample = samples.get(i + 1);
            if (fromSample.tick() >= maxTick) break;

            Vec3 from = fromSample.position();
            boolean clippedAtTarget = lastSegmentEnd != null && toSample.tick() == maxTick;
            Vec3 to = clippedAtTarget ? lastSegmentEnd : cbcSweep ? cbcSweepEnd(fromSample, toSample) : toSample.position();
            if (from.distanceToSqr(to) >= 1.0E-10) {
               SegmentHit hit = nearestBlockHit(level, from, to);
               if (hit != null) {
                  return ObstructionResult.blocked(toSample.tick(), hit.worldPosition(), hit.blockPosition(), hit.shipId());
               }
            }
         }
      }
      return ObstructionResult.clearPath();
   }

   private record SegmentHit(Vec3 worldPosition, BlockPos blockPosition, Long shipId) {}

   private static SegmentHit nearestBlockHit(Level level, Vec3 from, Vec3 to) {
      SegmentHit nearest = null;
      double nearestDistance = from.distanceToSqr(to) + 1.0E-8;
      ClipContext worldContext = new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, null);
      HitResult worldHit = level.clip(worldContext);
      if (worldHit instanceof BlockHitResult blockHit && worldHit.getType() == Type.BLOCK) {
         Vec3 hitPosition = worldHit.getLocation();
         BlockPos hitBlock = blockHit.getBlockPos();
         Ship managingShip = Mods.VALKYRIENSKIES.isLoaded() ? VS2Utils.getShipManagingPos(level, hitBlock) : null;
         Long shipId = null;
         if (managingShip != null) {
            Vector3d transformed = managingShip.getShipToWorld().transformPosition(new Vector3d(hitPosition.x, hitPosition.y, hitPosition.z));
            hitPosition = new Vec3(transformed.x, transformed.y, transformed.z);
            shipId = managingShip.getId();
         }
         double distance = from.distanceToSqr(hitPosition);
         if (distance <= nearestDistance) {
            nearestDistance = distance;
            nearest = new SegmentHit(hitPosition, hitBlock, shipId);
         }
      }

      if (!Mods.VALKYRIENSKIES.isLoaded()) return nearest;

      AABB bounds = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
              Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0E-4);
      for (Ship ship : VS2Utils.getLoadedShips(level, bounds)) {
         Vector3d localStart = ship.getWorldToShip().transformPosition(new Vector3d(from.x, from.y, from.z));
         Vector3d localEnd = ship.getWorldToShip().transformPosition(new Vector3d(to.x, to.y, to.z));
         Vec3 localFrom = new Vec3(localStart.x, localStart.y, localStart.z);
         Vec3 localTo = new Vec3(localEnd.x, localEnd.y, localEnd.z);
         HitResult localHit = level.clip(new ClipContext(localFrom, localTo, Block.COLLIDER, Fluid.NONE, null));
         if (!(localHit instanceof BlockHitResult blockHit) || localHit.getType() != Type.BLOCK) continue;
         Ship hitOwner = VS2Utils.getShipManagingPos(level, blockHit.getBlockPos());
         if (hitOwner == null || hitOwner.getId() != ship.getId()) continue;
         Vec3 localPos = localHit.getLocation();
         Vector3d worldPos = ship.getShipToWorld().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z));
         Vec3 worldHitPosition = new Vec3(worldPos.x, worldPos.y, worldPos.z);
         double distance = from.distanceToSqr(worldHitPosition);
         if (distance > nearestDistance) continue;
         nearestDistance = distance;
         nearest = new SegmentHit(worldHitPosition, blockHit.getBlockPos(), ship.getId());
      }
      return nearest;
   }

   static Vec3 cbcSweepEnd(Trajectory.Sample from, Trajectory.Sample to) {
      return from.position().add(to.velocity());
   }
}
