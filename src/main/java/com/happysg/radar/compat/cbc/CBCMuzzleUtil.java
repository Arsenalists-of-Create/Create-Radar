package com.happysg.radar.compat.cbc;

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

        if (cannon instanceof MountedAutocannonContraption) {
            BlockPos cur = start.relative(dir).immutable();
            while (true) {
                BlockEntity be = cannon.presentBlockEntities.get(cur);
                if (!(be instanceof IAutocannonBlockEntity)) break;
                cur = cur.relative(dir);
            }
            return cur;
        }

        return null;
    }


    public static Vec3 getCBCSpawnAnchorWorld(PitchOrientedContraptionEntity poce) {
        if (poce == null) return Vec3.ZERO;

        if (!(poce.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        }

        BlockPos outside = getMuzzleExitLocal(cannon);
        if (outside == null) {
            return poce.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        }

        Direction dir = cannon.initialOrientation();

        // CBC spawns at centerOf(outside.relative(dir))
        BlockPos spawnAnchorLocal = outside.relative(dir);
        return poce.toGlobalVector(Vec3.atCenterOf(spawnAnchorLocal), 0);
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
