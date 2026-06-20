package com.happysg.radar.block.monitor;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MonitorProjection {
    public static final float FIT_SCALE = 0.75f;

    private final MonitorBlockEntity monitor;
    private final Direction monitorFacing;
    private final Vec3 center;
    private final float halfSpan;
    private final SubLevelAccess ship;

    private MonitorProjection(MonitorBlockEntity monitor, Direction monitorFacing, Vec3 center, float halfSpan, SubLevelAccess ship) {
        this.monitor = monitor;
        this.monitorFacing = monitorFacing;
        this.center = center;
        this.halfSpan = Math.max(1f, halfSpan);
        this.ship = ship;
    }

    public static MonitorProjection create(MonitorBlockEntity monitor) {
        return create(monitor, null);
    }

    public static MonitorProjection create(MonitorBlockEntity monitor, View view) {
        List<MonitorBlockEntity.RadarDisplayInfo> radars = monitor.getRunningRadarInfos();
        Direction facing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        boolean renderRelative = radars.stream().anyMatch(MonitorBlockEntity.RadarDisplayInfo::renderRelativeToMonitor);
        SubLevelAccess ship = Mods.SABLE.isLoaded() && renderRelative ? monitor.getShip() : null;

        if (view != null) {
            return new MonitorProjection(monitor, facing, new Vec3(view.centerX(), 0, view.centerZ()), view.halfSpan(), ship);
        }

        if (radars.isEmpty()) {
            Vec3 fallback = monitor.getRadarCenterPos();
            if (fallback == null) fallback = Vec3.atCenterOf(monitor.getControllerPos());
            Vec3 center = framePosition(monitor, ship, fallback);
            return new MonitorProjection(monitor, facing, center, Math.max(1f, monitor.getRange()), ship);
        }

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (MonitorBlockEntity.RadarDisplayInfo radar : radars) {
            Vec3 p = framePosition(monitor, ship, radar.center());
            double r = Math.max(1f, radar.range());
            minX = Math.min(minX, p.x - r);
            minZ = Math.min(minZ, p.z - r);
            maxX = Math.max(maxX, p.x + r);
            maxZ = Math.max(maxZ, p.z + r);
        }

        Vec3 center = new Vec3((minX + maxX) * 0.5, 0, (minZ + maxZ) * 0.5);
        float halfSpan = (float) Math.max(maxX - minX, maxZ - minZ) * 0.5f;
        return new MonitorProjection(monitor, facing, center, halfSpan, ship);
    }

    public DisplayPoint project(Vec3 worldPos) {
        Vec3 p = framePosition(monitor, ship, worldPos);
        return projectFramePosition(p.x, p.z);
    }

    public DisplayPoint projectFramePosition(double frameX, double frameZ) {
        Vec3 p = new Vec3(frameX, 0, frameZ);
        Vec3 rel = p.subtract(center);
        float xOff = calculateOffset(rel, true) * FIT_SCALE;
        float zOff = calculateOffset(rel, false) * FIT_SCALE;
        return new DisplayPoint(xOff, zOff);
    }

    public float displayScale(float worldRadius) {
        return Math.max(0.01f, worldRadius / halfSpan);
    }

    public float halfSpan() {
        return halfSpan;
    }

    public View view() {
        return new View(center.x, center.z, halfSpan);
    }

    public View viewCenteredOn(Vec3 worldPos, float newHalfSpan) {
        Vec3 frameCenter = framePosition(monitor, ship, worldPos);
        return new View(frameCenter.x, frameCenter.z, Math.max(1f, newHalfSpan));
    }

    public View zoomAround(DisplayPoint anchor, float newHalfSpan) {
        float clampedHalfSpan = Math.max(1f, newHalfSpan);
        Vec3 frameAnchor = unproject(anchor);
        Vec3 rel = frameVectorFromDisplay(anchor, clampedHalfSpan);
        Vec3 newCenter = frameAnchor.subtract(rel);
        return new View(newCenter.x, newCenter.z, clampedHalfSpan);
    }

    public View panByDisplayDelta(float xOffsetDelta, float zOffsetDelta) {
        Vec3 frameDelta = frameVectorFromDisplay(new DisplayPoint(xOffsetDelta, zOffsetDelta), halfSpan);
        Vec3 newCenter = center.subtract(frameDelta);
        return new View(newCenter.x, newCenter.z, halfSpan);
    }

    public DisplayPoint displayPointFromUi(double mouseX, double mouseY, int left, int top, int uiSize) {
        float xOffset = (float) ((mouseX - left) / uiSize - 0.5);
        float zOffset = (float) ((mouseY - top) / uiSize - 0.5);
        return new DisplayPoint(xOffset, zOffset);
    }

    public Vec3 unproject(DisplayPoint point) {
        return center.add(frameVectorFromDisplay(point, halfSpan));
    }

    private Vec3 frameVectorFromDisplay(DisplayPoint point, float span) {
        double displayX = point.xOffset() / FIT_SCALE * 2f * span;
        double displayZ = point.zOffset() / FIT_SCALE * 2f * span;

        double relX;
        double relZ;

        if (monitorFacing.getAxis() == Direction.Axis.Z) {
            relX = (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) ? -displayX : displayX;
            relZ = (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) ? -displayZ : displayZ;
        } else {
            relZ = (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) ? -displayX : displayX;
            relX = (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) ? -displayZ : displayZ;
        }

        return new Vec3(relX, 0, relZ);
    }

    private float calculateOffset(Vec3 relativePos, boolean isXOffset) {
        float offset;

        if (isXOffset) {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.x()) : getOffset(relativePos.z());

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) {
                offset = -offset;
            }
        } else {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.z()) : getOffset(relativePos.x());

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) {
                offset = -offset;
            }
        }

        return offset;
    }

    private float getOffset(double coordinate) {
        return (float) (coordinate / halfSpan) / 2f;
    }

    private static Vec3 framePosition(MonitorBlockEntity monitor, SubLevelAccess ship, Vec3 worldPos) {
        if (ship != null) {
            return PhysicsHandler.getShipVec(worldPos, monitor);
        }
        return worldPos;
    }

    public record DisplayPoint(float xOffset, float zOffset) {
        public boolean outside() {
            return Math.abs(xOffset) > 0.5f || Math.abs(zOffset) > 0.5f;
        }
    }

    public record View(double centerX, double centerZ, float halfSpan) {}

    public record Quad(float minX, float minZ, float maxX, float maxZ) {}

    public static Quad fullSizeQuad(DisplayPoint point, int monitorSize) {
        return scaledQuad(point, monitorSize, 1f);
    }

    public static Quad scaledQuad(DisplayPoint point, int monitorSize, float scale) {
        float clampedScale = Math.max(0.01f, scale);
        float centerX = 1f - monitorSize / 2f + point.xOffset() * monitorSize;
        float centerZ = 1f - monitorSize / 2f + point.zOffset() * monitorSize;
        float half = monitorSize * clampedScale * 0.5f;
        return new Quad(centerX - half, centerZ - half, centerX + half, centerZ + half);
    }
}
