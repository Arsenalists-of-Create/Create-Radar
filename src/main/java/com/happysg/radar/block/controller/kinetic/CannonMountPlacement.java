package com.happysg.radar.block.controller.kinetic;

import com.happysg.radar.compat.cbc.CannonMountContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Selects a valid adjacent CBC mount endpoint for controller placement.
 */
public final class CannonMountPlacement {
    private CannonMountPlacement() {
    }

    @Nullable
    public static Direction findPreferredMount(
            BlockPlaceContext context, Direction... directions
    ) {
        BlockPos placementPos = context.getClickedPos();
        Direction clickedDirection = context.getClickedFace().getOpposite();
        Player player = context.getPlayer();
        Vec3 look = player == null ? Vec3.ZERO : player.getLookAngle();

        Direction bestDirection = null;
        double bestScore = -Double.MAX_VALUE;
        for (Direction direction : directions) {
            if (CannonMountContext.resolveEndpoint(
                    context.getLevel(), placementPos.relative(direction)) == null) {
                continue;
            }
            if (direction == clickedDirection) {
                return direction;
            }

            double score = player == null
                    ? 0
                    : look.x * direction.getStepX()
                    + look.y * direction.getStepY()
                    + look.z * direction.getStepZ();
            if (bestDirection == null || score > bestScore) {
                bestDirection = direction;
                bestScore = score;
            }
        }
        return bestDirection;
    }
}
