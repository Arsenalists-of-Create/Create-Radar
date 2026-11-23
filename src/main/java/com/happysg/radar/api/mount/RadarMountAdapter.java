package com.happysg.radar.api.mount;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/**
 * Represents a weapon mount that Create Radar can aim.
 */
public interface RadarMountAdapter {
    /**
     * @return {@code true} while the underlying mount still exists and can
     * be safely accessed
     */
    boolean isValid();

    /**
     * @return {@code true} when the mount currently has an assembled
     * weapon or contraption
     */
    boolean isAssembled();

    /**
     * @return {@code true} if this mount supports direct yaw control
     */
    boolean supportsYaw();

    /**
     * @return {@code true} if this mount supports direct pitch control
     */
    boolean supportsPitch();

    /**
     * Returns the mount's current yaw.
     *
     * @return yaw in degrees
     */
    double getYaw();

    /**
     * Returns the mount's current pitch.
     *
     * @return pitch in degrees
     */
    double getPitch();

    /**
     * Commands the mount to the supplied yaw.
     *
     * @param yaw yaw in degrees
     */
    void setYaw(double yaw);

    /**
     * Commands the mount to the supplied pitch.
     *
     * @param pitch pitch in degrees
     */
    void setPitch(double pitch);

    /**
     * Returns the current world-space point from which aiming calculations
     * should originate.
     *
     * <p>This may change over time for moving mounts.</p>
     *
     * @return world-space aiming origin, or {@code null} if one cannot
     * currently be resolved
     */
    @Nullable
    Vec3 getAimOrigin();

    /**
     * Returns the canonical block position used to identify and link this
     * mount.
     *
     * <p>This position should remain stable for the lifetime of the mount
     * and must not be {@code null}.</p>
     *
     * @return canonical mount position
     */
    BlockPos getMountPos();
}
