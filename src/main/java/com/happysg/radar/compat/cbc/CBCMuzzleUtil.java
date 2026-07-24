package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc_at.CBCATCannonCompat;
import com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWCannonCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;

public final class CBCMuzzleUtil {
    private CBCMuzzleUtil() {}

    /**
     * Returns the first local BlockPos outside the muzzle.
     * - Big cannon: startPos then walk forward while IBigCannonBlockEntity
     * - Autocannon: startPos + dir then walk forward while IAutocannonBlockEntity
     */
    public static BlockPos getMuzzleExitLocal(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return null;

        Direction dir = cannon.initialOrientation();
        if (dir == null) return null;

        BlockPos start = cannon.getStartPos();
        if (start == null) start = BlockPos.ZERO;

        if (cannon instanceof MountedBigCannonContraption) {
            BlockPos cur = start.immutable();
            while (true) {
                BlockEntity be = cannon.presentBlockEntities.get(cur);
                if (!(be instanceof IBigCannonBlockEntity)) break;
                cur = cur.relative(dir);
            }
            return cur;
        }

        if (cannon instanceof MountedAutocannonContraption
                || Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATCannon(cannon)) {
            BlockPos cur = start.relative(dir).immutable();
            while (true) {
                BlockEntity be = cannon.presentBlockEntities.get(cur);
                if (!(be instanceof IAutocannonBlockEntity)
                        && !(Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATBarrel(be))) break;
                cur = cur.relative(dir);
            }
            return cur;
        }

        if (Mods.CBCMORESHELLS.isLoaded() && CBCMSCannonCompat.isCBCMSMount(cannon)) {
            BlockPos cur = start.immutable();
            while (CBCMSCannonCompat.isCBCMSBarrel(cannon.presentBlockEntities.get(cur))) {
                cur = cur.relative(dir);
            }
            return cur;
        }

        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon)) {
            return CBCMWCannonCompat.getMuzzleExitLocal(cannon);
        }

        return null;
    }


    public static Vec3 getCBCSpawnAnchorWorld(PitchOrientedContraptionEntity poce) {
        if (poce == null) return Vec3.ZERO;

        if (!(poce.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        }

        if (cannon instanceof MountedBigCannonContraption) {
            Vec3 center = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 1.0F);
            Vec3 forward = getForwardWorld(poce);
            if (forward.lengthSqr() < 1.0E-8) {
                return center;
            }
            return center.add(forward.scale(getBigCannonSpawnForwardOffset(cannon)));
        }

        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWCannonCompat.isCBCMWCannon(cannon)) {
            return CBCMWCannonCompat.getSpawnAnchorWorld(poce, cannon);
        }

        BlockPos outside = getMuzzleExitLocal(cannon);
        if (outside == null) {
            return poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        }

        Direction dir = cannon.initialOrientation();

        if (Mods.CBCMORESHELLS.isLoaded() && CBCMSCannonCompat.isCBCMSMount(cannon)) {
            Vec3 outsideCenter = poce.toGlobalVector(Vec3.atCenterOf(outside), 0);
            Vec3 forward = getForwardWorld(poce);
            return forward.lengthSqr() < 1.0E-8
                    ? outsideCenter
                    : outsideCenter.subtract(forward.scale(2.0));
        }

        BlockPos spawnAnchorLocal = outside.relative(dir);
        Vec3 anchor = poce.toGlobalVector(Vec3.atCenterOf(spawnAnchorLocal), 0);
        Vec3 center = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        Vec3 forward = anchor.subtract(center);
        if (forward.lengthSqr() < 1.0E-8) {
            return anchor;
        }

        double spawnBackoff = Mods.CBC_AT.isLoaded() && CBCATCannonCompat.isCBCATCannon(cannon) ? 1.5 : 2.0;
        return anchor.subtract(forward.normalize().scale(spawnBackoff));
    }

    public static double getBigCannonSpawnForwardOffset(AbstractMountedCannonContraption cannon) {
        return Math.max(0.0, CannonUtil.getBarrelLength(cannon));
    }

    /**
     * World-space forward direction of the contraption (unit vector).
     */
    public static Vec3 getForwardWorld(PitchOrientedContraptionEntity poce) {
        if (poce == null) return Vec3.ZERO;
        Vec3 center = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        Vec3 ahead  = poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO.relative(poce.getInitialOrientation())), 0);
        Vec3 v = ahead.subtract(center);
        return v.lengthSqr() < 1e-8 ? Vec3.ZERO : v.normalize();
    }
}
