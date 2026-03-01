package com.happysg.radar.block.datalink;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.outliner.Outliner;
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;
import riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity;
import net.arsenalists.createenergycannons.content.energymount.EnergyCannonMount;
import net.arsenalists.createenergycannons.content.energymount.EnergyCannonMountBlockEntity;

import javax.annotation.Nullable;

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
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        BlockPos clickedPos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        BlockState clickedState = level.getBlockState(clickedPos);
        Player player = ctx.getPlayer();

        if (player == null)
            return InteractionResult.FAIL;

        // Shift-click clears any in-progress selection
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("display_link.clear"), true);
                stack.setTag(null);
            }
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = stack.getOrCreateTag();
        var be = level.getBlockEntity(clickedPos);

        // ==========================================
        // MODE SELECT: Mount-first (weapon group)
        // ==========================================
        boolean isEnergyMount = Mods.CREATEENERGYCANNONS.isLoaded() && clickedState.getBlock() instanceof EnergyCannonMount;
        if (clickedState.getBlock() instanceof CannonMountBlock || isEnergyMount) {
            if (!level.isClientSide) {
                tag.put("SelectedMountPos", NbtUtils.writeBlockPos(clickedPos));
                // Ensure other mode is cleared
                tag.remove("SelectedFiltererPos");

                // Clear any controller picks
                tag.remove("SelectedYawPos");
                tag.remove("SelectedPitchPos");
                tag.remove("SelectedFiringPos");

                stack.setTag(tag);
                player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".data_link.mount_set"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // ==========================================
        // MODE SELECT: Filterer-first (filter network)
        // ==========================================
        if (clickedState.getBlock() instanceof NetworkFiltererBlock) {
            if (!level.isClientSide) {
                tag.put("SelectedFiltererPos", NbtUtils.writeBlockPos(clickedPos));
                // Ensure other mode is cleared
                tag.remove("SelectedMountPos");
                // Clear any controller picks
                tag.remove("SelectedYawPos");
                tag.remove("SelectedPitchPos");
                tag.remove("SelectedFiringPos");

                stack.setTag(tag);
                player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".data_link.filterer_set"), true);
            }
            return InteractionResult.SUCCESS;
        }



        ControllerType controllerType = getControllerType(be, clickedState);
        if (controllerType != null && tag.contains("SelectedMountPos")) {


            if (level.isClientSide)
                return InteractionResult.SUCCESS;

            if (!(level instanceof ServerLevel serverLevel))
                return InteractionResult.FAIL;

            BlockPos mountPos = NbtUtils.readBlockPos(tag.getCompound("SelectedMountPos"));

            WeaponNetworkData weaponData = WeaponNetworkData.get(serverLevel);
            BlockPos existingMount = weaponData.getMountForController(serverLevel.dimension(), clickedPos);
            if (existingMount != null) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.controller_already_linked")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null); // user must restart each time
                return InteractionResult.FAIL;
            }

            // Placement position: adjacent to controller on clicked face
            BlockPos placedPos = clickedPos.relative(ctx.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);

            // Range check: mount + controller must reach the datalink placement
            double range = RadarConfig.server().radarLinkRange.get();
            if (!withinRange(level, placedPos, mountPos, range) || !withinRange(level, placedPos, clickedPos, range)) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID+ ".data_link.too_far").withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null); // user must restart each time
                return InteractionResult.FAIL;
            }

            // Validate group merge before placement (no mutation)
            WeaponNetworkData.Group group = weaponData.getOrCreateGroup(serverLevel.dimension(), mountPos);

            BlockPos yawPos = null, pitchPos = null, firePos = null;
            switch (controllerType) {
                case YAW -> yawPos = clickedPos;
                case PITCH -> pitchPos = clickedPos;
                case FIRING -> firePos = clickedPos;
            }

            if (!weaponData.canMergeIntoGroup(group, yawPos, pitchPos, firePos)) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.duplicate_controller_type")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null); // user must restart each time
                return InteractionResult.FAIL;
            }

            // Place the DataLink (this is the ONLY place call in weapon mode)
            InteractionResult placed = super.useOn(ctx);
            if (placed == InteractionResult.FAIL) {
                stack.setTag(null);
                return placed;
            }

            // Verify placement landed where expected
            if (!(level.getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.place_failed")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null);
                return InteractionResult.FAIL;
            }

            // Set texture/style for this link method
            BlockState dlState = level.getBlockState(placedPos);
            if (dlState.hasProperty(DataLinkBlock.LINK_STYLE)) {
                level.setBlock(placedPos,
                        dlState.setValue(DataLinkBlock.LINK_STYLE, DataLinkBlock.LinkStyle.CONTROLLER),
                        3);
            }

            // Commit now that placement succeeded
            boolean merged = weaponData.tryMergeIntoGroup(group, yawPos, pitchPos, firePos);
            if (!merged) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.commit_failed")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null);
                return InteractionResult.SUCCESS;
            }

            weaponData.addDataLinkToGroup(group, placedPos);

            player.displayClientMessage(
                    Component.translatable("display_link.success").withStyle(ChatFormatting.GREEN),
                    true
            );

            stack.setTag(null); // do NOT keep mount selected; user must restart each time
            return InteractionResult.SUCCESS;
        }



        // Requires: filterer selected
        // Allowed targets: Monitor (0..1), RadarBearing OR Stationary (0..1), Controllers (unbounded, but no duplicate weapon group)

        if (tag.contains("SelectedFiltererPos")) {
            // Determine allowed target type
            FilterTarget target = getFilterTarget(be, clickedState);

            if (target == null) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable(CreateRadar.MODID + ".data_link.invalid_filter_target")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    stack.setTag(null); // user must restart each time
                }
                return InteractionResult.FAIL;
            }

            if (level.isClientSide)
                return InteractionResult.SUCCESS;

            if (!(level instanceof ServerLevel serverLevel))
                return InteractionResult.FAIL;

            BlockPos filtererPos = NbtUtils.readBlockPos(tag.getCompound("SelectedFiltererPos"));

            // adjacent to target on clicked face
            BlockPos placedPos = clickedPos.relative(ctx.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);

            //  filterer + target must reach datalink
            double range = RadarConfig.server().radarLinkRange.get();
            if (!withinRange(level, placedPos, filtererPos, range) || !withinRange(level, placedPos, clickedPos, range)) {
                player.displayClientMessage(
                        Component.translatable("display_link.too_far").withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null);
                return InteractionResult.FAIL;
            }

            NetworkData filterData = NetworkData.get(serverLevel);
            NetworkData.Group fGroup = filterData.getOrCreateGroup(serverLevel.dimension(), filtererPos);

            // Validate before placement
            boolean canAttach;
            BlockPos weaponMountPos = null;

            switch (target.kind) {
                case MONITOR -> canAttach = filterData.canAttachMonitor(fGroup, clickedPos);

                case RADAR_BEARING -> canAttach = filterData.canAttachRadar(fGroup, clickedPos, NetworkData.RadarKind.BEARING);

                case RADAR_STATIONARY -> canAttach = filterData.canAttachRadar(fGroup, clickedPos, NetworkData.RadarKind.STATIONARY);

                case CONTROLLER -> {
                    if (!(be instanceof AutoPitchControllerBlockEntity)) {
                        player.displayClientMessage(
                                Component.translatable(CreateRadar.MODID + ".data_link.only_pitch_allowed")
                                        .withStyle(ChatFormatting.RED),
                                true
                        );
                        stack.setTag(null);
                        return InteractionResult.FAIL;
                    }

                    // // Controller MUST already belong to a weapon group for filter networks
                    WeaponNetworkData weaponData = WeaponNetworkData.get(serverLevel);
                    weaponMountPos = weaponData.getMountForController(serverLevel.dimension(), clickedPos);
                    if (weaponMountPos == null) {
                        player.displayClientMessage(
                                Component.translatable(CreateRadar.MODID + ".data_link.controller_no_weapon_group")
                                        .withStyle(ChatFormatting.RED),
                                true
                        );
                        stack.setTag(null);
                        return InteractionResult.FAIL;
                    }

                    canAttach = filterData.canAttachWeaponEndpoint(fGroup, clickedPos, weaponMountPos);
                }


                default -> canAttach = false;
            }

            if (!canAttach) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.filter_attach_denied")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null);
                return InteractionResult.FAIL;
            }

            // Place the DataLink (ONLY placement path in filter mode)
            InteractionResult placed = super.useOn(ctx);
            if (placed == InteractionResult.FAIL) {
                stack.setTag(null);
                return placed;
            }

            if (!(level.getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".data_link.place_failed")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                stack.setTag(null);
                return InteractionResult.FAIL;
            }

            // Set texture/style for this link method
            BlockState dlState = level.getBlockState(placedPos);
            if (dlState.hasProperty(DataLinkBlock.LINK_STYLE)) {
                level.setBlock(placedPos,
                        dlState.setValue(DataLinkBlock.LINK_STYLE, DataLinkBlock.LinkStyle.RADAR),
                        3);
            }

            // Commit after placement
            switch (target.kind) {
                case MONITOR -> {
                    BlockPos pos = clickedPos;
                    BlockEntity mbe = serverLevel.getBlockEntity(clickedPos);
                    if (mbe instanceof MonitorBlockEntity m) {
                        pos = m.getControllerPos();
                    }
                    filterData.attachMonitor(serverLevel,fGroup, pos);
                }

                case RADAR_BEARING -> {
                    filterData.attachRadar(fGroup, clickedPos, NetworkData.RadarKind.BEARING);

                    BlockEntity fbe = serverLevel.getBlockEntity(filtererPos);
                    if (fbe instanceof com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity nfb) {
                        nfb.applyFiltersToNetwork();
                    }
                }

                case RADAR_STATIONARY -> {
                    filterData.attachRadar(fGroup, clickedPos, NetworkData.RadarKind.STATIONARY);

                    BlockEntity fbe = serverLevel.getBlockEntity(filtererPos);
                    if (fbe instanceof com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity nfb) {
                        nfb.applyFiltersToNetwork();
                    }
                }

                case CONTROLLER -> filterData.attachWeaponEndpoint(fGroup, clickedPos, weaponMountPos);
            }

            filterData.addDataLinkToGroup(fGroup, placedPos,clickedPos); // Might be issue later

            player.displayClientMessage(
                    Component.translatable("display_link.success").withStyle(ChatFormatting.GREEN),
                    true
            );

            stack.setTag(null);
            return InteractionResult.SUCCESS;
        }


        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".data_link.select_mount_or_filterer_first")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
        return InteractionResult.FAIL;
    }

// -------------------------D
// Helper types / methods
// -------------------------

    private static boolean withinRange(Level level, BlockPos a, BlockPos b, double range) {
        Vec3 wa = PhysicsHandler.getWorldPos(level, a).getCenter();
        Vec3 wb = PhysicsHandler.getWorldPos(level, b).getCenter();
        return wa.closerThan(wb, range);
    }


    private static boolean isCannonMountBE(@Nullable BlockEntity be) {
        if(be instanceof CannonMountBlockEntity)return true;
        if(be instanceof FixedCannonMountBlockEntity) return true;
        if(Mods.CREATEENERGYCANNONS.isLoaded()){
            if(be instanceof EnergyCannonMountBlockEntity) return true;
        }
        if(Mods.CBCMODERNWARFARE.isLoaded()){
            if(be instanceof CompactCannonMountBlockEntity) return true;
        }
        return false;
    }
    private enum MountType{NORMAL, FIXED, COMPACT, ENERGY}
    private static MountType getMountType(BlockEntity be, BlockState state){
        if(be instanceof FixedCannonMountBlockEntity) return MountType.FIXED;
        if(Mods.CREATEENERGYCANNONS.isLoaded()){
            if(be instanceof EnergyCannonMountBlockEntity) return MountType.ENERGY;
        }
        if(be instanceof CannonMountBlockEntity) return MountType.NORMAL;
        if(Mods.CBCMODERNWARFARE.isLoaded()){
            if(be instanceof CompactCannonMountBlockEntity) return MountType.COMPACT;
        }
        return null;
    }
    private enum ControllerType { YAW, PITCH, FIRING }

    private static @Nullable ControllerType getControllerType(@Nullable BlockEntity be, BlockState state) {
        if (be instanceof AutoYawControllerBlockEntity) return ControllerType.YAW;
        if (be instanceof AutoPitchControllerBlockEntity) return ControllerType.PITCH;
        if (state.getBlock() instanceof FireControllerBlock) return ControllerType.FIRING;
        if (be instanceof FireControllerBlockEntity) return ControllerType.FIRING;
        return null;
    }

    private static String controllerKey(ControllerType type) {
        return switch (type) {
            case YAW -> "SelectedYawPos";
            case PITCH -> "SelectedPitchPos";
            case FIRING -> "SelectedFiringPos";
        };
    }

    private static class FilterTarget {
        final FilterTargetKind kind;
        FilterTarget(FilterTargetKind kind) { this.kind = kind; }
    }

    private enum FilterTargetKind {
        MONITOR,
        RADAR_BEARING,
        RADAR_STATIONARY,
        CONTROLLER
    }


    private static @Nullable FilterTarget getFilterTarget(@Nullable BlockEntity be, BlockState state) {
        if (be instanceof MonitorBlockEntity) return new FilterTarget(FilterTargetKind.MONITOR);

        if (state.getBlock() instanceof RadarBearingBlock) return new FilterTarget(FilterTargetKind.RADAR_BEARING);
        if (state.getBlock() instanceof StationaryRadarBlock) return new FilterTarget(FilterTargetKind.RADAR_STATIONARY);


        if (getControllerType(be, state) != null) return new FilterTarget(FilterTargetKind.CONTROLLER);

        return null;
    }

    private static void clearControllersKeepMount(ItemStack stack) {
        if (!stack.hasTag()) return;
        CompoundTag tag = stack.getTag();
        tag.remove("SelectedYawPos");
        tag.remove("SelectedPitchPos");
        tag.remove("SelectedFiringPos");
        tag.remove("BlockEntityTag");
        if (tag.isEmpty()) stack.setTag(null);
    }

    private static void clearItemTag(Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        if (!inHand.isEmpty()) inHand.setTag(null);
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

