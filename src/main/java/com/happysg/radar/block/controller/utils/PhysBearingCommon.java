package com.happysg.radar.block.controller.utils;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;

public final class PhysBearingCommon {
    private PhysBearingCommon() {}

    public static void ensureFollowAngleMode(PhysBearingBlockEntity mount) {
        ScrollOptionBehaviour<ContraptionController.LockedMode> mode = mount.getMovementMode();
        if (mode != null && mode.getValue() != ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal()) {
            mode.setValue(ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal());
        }
    }

    @Nullable
    public static SubLevelAccess getShipIfPresent(Level level, net.minecraft.core.BlockPos pos) {
        if (level == null) return null;
        if (!Mods.SABLE.isLoaded()) return null;
        return SableCompanion.INSTANCE.getContaining(level, pos);
    }

    public static Vec3 toShipSpace(SubLevelAccess ship, Vec3 worldPos) {
        Vector3d v = ship.logicalPose().transformPositionInverse(new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        return new Vec3(v.x(), v.y(), v.z());
    }

    public static double wrap360(double deg) {
        deg %= 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    public static double wrap180(double deg) {
        deg = wrap360(deg);
        if (deg >= 180.0) deg -= 360.0;
        return deg;
    }

    public static double shortestDelta(double from, double to) {
        return ((to - from + 540.0) % 360.0) - 180.0;
    }

    public static double unwrapNear(double lastContinuous, double newWrapped) {
        double lastWrapped = wrap360(lastContinuous);
        return lastContinuous + shortestDelta(lastWrapped, newWrapped);
    }

    public static boolean isAngleInWrappedRange(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);

        if (min <= max) {
            return angle >= min && angle <= max;
        }
        return angle >= min || angle <= max;
    }

    public static double wrappedDistance(double a, double b) {
        double d = Math.abs(wrap360(a) - wrap360(b));
        return Math.min(d, 360.0 - d);
    }

    public static double clampAngleToWrappedRange(double angle, double min, double max) {
        angle = wrap360(angle);
        min = wrap360(min);
        max = wrap360(max);

        if (wrappedDistance(min, max) < 1e-6) {
            return angle;
        }

        if (isAngleInWrappedRange(angle, min, max)) {
            return angle;
        }

        double dToMin = wrappedDistance(angle, min);
        double dToMax = wrappedDistance(angle, max);
        return dToMin <= dToMax ? min : max;
    }

    public static double controllerYawForCardinalDirection(net.minecraft.core.Direction d) {
        return switch (d) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
    }
}