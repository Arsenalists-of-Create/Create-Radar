package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (level instanceof ServerLevel sl) {
            if (!isMoving) {
                DataLinkBlock.disconnectAttachedEndpointLinks(sl, pos);
            }
            NetworkData.get(sl).onEndpointRemoved(sl, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }
}
