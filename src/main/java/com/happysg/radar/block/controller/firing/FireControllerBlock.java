package com.happysg.radar.block.controller.firing;


import com.happysg.radar.block.behavior.networks.WeaponNetwork;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class FireControllerBlock extends Block implements EntityBlock {

    public static BooleanProperty POWERED = BlockStateProperties.POWERED;
    public WeaponNetwork weaponNetwork;

    public FireControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new FireControllerBlockEntity(ModBlockEntityTypes.FIRE_CONTROLLER.get(), pPos, pState);
    }
    @Override
    public int getSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
        return pBlockState.getValue(POWERED) ? 15 : 0;
    }
    @Override
    public int getDirectSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
        return pBlockState.getSignal(pBlockAccess, pPos, pSide);
    }
    @Override
    public boolean isSignalSource(BlockState pState) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel sl && state.getBlock() != newState.getBlock() ) {
            breakAttachedDataLinks(level, pos);
            WeaponNetworkData data = WeaponNetworkData.get(sl);
            var view = data.getWeaponGroupViewFromEndpoint(sl.dimension(),pos);
            if(view != null){
                data.removeController(level.dimension(), pos);

            }
        }
        super.onRemove(state, level, pos, newState, isMoving);

    }
    private static void breakAttachedDataLinks(Level level, BlockPos controllerPos) {
        for (Direction dir : Direction.values()) {
            BlockPos linkPos = controllerPos.relative(dir);
            BlockState linkState = level.getBlockState(linkPos);

            if (!(linkState.getBlock() instanceof DataLinkBlock))
                continue;

            if (linkState.hasProperty(DataLinkBlock.LINK_STYLE)
                    && linkState.getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.CONTROLLER)
                continue;

            if (linkState.hasProperty(DataLinkBlock.FACING)) {
                Direction facing = linkState.getValue(DataLinkBlock.FACING);
                if (!linkPos.relative(facing.getOpposite()).equals(controllerPos))
                    continue;
            }

            level.destroyBlock(linkPos, true);
        }
    }



}
