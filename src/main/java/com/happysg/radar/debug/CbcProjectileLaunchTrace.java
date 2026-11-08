package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

@EventBusSubscriber(modid = CreateRadar.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CbcProjectileLaunchTrace {
    private CbcProjectileLaunchTrace() {}

    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (!MotionTraceRecorder.isEnabled()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity entity = event.getEntity();

        if (!(entity instanceof AbstractCannonProjectile)) {
            return;
        }

        Vec3 position = entity.position();
        Vec3 velocity = entity.getDeltaMovement();

        MotionTraceRecorder.record(
                "cbc.projectile_spawn",
                serverLevel,
                entity.blockPosition(),

                "projectileId", entity.getUUID(),
                "projectileEntityId", entity.getId(),
                "projectileClass", entity.getClass().getName(),

                "spawnTick", serverLevel.getGameTime(),
                "spawnPosition", position,
                "spawnVelocity", velocity,
                "spawnSpeed", velocity.length(),

                "velocityX", velocity.x,
                "velocityY", velocity.y,
                "velocityZ", velocity.z
        );
    }
}