package com.happysg.radar.block.controller.limits.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A local-space oriented box used while assembling display geometry. */
record ControllerVisualBox(
        Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ
) {
    static ControllerVisualBox axisAligned(AABB box) {
        return new ControllerVisualBox(box.getCenter(),
                new Vec3(box.getXsize() * 0.5, 0, 0),
                new Vec3(0, box.getYsize() * 0.5, 0),
                new Vec3(0, 0, box.getZsize() * 0.5));
    }

    ControllerVisualBox move(BlockPos position) {
        return new ControllerVisualBox(center.add(position.getX(),
                position.getY(), position.getZ()), axisX, axisY, axisZ);
    }
}
