package com.happysg.radar.block.controller.track;//package com.happysg.radar.block.controller.track;
//
//import com.happysg.radar.registry.ModBlockEntityTypes;
//import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
//import com.simibubi.create.foundation.block.IBE;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.Direction;
//import net.minecraft.world.level.LevelReader;
//import net.minecraft.world.level.block.entity.BlockEntityType;
//import net.minecraft.world.level.block.state.BlockState;
//
//public class TrackControllerBlock extends HorizontalKineticBlock implements IBE<TrackControllerBlockEntity> {
//
//    public TrackControllerBlock(Properties properties) {
//        super(properties);
//    }
//
//
//    @Override
//    public Direction.Axis getRotationAxis(BlockState state) {
//        return Direction.Axis.Y;
//    }
//
//    @Override
//    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
//        return face == state.getValue(HORIZONTAL_FACING).getOpposite() ||
//                face.getAxis() == state.getValue(HORIZONTAL_FACING).getCounterClockWise().getAxis();
//    }
//
//    @Override
//    public Class<TrackControllerBlockEntity> getBlockEntityClass() {
//        return TrackControllerBlockEntity.class;
//    }
//
//    @Override
//    public BlockEntityType<? extends TrackControllerBlockEntity> getBlockEntityType() {
//        return ModBlockEntityTypes.TRACK_CONTROLLER.get();
//    }
//}
