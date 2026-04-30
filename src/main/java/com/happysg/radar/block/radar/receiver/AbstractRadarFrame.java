package com.happysg.radar.block.radar.receiver;

import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Predicate;

public class AbstractRadarFrame extends WrenchableDirectionalBlock {
    public VoxelShaper shaper;
    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public AbstractRadarFrame(Properties properties, VoxelShaper shaper) {
        super(properties);
        this.shaper = shaper;
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState pState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        return shaper.get(pState.getValue(FACING));
    }

    @Override
    @ParametersAreNonnullByDefault
    public @NotNull net.minecraft.world.ItemInteractionResult useItemOn(ItemStack heldItem, BlockState pState, net.minecraft.world.level.Level pLevel, BlockPos pPos, Player pPlayer, net.minecraft.world.InteractionHand pHand, BlockHitResult pHit) {
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);

        if (!pPlayer.isShiftKeyDown() && pPlayer.mayBuild()) {
            if (placementHelper.matchesItem(heldItem)) {
                placementHelper.getOffset(pPlayer, pLevel, pState, pPos, pHit)
                        .placeInWorld(pLevel, (BlockItem) heldItem.getItem(), pPlayer, pHand, pHit);
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
        }

        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return i -> ModBlocks.RADAR_PLATE_BLOCK.isIn(i) || ModBlocks.RADAR_DISH_BLOCK.isIn(i);
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return s -> s.getBlock() instanceof AbstractRadarFrame;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
                                         BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(),
                    state.getValue(SailBlock.FACING)
                            .getAxis(),
                    dir -> world.getBlockState(pos.relative(dir))
                            .canBeReplaced());

            if (directions.isEmpty())
                return PlacementOffset.fail();
            else {
                return PlacementOffset.success(pos.relative(directions.get(0)),
                        s -> s.setValue(FACING, state.getValue(FACING)));
            }
        }
    }
}