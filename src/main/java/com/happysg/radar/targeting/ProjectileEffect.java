package com.happysg.radar.targeting;

public enum ProjectileEffect {
   UNKNOWN,
   DIRECT,
   EXPLOSIVE;

   public boolean allowsSplash() {
      return this == EXPLOSIVE;
   }

   // $FF: synthetic method
   private static ProjectileEffect[] $values() {
      return new ProjectileEffect[]{UNKNOWN, DIRECT, EXPLOSIVE};
   }
}
