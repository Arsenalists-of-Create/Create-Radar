package com.happysg.radar.block.datalink;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.controller.WeaponContactUnlinkState;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.compat.sable.SableDataLinkRelocation;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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

    public static void disconnectSavedLink(ServerLevel level, BlockPos linkPos) {
        if (level == null || linkPos == null || !level.hasChunkAt(linkPos) || SableDataLinkRelocation.isRelocating(level, linkPos)) {
            return;
        }

        BlockState state = level.getBlockState(linkPos);
        if (!(state.getBlock() instanceof DataLinkBlock) || state.getValue(LINK_STYLE) != LinkStyle.RADAR) {
            return;
        }

        if (!(level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link)) {
            return;
        }

        if (SableDataLinkRelocation.isRelocatingEndpoint(level, link.getSourcePosition())) {
            return;
        }

        NetworkData network = NetworkData.get(level);
        ARADData arad = ARADData.get(level);

        BlockPos targetPos = network.peekEndpointForDataLink(level.dimension(), linkPos);

        if (targetPos == null) {
            targetPos = arad.peekEndpointForDataLink(level.dimension(), linkPos);
        }

        if (targetPos == null) {
            targetPos = link.getTargetPosition();
        }

        network.removeDataLinkAndCleanup(level.dimension(), linkPos, level, targetPos);
        arad.removeDataLinkAndCleanup(level.dimension(), linkPos, level);

        if (targetPos != null && !network.isEndpointLinked(level.dimension(), targetPos) && level.hasChunkAt(targetPos) && level.getBlockEntity(targetPos) instanceof AutoPitchControllerBlockEntity pitch) {
            pitch.suppressFiltererContact();
            pitch.onRadarNetworkDisconnected();
        }

        link.clearSavedConnection();
    }

    public static void disconnectAttachedEndpointLinks(ServerLevel level, BlockPos sourcePos) {
        if (level == null || sourcePos == null || SableDataLinkRelocation.isRelocatingEndpoint(level, sourcePos)) {
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos linkPos = sourcePos.relative(direction);
            if (!level.hasChunkAt(linkPos) || SableDataLinkRelocation.isRelocating(level, linkPos)) {
                continue;
            }

            BlockState linkState = level.getBlockState(linkPos);
            if (!(linkState.getBlock() instanceof DataLinkBlock) || linkState.getValue(LINK_STYLE) != LinkStyle.RADAR) {
                continue;
            }

            BlockPos actualSource = linkPos.relative(linkState.getValue(FACING).getOpposite());
            if (!sourcePos.equals(actualSource)) {
                continue;
            }

            disconnectSavedLink(level, linkPos);
        }
    }

    public static void disconnectAttachedControllerLinks(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null || SableDataLinkRelocation.isRelocatingEndpoint(level, controllerPos)) {
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos linkPos = controllerPos.relative(direction);

            if (!level.hasChunkAt(linkPos) || SableDataLinkRelocation.isRelocating(level, linkPos)) {
                continue;
            }

            BlockState state = level.getBlockState(linkPos);
            if (!(state.getBlock() instanceof DataLinkBlock) || state.getValue(LINK_STYLE) != LinkStyle.CONTROLLER) {
                continue;
            }

            BlockPos sourcePos = linkPos.relative(state.getValue(FACING).getOpposite());
            if (!controllerPos.equals(sourcePos)) {
                continue;
            }

            if (level.getBlockEntity(linkPos) instanceof DataLinkBlockEntity link) {
                link.clearSavedConnection();
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            ResourceKey<Level> dim = serverLevel.dimension();
            LinkStyle style = state.getValue(LINK_STYLE);
            boolean assemblyMove = isMoving || SableDataLinkRelocation.isRelocating(serverLevel, pos);
            BlockPos sourcePos = pos.relative(state.getValue(FACING).getOpposite());
            BlockEntity oldEntity = level.getBlockEntity(pos);
            DataLinkBlockEntity oldLink = oldEntity instanceof DataLinkBlockEntity link ? link : null;
            NetworkData data = NetworkData.get(serverLevel);

            if (!assemblyMove) {
                if (style == LinkStyle.CONTROLLER && serverLevel.hasChunkAt(sourcePos)) {
                    BlockEntity controllerEntity = serverLevel.getBlockEntity(sourcePos);

                    if (controllerEntity instanceof WeaponContactUnlinkState controller) {
                        controller.suppressWeaponContact();
                    }
                }

                if (style == LinkStyle.RADAR) {
                    BlockPos targetPos = oldLink != null ? oldLink.getTargetPosition() : data.peekEndpointForDataLink(dim, pos);
                    data.removeDataLinkAndCleanup(dim, pos, serverLevel, targetPos);

                    if (targetPos != null && !data.isEndpointLinked(dim, targetPos) && serverLevel.hasChunkAt(targetPos)) {
                        BlockEntity endpoint = serverLevel.getBlockEntity(targetPos);

                        if (endpoint instanceof AutoPitchControllerBlockEntity pitch) {
                            pitch.suppressFiltererContact();
                            pitch.onRadarNetworkDisconnected();
                        }
                    }
                }

                ARADData.get(serverLevel).removeDataLinkAndCleanup(dim, pos, serverLevel);
            }

            WeaponNetworkRuntime.get(serverLevel).unregister(pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }


}
