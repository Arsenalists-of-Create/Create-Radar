package com.happysg.radar.block.datalink;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class DataLinkBlock extends WrenchableDirectionalBlock implements IBE<DataLinkBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Logger LOGGER = LogUtils.getLogger();
    public enum LinkStyle implements StringRepresentable {
        RADAR("radar"),
        CONTROLLER("controller");

        private final String name;
        LinkStyle(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
        }


        public static final EnumProperty<LinkStyle> LINK_STYLE =
                EnumProperty.create("link_style", LinkStyle.class);


    public DataLinkBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(LINK_STYLE, LinkStyle.RADAR));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LINK_STYLE);
    }



    

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return AllShapes.DATA_GATHERER.get(pState.getValue(FACING));
    }


    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        if (placed == null)
            placed = this.defaultBlockState();

        return placed
                .setValue(FACING, context.getClickedFace())
                .setValue(LINK_STYLE, LinkStyle.RADAR);
    }

    @Override
    public Class<DataLinkBlockEntity> getBlockEntityClass() {
        return DataLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DataLinkBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.RADAR_LINK.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if(Mods.SABLE.isLoaded()) return;
            ResourceKey<Level> dim = serverLevel.dimension();
            Direction supportFace = state.getValue(FACING);

            NetworkData.get(serverLevel).removeDataLinkAndCleanup(dim, pos, serverLevel);
            WeaponNetworkData.get(serverLevel).removeDataLinkAndCleanup(dim, pos);

            if (state.getValue(LINK_STYLE) == LinkStyle.RADAR) {
                NetworkData.get(serverLevel).onEndpointRemoved(serverLevel, pos.relative(supportFace.getOpposite()));
            }

            if (state.getValue(LINK_STYLE) == LinkStyle.CONTROLLER) {
                WeaponNetworkData.get(serverLevel).removeController(dim, pos.relative(supportFace.getOpposite()));
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }


}
