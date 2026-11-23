package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record ObstructionResult(boolean clear, boolean blocked, int blockedTick, @Nullable Vec3 blockedPosition, @Nullable BlockPos blockPosition, @Nullable Long obstructingSublevelId) {
   public ObstructionResult(boolean clear, boolean blocked, int blockedTick, @Nullable Vec3 blockedPosition, @Nullable BlockPos blockPosition) {
      this(clear, blocked, blockedTick, blockedPosition, blockPosition, null);
   }

   public static ObstructionResult clearPath() {
      return new ObstructionResult(true, false, -1, null, null, null);
   }

   public static ObstructionResult blocked(int blockedTick, Vec3 blockedPosition, @Nullable BlockPos blockPosition) {
      return blocked(blockedTick, blockedPosition, blockPosition, null);
   }

   public static ObstructionResult blocked(int blockedTick, Vec3 blockedPosition, @Nullable BlockPos blockPosition, @Nullable Long shipId) {
      return new ObstructionResult(false, true, blockedTick, blockedPosition, blockPosition, shipId);
   }
}
