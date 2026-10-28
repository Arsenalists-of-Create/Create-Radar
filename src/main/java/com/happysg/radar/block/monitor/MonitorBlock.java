package com.happysg.radar.block.monitor;

import com.happysg.radar.api.monitor.client.MonitorScreenRegistry;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.compat.sable.SableMonitorRelocation;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class MonitorBlock extends HorizontalDirectionalBlock implements IBE<MonitorBlockEntity> {
    public MonitorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.SINGLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull BlockState pOldState, boolean pMovedByPiston) {
        super.onPlace(pState, pLevel, pPos, pOldState, pMovedByPiston);
        MonitorMultiBlockHelper.onPlace(pState, pLevel, pPos, pOldState, pMovedByPiston);
    }

    @Override
    public void onRemove(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull BlockState pNewState, boolean pIsMoving) {
        boolean sableRelocation = pLevel instanceof ServerLevel serverLevel && SableMonitorRelocation.isRelocating(serverLevel, pPos);

        if (pIsMoving || sableRelocation) {
            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
            return;
        }

        if (!pState.is(pNewState.getBlock()) && pLevel instanceof ServerLevel serverLevel && pLevel.getBlockEntity(pPos) instanceof MonitorBlockEntity monitor) {
            ARADTargetDesignationHandler.clear(serverLevel, monitor.getController());
        }

        MonitorMultiBlockHelper.onRemove(pState, pLevel, pPos, pNewState, false);

        if (!pState.is(pNewState.getBlock()) && pLevel instanceof ServerLevel sl) {
            DataLinkBlock.breakAttachedEndpointLinks(sl, pPos);

            NetworkData.get(sl).onEndpointRemoved(sl, pPos);
            ARADData.get(sl).onEndpointRemoved(sl, pPos);
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        //todo deal with this for contraption movements
        MonitorMultiBlockHelper.onNeighborChange(state, level, pos, neighbor);
        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            Player player,
            @NotNull BlockHitResult hit) {

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MonitorBlockEntity monitor)) {
            return InteractionResult.PASS;
        }

        MonitorBlockEntity controller = monitor.getController();
        if (controller == null) {
            return InteractionResult.PASS;
        }

        if (controller.isAradLinked()) {
            return onBlockEntityUse(
                    level,
                    pos,
                    be -> MonitorInputHandler.onUse(
                            be.getController(),
                            player,
                            InteractionHand.MAIN_HAND,
                            hit,
                            state.getValue(FACING)
                    )
            );
        }

        if (level.isClientSide) {
            boolean guiByDefault = RadarConfig.client().useGuiByDefault.get();
            boolean openGui = guiByDefault != player.isShiftKeyDown();

            if (openGui) {
                openMonitorScreenClient(monitor);
            } else {
                MonitorInputHandler.onUse(
                        controller,
                        player,
                        InteractionHand.MAIN_HAND,
                        hit,
                        state.getValue(FACING)
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                       @NotNull BlockState state,
                                                       @NotNull Level level,
                                                       @NotNull BlockPos pos,
                                                       @NotNull Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public enum Shape implements StringRepresentable {
        SINGLE, CENTER, LOWER_CENTER, LOWER_LEFT, LOWER_RIGHT, UPPER_CENTER, UPPER_LEFT, UPPER_RIGHT, MIDDLE_LEFT, MIDDLE_RIGHT;

        @Override
        public @NotNull String getSerializedName() {
            return Lang.asId(name());
        }
    }

    @Override
    public Class<MonitorBlockEntity> getBlockEntityClass() {
        return MonitorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MonitorBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.MONITOR.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(SHAPE);
        super.createBlockStateDefinition(builder);
    }
    private static boolean isGuiHotspot(MonitorBlockEntity anyPiece, BlockHitResult hit) {
        if (anyPiece == null || anyPiece.getLevel() == null) return false;

        MonitorBlockEntity controller = anyPiece.isController() ? anyPiece : anyPiece.getController();
        if (controller == null) return false;

        Direction screenFace = controller.getBlockState().getValue(FACING);

        // i only accept clicks on the actual screen face
        if (hit.getDirection() != screenFace) return false;

        BlockPos controllerPos = controller.getControllerPos();
        if (controllerPos == null) controllerPos = controller.getBlockPos();

        // i keep the hotspot on the controller tile (your LOWER_RIGHT)
        if (!hit.getBlockPos().equals(controllerPos)) return false;

        int size = controller.getSize();
        if (size <= 0) return false;

        // i use your sizing rule: ~3 px on 1x1, ~6 px on 3x3 (and bigger screens still get 6)
        int stripPx = (size == 1) ? 3 : 6;
        float epsY = stripPx / 16f; // fraction of a block face

        // i compute local coords inside the clicked block (0..1)
        Vec3 local = hit.getLocation().subtract(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());

        // v: 0 top -> 1 bottom
        float v = 1f - (float) local.y;

        // bottom strip: any u, just v near bottom
        return v >= 1f - epsY;
    }


    private static void openMonitorScreenClient(MonitorBlockEntity anyPiece) {
        // i pass only the position across, not the block entity
        BlockPos pos = anyPiece.getBlockPos();

        Client.openMonitorScreen(pos);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static final class Client {
        static void openMonitorScreen(BlockPos clickedPos) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            // i re-fetch the BE client-side so i never carry server/common objects through DistExecutor
            BlockEntity be = mc.level.getBlockEntity(clickedPos);
            if (!(be instanceof MonitorBlockEntity anyPiece)) return;

            MonitorBlockEntity controller = anyPiece.isController() ? anyPiece : anyPiece.getController();
            if (controller == null) return;
            if (controller.isAradLinked()) return;

            BlockPos controllerPos = controller.getControllerPos();
            if (controllerPos == null) {
                controllerPos = controller.getBlockPos();
            }

            var customScreen = MonitorScreenRegistry.create(mc.level, controllerPos, controller.getRunningRadarSnapshots());

            if (customScreen != null) {
                mc.setScreen(customScreen);
                return;
            }

            mc.setScreen(new MonitorScreen(controllerPos));
        }
    }



}
