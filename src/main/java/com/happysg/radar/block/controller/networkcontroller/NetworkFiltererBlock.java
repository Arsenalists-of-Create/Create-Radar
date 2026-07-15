package com.happysg.radar.block.controller.networkcontroller;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.item.RadarCompassLink;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.happysg.radar.registry.ModBlocks;
import com.happysg.radar.registry.ModItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import org.jetbrains.annotations.Nullable;

public class NetworkFiltererBlock extends WrenchableDirectionalBlock implements IBE<NetworkFiltererBlockEntity> {

    public NetworkFiltererBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get()) {
            return (lvl, pos, st, be) -> NetworkFiltererBlockEntity.tick(lvl, pos, st, (NetworkFiltererBlockEntity) be);
        }
        return null;
    }

    public Class<NetworkFiltererBlockEntity> getBlockEntityClass() {
        return NetworkFiltererBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends NetworkFiltererBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Vec3 hitVec = hit.getLocation();
        double dx = hitVec.x - pos.getX();
        double dy = hitVec.y - pos.getY();
        double dz = hitVec.z - pos.getZ();

        Direction face = hit.getDirection();

        double clickU = 0.0;
        double clickV = 0.0;

        switch (face) {
            case NORTH -> {
                clickU = dx * 16.0;
                clickV = (1.0 - dy) * 16.0;
            }
            case SOUTH -> {
                clickU = (1.0 - dx) * 16.0;
                clickV = (1.0 - dy) * 16.0;
            }
            case WEST -> {
                clickU = (1.0 - dz) * 16.0;
                clickV = (1.0 - dy) * 16.0;
            }
            case EAST -> {
                clickU = dz * 16.0;
                clickV = (1.0 - dy) * 16.0;
            }
            case UP -> {
                clickU = dx * 16.0;
                clickV = (1.0 - dz) * 16.0;
            }
            case DOWN -> {
                clickU = dx * 16.0;
                clickV = dz * 16.0;
            }
        }

        final double[][] UVS = {{5.0, 11.0}, {11.0, 11.0}, {11.0, 5.0}};
        final double PIXEL_THRESHOLD = 2.5;

        int clickedSlot = -1;
        for (int i = 0; i < UVS.length; i++) {
            double du = clickU - UVS[i][0];
            double dv = clickV - UVS[i][1];
            double distSq = du * du + dv * dv;

            if (distSq <= PIXEL_THRESHOLD * PIXEL_THRESHOLD) {
                clickedSlot = i;
                break;
            }
        }

        if (clickedSlot == -1) {
            return InteractionResult.PASS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof NetworkFiltererBlockEntity netFC)) {
            return InteractionResult.PASS;
        }

        IItemHandler inv = netFC.getItemHandler();

        ItemStack extracted = inv.extractItem(clickedSlot, 1, false);
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        boolean added = player.addItem(extracted);
        if (!added) {
            player.drop(extracted, false);
        }

        world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
        player.displayClientMessage(
                Component.translatable(CreateRadar.MODID + ".network_filter.filter_removed").withStyle(ChatFormatting.GOLD),
                true
        );

        return InteractionResult.CONSUME;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level world, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (held.is(ModItems.BINOCULARS.asItem())) {
            return ItemInteractionResult.SUCCESS;
        }

        if (held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (world.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof NetworkFiltererBlockEntity netFC)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (held.is(Items.COMPASS)) {
            boolean bindHeldCompass = !player.hasInfiniteMaterials() && held.getCount() == 1;
            if (bindHeldCompass) {
                RadarCompassLink.bind(held, (ServerLevel) world, pos);
            } else {
                ItemStack boundCompass = held.transmuteCopy(Items.COMPASS, 1);
                held.consume(1, player);
                RadarCompassLink.bind(boundCompass, (ServerLevel) world, pos);
                if (!player.getInventory().add(boundCompass)) {
                    player.drop(boundCompass, false);
                }
            }

            world.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".network_filter.compass_bound")
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            return ItemInteractionResult.CONSUME;
        }

        Item radarItem = ModItems.RADAR_FILTER_ITEM.get();
        Item identItem = ModItems.IDENT_FILTER_ITEM.get();
        Item targetItem = ModItems.TARGET_FILTER_ITEM.get();

        int targetSlot;
        Item heldItem = held.getItem();

        if (heldItem == radarItem) {
            targetSlot = 0;
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".network_filter.success").withStyle(ChatFormatting.GREEN),
                    true
            );
        } else if (heldItem == identItem) {
            targetSlot = 1;
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".network_filter.success").withStyle(ChatFormatting.GREEN),
                    true
            );
        } else if (heldItem == targetItem) {
            targetSlot = 2;
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".network_filter.success").withStyle(ChatFormatting.GREEN),
                    true
            );
        } else {
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".network_filter.invalid").withStyle(ChatFormatting.RED),
                    true
            );
            return ItemInteractionResult.FAIL;
        }

        IItemHandler inv = netFC.getItemHandler();

        ItemStack toInsert = held.copy();
        toInsert.setCount(1);

        ItemStack remainder = inv.insertItem(targetSlot, toInsert, false);

        if (remainder.isEmpty()) {
            held.shrink(1);

            if (held.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }

            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.FAIL;
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Only run when the block is actually being removed/replaced with a different block
        if (state.getBlock() != newState.getBlock()) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {

                    // If this is the network controller, dissolve its network before the BE disappears
                    if (level instanceof ServerLevel serverLevel && be instanceof NetworkFiltererBlockEntity controller) {
                        controller.dissolveNetwork(serverLevel);
                    }

                    // Drop any inventory contents (if present)
                    if (be instanceof NetworkFiltererBlockEntity filterer) {
                        IItemHandler handler = filterer.getItemHandler();

                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack stack = handler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                            }
                        }
                    }
                }
            }
        }

        // call super last so vanilla can do its cleanup
        super.onRemove(state, level, pos, newState, isMoving);
    }


    //DEBUG ZONE
    /*
    private void sendSlotContentsWithNbt(Player player, IItemHandler inv) {
        for (int i = 0; i < 3; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null || s.isEmpty()) {
       //         player.sendSystemMessage(Component.literal("Slot " + (i + 1) + ": (empty)"));
                continue;
            }

            String name = s.getHoverName().getString();
            int count = s.getCount();

            // Do not create a tag if it does not exist - use hasTag() / getTag()
            String nbtString;
            if (s.hasTag()) {
                CompoundTag tag = s.getTag(); // may be non-null if hasTag() true
                // raw NBT as a string; could be long
                nbtString = tag == null ? "(null)" : tag.toString();
            } else {
                nbtString = "(none)";
            }

            // Compose and send.  can switch to displayClientMessage(..., true) if prefer actionbar
            player.sendSystemMessage(Component.literal(
                    "Slot " + (i + 1) + ": " + count + "x " + name + " | NBT: " + nbtString
            ));
        }
    }
    */

}
