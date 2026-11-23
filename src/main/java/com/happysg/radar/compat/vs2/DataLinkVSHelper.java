package com.happysg.radar.compat.vs2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import javax.annotation.Nullable;

public final class DataLinkVSHelper {
    private DataLinkVSHelper() {
    }

    public record SameShipTarget(long shipId, BlockPos targetOffsetShip) {
    }

    @Nullable
    public static SameShipTarget getSameShipTarget(ServerLevel level, BlockPos sourcePos, BlockPos targetPos) {
        LoadedShip sourceShip = VSGameUtilsKt.getLoadedShipManagingPos(level, sourcePos);
        LoadedShip targetShip = VSGameUtilsKt.getLoadedShipManagingPos(level, targetPos);
        if (sourceShip == null || targetShip == null || sourceShip.getId() != targetShip.getId()) {
            return null;
        }
        return new SameShipTarget(sourceShip.getId(), targetPos.subtract(sourcePos));
    }

    @Nullable
    public static BlockPos getSourcePosition(ServerLevel level, long shipId, BlockPos dataLinkPos, Direction direction) {
        LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, dataLinkPos);
        if (ship == null || ship.getId() != shipId) {
            return null;
        }
        return dataLinkPos.relative(direction);
    }

    @Nullable
    public static BlockPos getTargetPosition(ServerLevel level, long shipId, BlockPos dataLinkPos, BlockPos targetOffsetShip) {
        LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, dataLinkPos);
        if (ship == null || ship.getId() != shipId) {
            return null;
        }
        BlockPos targetPos = dataLinkPos.offset(targetOffsetShip);
        LoadedShip targetShip = VSGameUtilsKt.getLoadedShipManagingPos(level, targetPos);
        return targetShip != null && targetShip.getId() == shipId ? targetPos : null;
    }
}
