package com.happysg.radar.block.monitor;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlockEntityTypes;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;


public class MonitorBlock extends HorizontalDirectionalBlock implements IBE<MonitorBlockEntity> {
    public static final com.mojang.serialization.MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public MonitorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, Shape.SINGLE));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection()
                        .getOpposite());
    }

    @Override
    public void onPlace(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull BlockState pOldState, boolean pMovedByPiston) {
        super.onPlace(pState, pLevel, pPos, pOldState, pMovedByPiston);
        MonitorMultiBlockHelper.onPlace(pState, pLevel, pPos, pOldState, pMovedByPiston);
    }

    @Override
    public void onRemove(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull BlockState pNewState, boolean pIsMoving) {
        if (pBlockStateHasBlockEntity(pState, pNewState)) {
             MonitorMultiBlockHelper.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
             if (pLevel instanceof ServerLevel sl) {
                 NetworkData.get(sl).onEndpointRemoved(sl, pPos);
             }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    private boolean pBlockStateHasBlockEntity(BlockState pState, BlockState pNewState) {
        return pState.hasBlockEntity() && (!pState.is(pNewState.getBlock()) || !pNewState.hasBlockEntity());
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        MonitorMultiBlockHelper.onNeighborChange(state, level, pos, neighbor);
        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hit);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (pPlayer instanceof FakePlayer) return InteractionResult.PASS;
        
        InteractionHand pHand = InteractionHand.MAIN_HAND;

        if (RadarConfig.client().useGuiByDefault.get()) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof MonitorBlockEntity monitor) {
                if (pLevel.isClientSide) {
                    openMonitorScreenClient(monitor);
                }
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
        }

        if (pPlayer.isShiftKeyDown()) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof MonitorBlockEntity monitor && isGuiHotspot(monitor, pHit)) {
                if (pLevel.isClientSide) {
                    openMonitorScreenClient(monitor);
                }
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
        }

        return onBlockEntityUse(pLevel, pPos, monitorBlockEntity -> MonitorInputHandler.onUse(monitorBlockEntity.getController(), pPlayer, pHand, pHit, pState.getValue(FACING)));
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

        if (hit.getDirection() != screenFace) return false;

        BlockPos controllerPos = controller.getBlockPos();
        if (!hit.getBlockPos().equals(controllerPos)) return false;

        int size = controller.getSize();
        if (size <= 0) return false;

        int stripPx = (size == 1) ? 3 : 6;
        float epsY = stripPx / 16f;

        Vec3 local = hit.getLocation().subtract(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
        float v = 1f - (float) local.y;

        return v >= 1f - epsY;
    }


    private static void openMonitorScreenClient(MonitorBlockEntity anyPiece) {
        BlockPos pos = anyPiece.getBlockPos();
        com.happysg.radar.compat.stub.DistExecutor.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT, () -> () -> Client.openMonitorScreen(pos));
    }

    private static final class Client {
        @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
        static void openMonitorScreen(BlockPos clickedPos) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            BlockEntity be = mc.level.getBlockEntity(clickedPos);
            if (!(be instanceof MonitorBlockEntity anyPiece)) return;

            MonitorBlockEntity controller = anyPiece.isController() ? anyPiece : anyPiece.getController();
            if (controller == null) return;

            BlockPos controllerPos = controller.getControllerPos();
            if (controllerPos == null) controllerPos = controller.getBlockPos();

            mc.setScreen(new MonitorScreen(controllerPos));
        }
    }
}