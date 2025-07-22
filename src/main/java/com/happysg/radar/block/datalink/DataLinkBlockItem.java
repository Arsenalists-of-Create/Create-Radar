package com.happysg.radar.block.datalink;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.network.WeaponNetwork;
import com.happysg.radar.block.network.WeaponNetworkSavedData;
import com.happysg.radar.block.network.WeaponNetworkUnit;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.registry.ModBlocks;

import com.simibubi.create.CreateClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

public class DataLinkBlockItem extends BlockItem {

    public DataLinkBlockItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    @SubscribeEvent
    public static void gathererItemAlwaysPlacesWhenUsed(PlayerInteractEvent.RightClickBlock event) {
        ItemStack usedItem = event.getItemStack();
        if (usedItem.getItem() instanceof DataLinkBlockItem) {
            if (ModBlocks.RADAR_LINK.has(event.getLevel()
                    .getBlockState(event.getPos())))
                return;
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        ItemStack stack = pContext.getItemInHand();
        BlockPos pos = pContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = pContext.getPlayer();

        if (player == null)
            return InteractionResult.FAIL;

        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            player.displayClientMessage(Component.translatable("display_link.clear"), true);
            stack.setTag(null);
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = (pContext.getLevel().getBlockEntity(pContext.getClickedPos()));
        if(blockEntity == null) return InteractionResult.FAIL;
        if (!stack.hasTag()) {
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            //todo monitor hardcoded for now
            if (AllDataBehaviors.targetOf(blockEntity) == null) {
                player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".data_link.link_to_monitor_or_mount"), true);
                return InteractionResult.FAIL;
            }
            CompoundTag stackTag = stack.getOrCreateTag();
            stackTag.put("SelectedPos", NbtUtils.writeBlockPos(pos));
            player.displayClientMessage(Component.translatable("display_link.set"), true);
            stack.setTag(stackTag);
            return InteractionResult.SUCCESS;
        }
        if (blockEntity instanceof WeaponNetworkUnit weaponNetworkUnit && weaponNetworkUnit.getWeaponNetwork() != null) {
            player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".data_link.block_in_network"), true);
            return InteractionResult.FAIL;
        }

        CompoundTag tag = stack.getTag();
        BlockPos selectedPos = NbtUtils.readBlockPos(tag.getCompound("SelectedPos"));
        BlockPos placedPos = pos.relative(pContext.getClickedFace(), state.canBeReplaced() ? 0 : 1);
        if (!level.isClientSide) {
            WeaponNetwork network = WeaponNetworkSavedData.get((ServerLevel) level).networkContains(selectedPos);
            if (network != null && network.isControllerFilled(blockEntity)) {
                player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".data_link.controller_filled"), true);
                return InteractionResult.FAIL;
            }
        }

        CompoundTag teTag = new CompoundTag();


        if (!PhysicsHandler.getWorldPos(level, selectedPos).closerThan(PhysicsHandler.getWorldPos(level, placedPos), RadarConfig.server().radarLinkRange.get())) {
            player.displayClientMessage(Component.translatable("display_link.too_far")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        teTag.put("TargetOffset", NbtUtils.writeBlockPos(selectedPos.subtract(placedPos)));
        tag.put("BlockEntityTag", teTag);

        InteractionResult useOn = super.useOn(pContext);
        if (level.isClientSide || useOn == InteractionResult.FAIL)
            return useOn;

        ItemStack itemInHand = player.getItemInHand(pContext.getHand());
        if (!itemInHand.isEmpty()) itemInHand.setTag(null);
        player.displayClientMessage(Component.translatable("display_link.success")
                .withStyle(ChatFormatting.GREEN), true);
        return useOn;
    }

    private static BlockPos lastShownPos = null;
    private static AABB lastShownAABB = null;

    @OnlyIn(Dist.CLIENT)
    public static void clientTick() {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;
        ItemStack heldItemMainhand = player.getMainHandItem();
        if (!(heldItemMainhand.getItem() instanceof DataLinkBlockItem))
            return;
        if (!heldItemMainhand.hasTag())
            return;
        CompoundTag stackTag = heldItemMainhand.getOrCreateTag();
        if (!stackTag.contains("SelectedPos"))
            return;

        BlockPos selectedPos = NbtUtils.readBlockPos(stackTag.getCompound("SelectedPos"));

        if (!selectedPos.equals(lastShownPos)) {
            lastShownAABB = getBounds(selectedPos);
            lastShownPos = selectedPos;
        }

        CreateClient.OUTLINER.showAABB("target", lastShownAABB)
                .colored(0x6fa8dc)
                .lineWidth(1 / 16f);
    }

    @OnlyIn(Dist.CLIENT)
    private static AABB getBounds(BlockPos pos) {
        Level world = Minecraft.getInstance().level;
        DataController target = AllDataBehaviors.targetOf(world, pos);

        if (target != null)
            return target.getMultiblockBounds(world, pos);

        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getShape(world, pos);
        return shape.isEmpty() ? new AABB(BlockPos.ZERO)
                : shape.bounds()
                .move(pos);
    }

}

