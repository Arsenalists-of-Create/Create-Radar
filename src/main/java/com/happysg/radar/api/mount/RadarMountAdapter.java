package com.happysg.radar.api.mount;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

/**
 * Represents a weapon mount that Create Radar can aim.
 */
public interface RadarMountAdapter {
    /**
     * @return true while the underlying mount still exists and can be accessed.
     */
    boolean isValid();

    /**
     * @return true when the mount has an assembled weapon/contraption.
     */
    boolean isAssembled();

    /**
     * @return true if this mount supports yaw control.
     */
    boolean supportsYaw();

    /**
     * @return true if this mount supports pitch control.
     */
    boolean supportsPitch();

    /**
     * Current yaw in degrees.
     */
    double getYaw();

    /**
     * Current pitch in degrees.
     */
    double getPitch();

    /**
     * Commands the yaw of the mount.
     */
    void setYaw(double yaw);

    /**
     * Commands the pitch of the mount.
     */
    void setPitch(double pitch);

    /**
     * World-space position aiming calculations should originate from.
     */
    Vec3 getAimOrigin();

    /**
     * Block position used to identify and link this mount.
     */
    BlockPos getMountPos();
}
