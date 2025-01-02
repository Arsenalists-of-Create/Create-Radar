package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RadarBearingBlock extends BearingBlock implements IBE<RadarBearingBlockEntity> {

    public RadarBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, Direction.UP);
    }

    @Override
    public Class<RadarBearingBlockEntity> getBlockEntityClass() {
        return RadarBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadarBearingBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.RADAR_BEARING.get();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
            if (be instanceof RadarBearingBlockEntity radar) {
                radar.disassemble();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return super.getRenderShape(pState);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadarBearingBlockEntity radar) {
                if (radar.isRunning()) {
                    radar.disassemble();
                } else {
                    radar.assemble();
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

}
