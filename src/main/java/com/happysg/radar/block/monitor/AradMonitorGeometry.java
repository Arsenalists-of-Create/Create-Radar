package com.happysg.radar.block.monitor;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared in-world geometry for rendering and picking ARAD/RWR monitor contacts. */
public final class AradMonitorGeometry {
    public static final float OUTER_RING_RADIUS = 0.40625f;
    public static final float MIDDLE_RING_RADIUS = 0.27083334f;
    public static final float INNER_RING_RADIUS = 0.1015625f;
    public static final float CONTACT_SCALE = 0.18f;
    public static final float PRIMARY_THREAT_SCALE = 0.135f;
    public static final float PICK_RADIUS = 0.11f;
    public static final float SINGLE_BLOCK_RENDER_SCALE = 0.9f;

    private AradMonitorGeometry() {
    }

    public static float radius(MonitorBlockEntity.RwrDisplayInfo contact) {
        float base = contact.exactLocked()
                ? INNER_RING_RADIUS
                : contact.withinRadarRange() ? MIDDLE_RING_RADIUS : OUTER_RING_RADIUS;
        return base + contact.radiusOffset();
    }

    public static MonitorProjection.DisplayPoint point(
            MonitorBlockEntity monitor,
            MonitorBlockEntity.RwrDisplayInfo contact
    ) {
        return point(contact.bearingDegrees(), monitorForwardBearing(monitor), radius(contact));
    }

    public static MonitorProjection.DisplayPoint point(
            float bearingDegrees,
            float forwardBearingDegrees,
            float radius
    ) {
        double radians = Math.toRadians(bearingDegrees - forwardBearingDegrees + 180.0f);
        return new MonitorProjection.DisplayPoint(
                (float) (-Math.sin(radians) * radius),
                (float) (-Math.cos(radians) * radius)
        );
    }

    public static MonitorProjection.Quad contactQuad(MonitorProjection.DisplayPoint point, int monitorSize) {
        return contactQuad(point, monitorSize, 1.0f);
    }

    public static MonitorProjection.Quad contactQuad(
            MonitorProjection.DisplayPoint point,
            int monitorSize,
            float scaleMultiplier
    ) {
        return scaledQuad(point, monitorSize, CONTACT_SCALE * scaleMultiplier);
    }

    public static MonitorProjection.Quad threatQuad(MonitorProjection.DisplayPoint point, int monitorSize) {
        return threatQuad(point, monitorSize, 1.0f);
    }

    public static MonitorProjection.Quad threatQuad(
            MonitorProjection.DisplayPoint point,
            int monitorSize,
            float scaleMultiplier
    ) {
        return scaledQuad(point, monitorSize, PRIMARY_THREAT_SCALE * scaleMultiplier);
    }

    public static MonitorProjection.Quad selectionQuad(MonitorProjection.DisplayPoint point, int monitorSize) {
        return MonitorProjection.fullSizeQuad(point, monitorSize);
    }

    public static MonitorProjection.Quad scaledQuad(
            MonitorProjection.DisplayPoint point,
            int monitorSize,
            float scale
    ) {
        float centerX = 1f - monitorSize / 2f + point.xOffset() * monitorSize;
        float centerZ = 1f - monitorSize / 2f + point.zOffset() * monitorSize;
        float half = monitorSize * scale * 0.5f;
        return new MonitorProjection.Quad(centerX - half, centerZ - half, centerX + half, centerZ + half);
    }

    public static float monitorForwardBearing(MonitorBlockEntity monitor) {
        Direction facing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        Vec3 forward = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (monitor.getShip() != null) {
            forward = PhysicsHandler.getWorldVecDirectionTransform(forward, monitor);
        }

        if (forward.x * forward.x + forward.z * forward.z < 1.0E-6) {
            return 0.0f;
        }
        double angle = Math.toDegrees(Math.atan2(forward.x, forward.z)) % 360.0;
        return (float) (angle < 0.0 ? angle + 360.0 : angle);
    }

    public static @Nullable MonitorProjection.DisplayPoint hitPoint(
            Level level,
            MonitorBlockEntity monitor,
            BlockHitResult hit
    ) {
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        if (hit.getDirection() != monitorFacing) {
            return null;
        }

        if (!(level.getBlockEntity(hit.getBlockPos()) instanceof MonitorBlockEntity hitMonitor)
                || !hitMonitor.getControllerPos().equals(monitor.getControllerPos())) {
            return null;
        }

        Direction right = monitorFacing.getClockWise();
        int size = monitor.getSize();
        BlockPos controllerPos = monitor.getControllerPos();
        Vec3 center = Vec3.atCenterOf(controllerPos)
                .add(right.getStepX() * (size - 1) / 2.0, (size - 1) / 2.0, right.getStepZ() * (size - 1) / 2.0);
        Vec3 delta = hit.getLocation().subtract(center);
        float renderScale = size == 1 ? SINGLE_BLOCK_RENDER_SCALE : 1.0f;
        float x = (float) (-delta.dot(new Vec3(right.getStepX(), 0.0, right.getStepZ())) / (size * renderScale));
        float z = (float) (-delta.y / (size * renderScale));
        return new MonitorProjection.DisplayPoint(x, z);
    }
}
