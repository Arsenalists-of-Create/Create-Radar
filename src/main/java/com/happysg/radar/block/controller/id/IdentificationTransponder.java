package com.happysg.radar.block.controller.id;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class IdentificationTransponder extends WrenchableDirectionalBlock {
    public IdentificationTransponder(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, @NotNull Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Mods.VALKYRIENSKIES.isLoaded()) {
            player.displayClientMessage(Component.translatable("create_radar.id_block.not_on_vs2"), true);
            return ItemInteractionResult.SUCCESS;
        }

        VS2IDHandler.use(state, level, pos, player, hand, hit);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            VS2IDHandler.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
    }
    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return AllShapes.DATA_GATHERER.get(pState.getValue(FACING));
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos onPos = pos.relative(context.getClickedFace().getOpposite());
        BlockState onState = level.getBlockState(onPos);


        return placed.setValue(FACING, context.getClickedFace());


    }
}
