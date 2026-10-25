package com.happysg.radar.block.monitor;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.happysg.radar.block.monitor.MonitorBlock.SHAPE;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;


//this is messy but couldn't figure out how to use Create MultiblockHelper
//todo make better
public class MonitorMultiBlockHelper {
    private static boolean forming;

    public static void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (forming || pIsMoving) return;

        Direction originFacing = pState.getValue(FACING);
        BlockPos bestController = null;
        int bestSize = 1;
        int maxSize = RadarConfig.server().monitorMaxSize.get();
        BlockPos min = pPos.offset(-maxSize, -maxSize, -maxSize);
        BlockPos max = pPos.offset(maxSize, maxSize, maxSize);

        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            BlockState candState = pLevel.getBlockState(candidate);
            if (!candState.is(ModBlocks.MONITOR.get())) continue;
            if (candState.getValue(FACING) != originFacing) continue;

            int size = getSize(pLevel, candidate);
            if (size <= bestSize) continue;
            if (!contains(candidate, originFacing, size, pPos)) continue;
            if (overlapsSameSizeOrLargerMulti(pLevel, candidate, originFacing, size)) continue;

            bestController = candidate.immutable();
            bestSize = size;
        }

        if (bestController != null) {
            formMulti(pState, pLevel, bestController, bestSize);
        }
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (ModBlocks.MONITOR.has(pNewState) && !pIsMoving)
            return;
        if (pLevel.getBlockEntity(pPos) instanceof MonitorBlockEntity monitor) {
            destroyMulti(pState, pLevel, pPos, monitor.getControllerPos(), monitor.getSize());
        }
    }

    static void formMulti(BlockState pState, Level pLevel, BlockPos pPos, int size) {
        formMulti(pState, pLevel, pPos, size, false);
    }

    private static void formMulti(BlockState pState, Level pLevel, BlockPos pPos, int size, boolean preserveSplitSuppression) {
        boolean wasForming = forming;
        forming = true;
        try {
            MonitorBlock.Shape shape;
            Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (size == 1) shape = MonitorBlock.Shape.SINGLE;
                    else if (i == 0 && j == 0) shape = MonitorBlock.Shape.LOWER_RIGHT;
                    else if (i == 0 && j == size - 1) shape = MonitorBlock.Shape.LOWER_LEFT;
                    else if (i == size - 1 && j == 0) shape = MonitorBlock.Shape.UPPER_RIGHT;
                    else if (i == size - 1 && j == size - 1) shape = MonitorBlock.Shape.UPPER_LEFT;
                    else if (i == 0) shape = MonitorBlock.Shape.LOWER_CENTER;
                    else if (i == size - 1) shape = MonitorBlock.Shape.UPPER_CENTER;
                    else if (j == 0) shape = MonitorBlock.Shape.MIDDLE_RIGHT;
                    else if (j == size - 1) shape = MonitorBlock.Shape.MIDDLE_LEFT;
                    else shape = MonitorBlock.Shape.CENTER;

                    BlockPos pos = pPos.above(i).relative(facing.getClockWise(), j);
                    pLevel.setBlockAndUpdate(pos, pState.setValue(SHAPE, shape));
                    if (pLevel.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
                        monitor.setControllerPos(pPos, size);

                        if (size > 1 && !preserveSplitSuppression) {
                            monitor.setSplitContactSuppressed(false);
                        }
                    }
                }
            }
        } finally {
            forming = wasForming;
        }
    }

    public static void restoreAfterSableMove(ServerLevel level, BlockPos controllerPos, int size) {
        if (size < 1 || size > RadarConfig.server().monitorMaxSize.get()) {
            return;
        }

        BlockState state = level.getBlockState(controllerPos);
        if (!state.is(ModBlocks.MONITOR.get())) {
            return;
        }

        formMulti(state, level, controllerPos, size);
    }

    private static void destroyMulti(BlockState pState, Level pLevel, BlockPos removedPos, BlockPos controllerPos, int size) {
        if (size == 1) return;

        boolean wasForming = forming;
        forming = true;

        try {
            BlockPos linkedSurvivor = null;

            if (pLevel instanceof ServerLevel sl) {
                NetworkData network = NetworkData.get(sl);
                ARADData arad = ARADData.get(sl);
                Direction right = pState.getValue(FACING).getClockWise();

                for (int i = 0; i < size && linkedSurvivor == null; i++) {
                    for (int j = 0; j < size; j++) {
                        BlockPos pos = controllerPos.above(i).relative(right, j);
                        if (pos.equals(removedPos)) continue;
                        if (!(sl.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) || !controllerPos.equals(monitor.getControllerPos())) continue;

                        DataLinkBlockEntity link = findAttachedMonitorLink(sl, pos, controllerPos);
                        if (link == null) continue;

                        linkedSurvivor = pos.immutable();
                        BlockPos linkPos = link.getBlockPos();

                        if (controllerPos.equals(network.peekEndpointForDataLink(sl.dimension(), linkPos))) {
                            network.retargetEndpoint(sl.dimension(), controllerPos, linkedSurvivor);
                        }

                        if (controllerPos.equals(arad.peekEndpointForDataLink(sl.dimension(), linkPos))) {
                            arad.updateMonitorPosition(sl.dimension(), controllerPos, linkedSurvivor);
                        }

                        link.target(linkedSurvivor);
                        break;
                    }
                }

                if (linkedSurvivor == null) {
                    var dim = sl.dimension();

                    BlockPos filtererPos = network.getEndpointOrigin(dim, controllerPos) == NetworkData.LinkOrigin.CONTACT ? network.getFiltererForEndpoint(dim, controllerPos) : null;
                    BlockPos rwrPos = arad.getEndpointOrigin(dim, controllerPos) == ARADData.LinkOrigin.CONTACT ? arad.getRwrForMonitor(dim, controllerPos) : null;

                    if (filtererPos != null || rwrPos != null) {
                        for (int i = 0; i < size && linkedSurvivor == null; i++) {
                            for (int j = 0; j < size; j++) {
                                BlockPos pos = controllerPos.above(i).relative(right, j);

                                if (pos.equals(removedPos)) continue;

                                if (!(sl.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) || !controllerPos.equals(monitor.getControllerPos())) {
                                    continue;
                                }

                                boolean networkContact = filtererPos != null && touchesExistingContact(sl, pos, controllerPos, filtererPos, network, arad, false);
                                boolean rwrContact = rwrPos != null && touchesExistingContact(sl, pos, controllerPos, rwrPos, network, arad, true);

                                if (!networkContact && !rwrContact) continue;
                                linkedSurvivor = pos.immutable();
                                if (networkContact) {
                                    network.retargetEndpoint(dim, controllerPos, linkedSurvivor);
                                }

                                if (rwrContact) {
                                    arad.updateMonitorPosition(dim, controllerPos, linkedSurvivor);
                                }

                                break;
                            }
                        }
                    }
                }
            }

            if (linkedSurvivor == null && pLevel instanceof ServerLevel sl) {
                NetworkData.get(sl).onEndpointRemoved(sl, controllerPos);
                ARADData.get(sl).onEndpointRemoved(sl, controllerPos);
            }

            if (pLevel.getBlockEntity(removedPos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
                monitor.onDataLinkRemoved();
                monitor.setControllerPos(removedPos, 1);
            }

            Direction facing = pState.getValue(FACING);
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    BlockPos pos = controllerPos.above(i).relative(facing.getClockWise(), j);
                    if (pos.equals(removedPos)) continue;

                    if (pLevel.getBlockEntity(pos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
                        monitor.onDataLinkRemoved();
                        monitor.setControllerPos(pos, 1);
                        monitor.setSplitContactSuppressed(!pos.equals(linkedSurvivor));
                        pLevel.setBlockAndUpdate(pos, pState.setValue(SHAPE, MonitorBlock.Shape.SINGLE));
                    }
                }
            }

            reformRemainingMonitors(
                    pState,
                    pLevel,
                    removedPos,
                    controllerPos,
                    size,
                    linkedSurvivor
            );

            if (pLevel instanceof ServerLevel sl && linkedSurvivor != null && sl.getBlockEntity(linkedSurvivor) instanceof MonitorBlockEntity survivor) {
                DataLinkBlockEntity link = findAttachedMonitorLink(sl, linkedSurvivor, survivor.getControllerPos());

                if (link != null) {
                    link.target(survivor.getControllerPos());
                }
            }

        } finally {
            forming = wasForming;
        }
    }

    private static void reformRemainingMonitors(
            BlockState state,
            Level level,
            BlockPos removedPos,
            BlockPos originalController,
            int originalSize,
            BlockPos linkedSurvivor) {

        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        Set<BlockPos> claimed = new HashSet<>();

        // The physically connected monitor gets priority
        // when selecting which smaller multiblock to form.
        if (linkedSurvivor != null) {
            boolean assigned = false;

            for (int newSize = originalSize - 1;
                 newSize >= 2 && !assigned;
                 newSize--) {

                for (int i = 0;
                     i <= originalSize - newSize && !assigned;
                     i++) {

                    for (int j = 0;
                         j <= originalSize - newSize;
                         j++) {

                        BlockPos candidate =
                                originalController.above(i).relative(right, j);

                        if (!contains(
                                candidate, facing, newSize, linkedSurvivor)) {
                            continue;
                        }

                        if (!canReformSquare(
                                level, candidate, facing,
                                newSize, removedPos, claimed)) {
                            continue;
                        }

                        claimSquare(claimed, candidate, right, newSize);
                        formMulti(state, level, candidate, newSize);

                        assigned = true;
                        break;
                    }
                }
            }
        }

        // Rebuild any other complete squares without overlapping
        // formations that have already been assigned.
        for (int newSize = originalSize - 1; newSize >= 2; newSize--) {
            for (int i = 0; i <= originalSize - newSize; i++) {
                for (int j = 0; j <= originalSize - newSize; j++) {

                    BlockPos candidate =
                            originalController.above(i).relative(right, j);

                    if (!canReformSquare(
                            level, candidate, facing,
                            newSize, removedPos, claimed)) {
                        continue;
                    }

                    claimSquare(claimed, candidate, right, newSize);
                    formMulti(state, level, candidate, newSize, true);
                }
            }
        }
    }

    private static boolean canReformSquare(Level level, BlockPos controller, Direction facing, int size, BlockPos removedPos, Set<BlockPos> claimed) {
        Direction right = facing.getClockWise();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                BlockPos pos = controller.above(i).relative(right, j);
                if (pos.equals(removedPos) || claimed.contains(pos)) {
                    return false;
                }

                BlockState state = level.getBlockState(pos);

                if (!state.is(ModBlocks.MONITOR.get()) || state.getValue(FACING) != facing) return false;
                if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) || monitor.getSize() != 1 || !pos.equals(monitor.getControllerPos())) {
                    return false;
                }
            }
        }

        return true;
    }

    private static void claimSquare(
            Set<BlockPos> claimed,
            BlockPos controller,
            Direction right,
            int size) {

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                claimed.add(controller.above(i).relative(right, j));
            }
        }
    }

    private static boolean touchesExistingContact(ServerLevel level, BlockPos candidate, BlockPos originalController, BlockPos source, NetworkData network, ARADData arad, boolean rwr) {
        var dim = level.dimension();
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = candidate.relative(direction);

            if (!level.hasChunkAt(adjacent)) continue;

            var adjacentEntity = level.getBlockEntity(adjacent);
            if (adjacentEntity == null) continue;

            BlockPos endpoint = adjacent;
            if (adjacentEntity instanceof MonitorBlockEntity monitor) {
                endpoint = monitor.getControllerPos();

                if (originalController.equals(endpoint)) continue;
            }

            if (adjacent.equals(source)) {
                return true;
            }

            if (rwr) {
                if (source.equals(arad.getRwrForMonitor(dim, endpoint))) {
                    return true;
                }
            } else {
                if (source.equals(network.getFiltererForEndpoint(dim, endpoint))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static DataLinkBlockEntity findAttachedMonitorLink(ServerLevel level, BlockPos monitorPos, BlockPos controllerPos) {
        NetworkData network = NetworkData.get(level);
        ARADData arad = ARADData.get(level);

        for (Direction direction : Direction.values()) {
            BlockPos linkPos = monitorPos.relative(direction);

            if (!(level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link)) continue;
            if (!monitorPos.equals(link.getSourcePosition())) continue;

            if (controllerPos.equals(network.peekEndpointForDataLink(level.dimension(), linkPos)) || controllerPos.equals(arad.peekEndpointForDataLink(level.dimension(), linkPos))) {
                return link;
            }
        }

        return null;
    }

    public static int getSize(Level pLevel, BlockPos pPos) {
        if (!pLevel.getBlockState(pPos).is(ModBlocks.MONITOR.get())) return 0;
        Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
        int potentialsize = 0;

        for (int i = 0; i < RadarConfig.server().monitorMaxSize.get(); i++) {
            AtomicBoolean valid = new AtomicBoolean(true);
            BlockPos.betweenClosed(pPos, pPos.above(i).relative(facing.getClockWise(), i)).forEach(p -> {
                BlockState state = pLevel.getBlockState(p);
                if (!state.is(ModBlocks.MONITOR.get()) || state.getValue(FACING) != facing) valid.set(false);
            });

            if (valid.get()) potentialsize = i + 1;
            else break;
        }

        if (potentialsize == 1) return 1;
        return potentialsize;

    }

    public static boolean isMulti(Level pLevel, BlockPos pos) {
        if (!pLevel.getBlockState(pos).is(ModBlocks.MONITOR.get()))
            return false;
        return getSize(pLevel, pos) > 1;
    }

    private static boolean contains(BlockPos controller, Direction facing, int size, BlockPos pos) {
        Direction right = facing.getClockWise();
        int upOffset = pos.getY() - controller.getY();
        int rightOffset = (pos.getX() - controller.getX()) * right.getStepX()
                + (pos.getZ() - controller.getZ()) * right.getStepZ();

        return upOffset >= 0 && upOffset < size
                && rightOffset >= 0 && rightOffset < size
                && pos.equals(controller.above(upOffset).relative(right, rightOffset));
    }

    private static boolean overlapsSameSizeOrLargerMulti(Level level, BlockPos controller, Direction facing, int size) {
        Direction right = facing.getClockWise();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                BlockPos pos = controller.above(i).relative(right, j);

                if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor)) {
                    continue;
                }

                int existingSize = monitor.getSize();
                BlockPos existingController = monitor.getControllerPos();

                if (existingSize <= 1 || existingController.equals(controller)) {
                    continue;
                }

                if (existingSize >= size) {
                    return true;
                }

                BlockPos existingOpposite = existingController.above(existingSize - 1).relative(right, existingSize - 1);
                if (!contains(controller, facing, size, existingController) || !contains(controller, facing, size, existingOpposite)) {
                    return true;
                }
            }
        }

        return false;
    }


    //todo add a size verification and reupdate multiblock if necessary
    public static void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {


    }
}
