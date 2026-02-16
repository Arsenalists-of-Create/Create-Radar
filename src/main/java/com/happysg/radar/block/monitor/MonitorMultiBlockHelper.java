package com.happysg.radar.block.monitor;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.happysg.radar.block.monitor.MonitorBlock.SHAPE;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;


//this is messy but couldn't figure out how to use Create MultiblockHelper
//todo make better
public class MonitorMultiBlockHelper {
    public static void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (pState.getValue(SHAPE) != MonitorBlock.Shape.SINGLE && !pIsMoving)
            return;

        Direction originFacing = pState.getValue(FACING);

        BlockPos.betweenClosedStream(new AABB(pPos).inflate(RadarConfig.server().monitorMaxSize.get()))
                .forEach(candidate -> {
                    BlockState candState = pLevel.getBlockState(candidate);
                    if (!candState.is(ModBlocks.MONITOR.get())) return;
                    if (candState.getValue(FACING) != originFacing) return;

                    if (pLevel.getBlockEntity(candidate) instanceof MonitorBlockEntity monitor) {
                        int size = getSize(pLevel, candidate);
                        if (size > 1) {
                            formMulti(pState, pLevel, monitor.getControllerPos(), size);
                        }
                    }});
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (ModBlocks.MONITOR.has(pNewState) && !pIsMoving)
            return;
        if (pLevel.getBlockEntity(pPos) instanceof MonitorBlockEntity monitor) {
            destroyMulti(pState, pLevel, pPos, monitor.getControllerPos(), monitor.getSize());
        }
    }

    static void formMulti(BlockState pState, Level pLevel, BlockPos pPos, int size) {
        MonitorBlock.Shape shape;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == 0 && j == 0) shape = MonitorBlock.Shape.LOWER_RIGHT;
                else if (i == 0 && j == size - 1) shape = MonitorBlock.Shape.LOWER_LEFT;
                else if (i == size - 1 && j == 0) shape = MonitorBlock.Shape.UPPER_RIGHT;
                else if (i == size - 1 && j == size - 1) shape = MonitorBlock.Shape.UPPER_LEFT;
                else if (i == 0) shape = MonitorBlock.Shape.LOWER_CENTER;
                else if (i == size - 1) shape = MonitorBlock.Shape.UPPER_CENTER;
                else if (j == 0) shape = MonitorBlock.Shape.MIDDLE_RIGHT;
                else if (j == size - 1) shape = MonitorBlock.Shape.MIDDLE_LEFT;
                else shape = MonitorBlock.Shape.CENTER;

                Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
                pLevel.setBlockAndUpdate(pPos.above(i).relative(facing.getClockWise(), j), pState.setValue(SHAPE, shape));
                if (pLevel.getBlockEntity(pPos.above(i).relative(facing.getClockWise(), j)) instanceof MonitorBlockEntity monitor) {
                    monitor.setControllerPos(pPos, size);
                }
            }
        }
    }

    private static void destroyMulti(BlockState pState, Level pLevel, BlockPos removedPos, BlockPos controllerPos, int size) {
        if (size == 1)
            return;
        if (pLevel.getBlockEntity(removedPos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
            monitor.setControllerPos(removedPos, 1);
        }
        Direction facing = pState.getValue(FACING);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                BlockPos pos = controllerPos.above(i).relative(facing.getClockWise(), j);
                if (pos.equals(removedPos))
                    continue;
                if (pLevel.getBlockEntity(pos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
                    monitor.setControllerPos(pos, 1);
                    monitor.onDataLinkRemoved();
                    pLevel.setBlockAndUpdate(pos, pState.setValue(SHAPE, MonitorBlock.Shape.SINGLE));
                }
            }
        }
    }



    public static int getSize(Level pLevel, BlockPos pPos) {
        if (!pLevel.getBlockState(pPos).is(ModBlocks.MONITOR.get()))
            return 0;
        Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
        int potentialsize = 0;
        for (int i = 0; i < RadarConfig.server().monitorMaxSize.get(); i++) {
            AtomicBoolean valid = new AtomicBoolean(true);
            BlockPos.betweenClosed(pPos, pPos.above(i).relative(facing.getClockWise(), i)).forEach(p -> {
                if (!pLevel.getBlockState(p).is(ModBlocks.MONITOR.get()))
                    valid.set(false);
            });
            if (valid.get())
                potentialsize = i + 1;
            else
                break;
        }
        if (potentialsize == 1)
            return 1;

        for (int i = 0; i < potentialsize; i++) {
            for (int j = 0; j < potentialsize; j++) {
                BlockEntity be = pLevel.getBlockEntity(pPos.above(i).relative(facing.getClockWise(), j));
                if (!(be instanceof MonitorBlockEntity monitor && monitor.getSize() < potentialsize))
                    return Math.max(1, Math.min(i, j));

            }
        }

        return potentialsize;

    }

    public static boolean isMulti(Level pLevel, BlockPos pos) {
        if (!pLevel.getBlockState(pos).is(ModBlocks.MONITOR.get()))
            return false;
        return getSize(pLevel, pos) > 1;
    }


    //todo add a size verification and reupdate multiblock if necessary
    public static void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {


    }
}