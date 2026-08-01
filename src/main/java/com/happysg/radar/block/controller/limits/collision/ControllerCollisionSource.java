package com.happysg.radar.block.controller.limits.collision;

import com.happysg.radar.compat.cbc.CannonMountContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Server-side mount resolution exposed to the collision snapshot builder. */
public interface ControllerCollisionSource {
    List<CannonMountContext> resolveCollisionCbcMounts();

    List<BlockPos> resolveCollisionMountPositions();

    /** Attached Sable assemblies controlled through a structural bearing. */
    default List<UUID> resolveCollisionSublevelIds() {
        return List.of();
    }

    /** Current structural-cannon forward direction in world space. */
    default @Nullable Vec3 resolveCollisionCannonForward() {
        return null;
    }

    /** Fixed controller-neutral forward direction in root-world space. */
    default @Nullable Vec3 resolveCollisionNeutralForward() {
        return null;
    }
}
