package com.happysg.radar.block.datalink;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlock;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlock;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.registry.ModBlocks;
import net.arsenalists.createenergycannons.content.energymount.EnergyCannonMount;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;

import javax.annotation.Nullable;

public class DataLinkBlockItem extends BlockItem {
    private static final String SELECTED_MOUNT_POS = "SelectedMountPos";
    private static final String SELECTED_FILTERER_POS = "SelectedFiltererPos";
    private static final String SELECTED_YAW_POS = "SelectedYawPos";
    private static final String SELECTED_PITCH_POS = "SelectedPitchPos";
    private static final String SELECTED_FIRING_POS = "SelectedFiringPos";
    private static final String SELECTED_RWR_POS = "SelectedRwrPos";
    private static final String SELECTED_POS = "SelectedPos";

    private static final String DISPLAY_CLEAR = "display_link.clear";
    private static final String DISPLAY_SUCCESS = "display_link.success";
    private static final String DATA_LINK_COMMIT_FAILED = CreateRadar.MODID + ".data_link.commit_failed";
    private static final String DATA_LINK_CONTROLLER_ALREADY_LINKED = CreateRadar.MODID + ".data_link.controller_already_linked";
    private static final String DATA_LINK_CONTROLLER_NO_WEAPON_GROUP = CreateRadar.MODID + ".data_link.controller_no_weapon_group";
    private static final String DATA_LINK_DUPLICATE_CONTROLLER_TYPE = CreateRadar.MODID + ".data_link.duplicate_controller_type";
    private static final String DATA_LINK_FILTER_ATTACH_DENIED = CreateRadar.MODID + ".data_link.filter_attach_denied";
    private static final String DATA_LINK_FILTERER_SET = CreateRadar.MODID + ".data_link.filterer_set";
    private static final String DATA_LINK_INVALID_FILTER_TARGET = CreateRadar.MODID + ".data_link.invalid_filter_target";
    private static final String DATA_LINK_MOUNT_SET = CreateRadar.MODID + ".data_link.mount_set";
    private static final String DATA_LINK_RWR_SET = CreateRadar.MODID + ".data_link.rwr_set";
    private static final String DATA_LINK_ARAD_MONITOR_CONFLICT = CreateRadar.MODID + ".data_link.arad_monitor_conflict";
    private static final String DATA_LINK_ONLY_PITCH_ALLOWED = CreateRadar.MODID + ".data_link.only_pitch_allowed";
    private static final String DATA_LINK_PLACE_FAILED = CreateRadar.MODID + ".data_link.place_failed";
    private static final String DATA_LINK_SELECT_FIRST = CreateRadar.MODID + ".data_link.select_mount_or_filterer_first";

    public DataLinkBlockItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    @SubscribeEvent
    public static void gathererItemAlwaysPlacesWhenUsed(PlayerInteractEvent.RightClickBlock event) {
        ItemStack usedItem = event.getItemStack();

        if (!(usedItem.getItem() instanceof DataLinkBlockItem)) {
            return;
        }

        if (ModBlocks.RADAR_LINK.has(event.getLevel().getBlockState(event.getPos()))) {
            return;
        }

        event.setUseBlock(TriState.FALSE);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null)
            return InteractionResult.FAIL;

        ItemStack stack = ctx.getItemInHand();
        Level level = ctx.getLevel();

        if (player.isShiftKeyDown() && hasLinkTag(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(DISPLAY_CLEAR), true);
                clearLinkTag(stack);
            }
            return InteractionResult.SUCCESS;
        }

        LinkUse use = LinkUse.create(ctx, stack, player, level, getLinkTag(stack));

        InteractionResult selection = trySelectSource(use);
        if (selection != null)
            return selection;

        ControllerType controllerType = ControllerType.from(use.be(), use.clickedState());
        if (controllerType != null && use.tag().contains(SELECTED_MOUNT_POS))
            return completeWeaponLink(use, controllerType);

        if (use.tag().contains(SELECTED_RWR_POS))
            return completeAradLink(use);

        if (use.tag().contains(SELECTED_FILTERER_POS))
            return completeFilterLink(use);

        if (!level.isClientSide) {
            sendError(player, DATA_LINK_SELECT_FIRST);
        }
        return InteractionResult.FAIL;
    }

    private InteractionResult trySelectSource(LinkUse use) {
        if (isMount(use.be(), use.clickedState())) {
            if (!use.level().isClientSide) {
                use.tag().put(SELECTED_MOUNT_POS, NbtUtils.writeBlockPos(use.clickedPos()));
                use.tag().remove(SELECTED_FILTERER_POS);
                use.tag().remove(SELECTED_RWR_POS);
                clearControllerSelections(use.tag());
                setLinkTag(use.stack(), use.tag());
                use.player().displayClientMessage(Component.translatable(DATA_LINK_MOUNT_SET), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (use.clickedState().getBlock() instanceof NetworkFiltererBlock) {
            if (!use.level().isClientSide) {
                use.tag().put(SELECTED_FILTERER_POS, NbtUtils.writeBlockPos(use.clickedPos()));
                use.tag().remove(SELECTED_MOUNT_POS);
                use.tag().remove(SELECTED_RWR_POS);
                clearControllerSelections(use.tag());
                setLinkTag(use.stack(), use.tag());
                use.player().displayClientMessage(Component.translatable(DATA_LINK_FILTERER_SET), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (use.be() instanceof RadarWarningReceiverBlockEntity) {
            if (!use.level().isClientSide) {
                use.tag().put(SELECTED_RWR_POS, NbtUtils.writeBlockPos(use.clickedPos()));
                use.tag().remove(SELECTED_MOUNT_POS);
                use.tag().remove(SELECTED_FILTERER_POS);
                clearControllerSelections(use.tag());
                setLinkTag(use.stack(), use.tag());
                use.player().displayClientMessage(Component.translatable(DATA_LINK_RWR_SET), true);
            }
            return InteractionResult.SUCCESS;
        }

        return null;
    }

    private static boolean isMount(@Nullable BlockEntity blockEntity, BlockState state) {
        boolean isEnergyMount = Mods.CREATEENERGYCANNONS.isLoaded() && state.getBlock() instanceof EnergyCannonMount;
        return CannonMountContext.of(blockEntity) != null
                || CannonMountContext.isCompactMount(blockEntity, state)
                || state.getBlock() instanceof CannonMountBlock
                || isEnergyMount;
    }

    private InteractionResult completeWeaponLink(LinkUse use, ControllerType controllerType) {
        if (use.level().isClientSide)
            return InteractionResult.SUCCESS;

        if (!(use.level() instanceof ServerLevel serverLevel))
            return InteractionResult.FAIL;

        BlockPos mountPos = readSelectedPos(use.tag(), SELECTED_MOUNT_POS);
        if (mountPos == null) {
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        WeaponNetworkRuntime weaponRuntime = WeaponNetworkRuntime.get(serverLevel);
        BlockPos existingMount = weaponRuntime.getMountForController(use.clickedPos());
        if (existingMount != null) {
            sendError(use.player(), DATA_LINK_CONTROLLER_ALREADY_LINKED);
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        BlockPos placedPos = getPlacementPos(use);
        DataLinkBlockEntity.WeaponEndpointType endpointType = controllerType.endpointType();
        if (!weaponRuntime.canAttachEndpoint(endpointType, use.clickedPos(), mountPos)) {
            sendError(use.player(), DATA_LINK_DUPLICATE_CONTROLLER_TYPE);
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        InteractionResult placed = placeAndVerify(use, placedPos);
        if (placed != null) {
            clearLinkTag(use.stack());
            return placed;
        }

        setLinkStyle(use.level(), placedPos, DataLinkBlock.LinkStyle.CONTROLLER);

        if (serverLevel.getBlockEntity(placedPos) instanceof DataLinkBlockEntity dataLink) {
            dataLink.target(mountPos);
            dataLink.setWeaponEndpointType(endpointType);
            weaponRuntime.register(dataLink);
        } else {
            sendError(use.player(), DATA_LINK_COMMIT_FAILED);
            clearLinkTag(use.stack());
            return InteractionResult.SUCCESS;
        }
        sendSuccess(use.player());
        clearLinkTag(use.stack());
        return InteractionResult.SUCCESS;
    }

    private InteractionResult completeAradLink(LinkUse use) {
        if (!(use.be() instanceof MonitorBlockEntity monitor)) {
            if (!use.level().isClientSide) {
                sendError(use.player(), DATA_LINK_INVALID_FILTER_TARGET);
                clearLinkTag(use.stack());
            }
            return InteractionResult.FAIL;
        }

        if (use.level().isClientSide)
            return InteractionResult.SUCCESS;

        if (!(use.level() instanceof ServerLevel serverLevel))
            return InteractionResult.FAIL;

        BlockPos rwrPos = readSelectedPos(use.tag(), SELECTED_RWR_POS);
        if (rwrPos == null) {
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        BlockPos monitorPos = monitor.getControllerPos();
        if (monitorPos == null) monitorPos = use.clickedPos();

        BlockPos placedPos = getPlacementPos(use);
        ARADData aradData = ARADData.get(serverLevel);
        ARADData.Group group = aradData.getOrCreateGroup(serverLevel.dimension(), rwrPos);
        if (!aradData.canAttachMonitor(serverLevel, group, monitorPos)) {
            sendError(use.player(), DATA_LINK_ARAD_MONITOR_CONFLICT);
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        InteractionResult placed = placeAndVerify(use, placedPos);
        if (placed != null) {
            clearLinkTag(use.stack());
            return placed;
        }

        setLinkStyle(use.level(), placedPos, DataLinkBlock.LinkStyle.RADAR);
        aradData.attachMonitor(serverLevel, group, monitorPos, ARADData.LinkOrigin.DATALINK);
        aradData.addDataLinkToGroup(group, placedPos, monitorPos);
        if (serverLevel.getBlockEntity(placedPos) instanceof DataLinkBlockEntity dataLink) {
            dataLink.target(monitorPos);
        }

        sendSuccess(use.player());
        clearLinkTag(use.stack());
        return InteractionResult.SUCCESS;
    }

    private InteractionResult completeFilterLink(LinkUse use) {
        FilterTarget target = FilterTarget.from(use.be(), use.clickedState());
        if (target == null) {
            if (!use.level().isClientSide) {
                sendError(use.player(), DATA_LINK_INVALID_FILTER_TARGET);
                clearLinkTag(use.stack());
            }
            return InteractionResult.FAIL;
        }

        if (use.level().isClientSide)
            return InteractionResult.SUCCESS;

        if (!(use.level() instanceof ServerLevel serverLevel))
            return InteractionResult.FAIL;

        BlockPos filtererPos = readSelectedPos(use.tag(), SELECTED_FILTERER_POS);
        if (filtererPos == null) {
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        BlockPos placedPos = getPlacementPos(use);
        NetworkData filterData = NetworkData.get(serverLevel);
        NetworkData.Group group = filterData.getOrCreateGroup(serverLevel.dimension(), filtererPos);

        FilterCommit commit = target.validate(use, serverLevel, filterData, group);
        if (commit.denial() != null) {
            sendError(use.player(), commit.denial());
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        if (!commit.canAttach()) {
            sendError(use.player(), DATA_LINK_FILTER_ATTACH_DENIED);
            clearLinkTag(use.stack());
            return InteractionResult.FAIL;
        }

        InteractionResult placed = placeAndVerify(use, placedPos);
        if (placed != null) {
            clearLinkTag(use.stack());
            return placed;
        }

        setLinkStyle(use.level(), placedPos, DataLinkBlock.LinkStyle.RADAR);

        target.commit(use, serverLevel, filterData, group, filtererPos, commit);
        filterData.addDataLinkToGroup(group, placedPos, use.clickedPos());
        if (serverLevel.getBlockEntity(placedPos) instanceof DataLinkBlockEntity dataLink) {
            dataLink.target(use.clickedPos());
            dataLink.setFiltererPosition(filtererPos);
        }

        sendSuccess(use.player());
        clearLinkTag(use.stack());
        return InteractionResult.SUCCESS;
    }

    private InteractionResult placeAndVerify(LinkUse use, BlockPos placedPos) {
        InteractionResult placed = super.useOn(use.ctx());
        if (placed == InteractionResult.FAIL)
            return placed;

        if (!(use.level().getBlockState(placedPos).getBlock() instanceof DataLinkBlock)) {
            sendError(use.player(), DATA_LINK_PLACE_FAILED);
            return InteractionResult.FAIL;
        }

        return null;
    }

    private static BlockPos getPlacementPos(LinkUse use) {
        return use.clickedPos().relative(use.ctx().getClickedFace(), use.clickedState().canBeReplaced() ? 0 : 1);
    }

    private static void setLinkStyle(Level level, BlockPos placedPos, DataLinkBlock.LinkStyle style) {
        BlockState dlState = level.getBlockState(placedPos);
        if (dlState.hasProperty(DataLinkBlock.LINK_STYLE)) {
            level.setBlock(placedPos, dlState.setValue(DataLinkBlock.LINK_STYLE, style), 3);
        }
    }

    private static void clearControllerSelections(CompoundTag tag) {
        tag.remove(SELECTED_YAW_POS);
        tag.remove(SELECTED_PITCH_POS);
        tag.remove(SELECTED_FIRING_POS);
    }

    @Nullable
    private static BlockPos readSelectedPos(CompoundTag tag, String key) {
        return NbtUtils.readBlockPos(tag, key).orElse(null);
    }

    private static void sendError(Player player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
    }

    private static void sendSuccess(Player player) {
        player.displayClientMessage(Component.translatable(DISPLAY_SUCCESS).withStyle(ChatFormatting.GREEN), true);
    }

    private static CompoundTag getLinkTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    private static boolean hasLinkTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    private static void setLinkTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
    }

    private static void clearLinkTag(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    private record LinkUse(
            UseOnContext ctx,
            ItemStack stack,
            Player player,
            Level level,
            BlockPos clickedPos,
            BlockState clickedState,
            @Nullable BlockEntity be,
            CompoundTag tag
    ) {
        static LinkUse create(UseOnContext ctx, ItemStack stack, Player player, Level level, CompoundTag tag) {
            BlockPos clickedPos = ctx.getClickedPos();
            return new LinkUse(
                    ctx,
                    stack,
                    player,
                    level,
                    clickedPos,
                    level.getBlockState(clickedPos),
                    level.getBlockEntity(clickedPos),
                    tag
            );
        }
    }

    private enum ControllerType {
        YAW,
        PITCH,
        FIRING;

        static @Nullable ControllerType from(@Nullable BlockEntity be, BlockState state) {
            if (be instanceof AutoYawControllerBlockEntity) return YAW;
            if (be instanceof AutoPitchControllerBlockEntity) return PITCH;
            if (state.getBlock() instanceof FireControllerBlock) return FIRING;
            if (be instanceof FireControllerBlockEntity) return FIRING;
            return null;
        }

        DataLinkBlockEntity.WeaponEndpointType endpointType() {
            return switch (this) {
                case YAW -> DataLinkBlockEntity.WeaponEndpointType.YAW;
                case PITCH -> DataLinkBlockEntity.WeaponEndpointType.PITCH;
                case FIRING -> DataLinkBlockEntity.WeaponEndpointType.FIRING;
            };
        }
    }

    private record FilterCommit(boolean canAttach, @Nullable BlockPos weaponMountPos, @Nullable String denial) {
        static FilterCommit allowed(boolean canAttach) {
            return new FilterCommit(canAttach, null, null);
        }

        static FilterCommit weapon(boolean canAttach, BlockPos weaponMountPos) {
            return new FilterCommit(canAttach, weaponMountPos, null);
        }

        static FilterCommit denied(String translationKey) {
            return new FilterCommit(false, null, translationKey);
        }
    }

    private enum FilterTarget {
        MONITOR,
        RADAR_BEARING,
        RADAR_STATIONARY,
        RADAR_SKY,
        RADAR_SONAR,
        CONTROLLER;

        static @Nullable FilterTarget from(@Nullable BlockEntity be, BlockState state) {
            if (be instanceof MonitorBlockEntity) return MONITOR;
            if (state.getBlock() instanceof RadarBearingBlock) return RADAR_BEARING;
            if (state.getBlock() instanceof StationaryRadarBlock) return RADAR_STATIONARY;
            if (state.getBlock() instanceof SkyRadarBlock) return RADAR_SKY;
            if (isSonarBlock(state)) return RADAR_SONAR;
            if (ControllerType.from(be, state) != null) return CONTROLLER;
            return null;
        }

        private static boolean isSonarBlock(BlockState state) {
            Class<?> blockClass = state.getBlock().getClass();
            Package blockPackage = blockClass.getPackage();
            return blockPackage != null
                    && blockPackage.getName().contains(".radar.sonar")
                    && blockClass.getSimpleName().contains("Sonar");
        }

        FilterCommit validate(LinkUse use, ServerLevel serverLevel, NetworkData data, NetworkData.Group group) {
            return switch (this) {
                case MONITOR -> validateMonitor(use, serverLevel, data, group);
                case RADAR_BEARING -> FilterCommit.allowed(data.canAttachRadar(group, use.clickedPos(), NetworkData.RadarKind.BEARING));
                case RADAR_STATIONARY -> FilterCommit.allowed(data.canAttachRadar(group, use.clickedPos(), NetworkData.RadarKind.STATIONARY));
                case RADAR_SKY -> FilterCommit.allowed(data.canAttachRadar(group, use.clickedPos(), NetworkData.RadarKind.SKY));
                case RADAR_SONAR -> FilterCommit.allowed(data.canAttachRadar(group, use.clickedPos(), NetworkData.RadarKind.SONAR));
                case CONTROLLER -> validateController(use, serverLevel, data, group);
            };
        }

        private static FilterCommit validateMonitor(LinkUse use, ServerLevel serverLevel, NetworkData data, NetworkData.Group group) {
            BlockPos monitorPos = normalizedMonitorPos(use);
            if (ARADData.get(serverLevel).isMonitorDatalinked(serverLevel.dimension(), monitorPos)) {
                return FilterCommit.denied(DATA_LINK_ARAD_MONITOR_CONFLICT);
            }
            return FilterCommit.allowed(data.canAttachMonitor(group, monitorPos));
        }

        private static FilterCommit validateController(LinkUse use, ServerLevel serverLevel, NetworkData data, NetworkData.Group group) {
            if (!(use.be() instanceof AutoPitchControllerBlockEntity)) {
                return FilterCommit.denied(DATA_LINK_ONLY_PITCH_ALLOWED);
            }

            BlockPos weaponMountPos = WeaponNetworkRuntime.get(serverLevel).getMountForController(use.clickedPos());
            if (weaponMountPos == null) {
                return FilterCommit.denied(DATA_LINK_CONTROLLER_NO_WEAPON_GROUP);
            }

            return FilterCommit.weapon(data.canAttachWeaponEndpoint(group, use.clickedPos(), weaponMountPos), weaponMountPos);
        }

        private static BlockPos normalizedMonitorPos(LinkUse use) {
            if (use.be() instanceof MonitorBlockEntity monitor) {
                BlockPos controllerPos = monitor.getControllerPos();
                if (controllerPos != null) return controllerPos;
            }
            return use.clickedPos();
        }

        void commit(LinkUse use, ServerLevel serverLevel, NetworkData data, NetworkData.Group group, BlockPos filtererPos, FilterCommit commit) {
            switch (this) {
                case MONITOR -> {
                    BlockPos pos = use.clickedPos();
                    BlockEntity mbe = serverLevel.getBlockEntity(use.clickedPos());
                    if (mbe instanceof MonitorBlockEntity monitor) {
                        pos = monitor.getControllerPos();
                    }
                    if (ARADData.get(serverLevel).getEndpointOrigin(serverLevel.dimension(), pos) == ARADData.LinkOrigin.CONTACT) {
                        ARADData.get(serverLevel).removeContactEndpoint(serverLevel, pos);
                    }
                    data.attachMonitor(serverLevel, group, pos);
                }
                case RADAR_BEARING -> {
                    data.attachRadar(group, use.clickedPos(), NetworkData.RadarKind.BEARING);
                    applyFilters(serverLevel, filtererPos);
                }
                case RADAR_STATIONARY -> {
                    data.attachRadar(group, use.clickedPos(), NetworkData.RadarKind.STATIONARY);
                    applyFilters(serverLevel, filtererPos);
                }
                case RADAR_SKY -> {
                    data.attachRadar(group, use.clickedPos(), NetworkData.RadarKind.SKY);
                    applyFilters(serverLevel, filtererPos);
                }
                case RADAR_SONAR -> {
                    data.attachRadar(group, use.clickedPos(), NetworkData.RadarKind.SONAR);
                    applyFilters(serverLevel, filtererPos);
                }
                case CONTROLLER -> data.attachWeaponEndpoint(group, use.clickedPos(), commit.weaponMountPos());
            }
        }

        private static void applyFilters(ServerLevel serverLevel, BlockPos filtererPos) {
            BlockEntity fbe = serverLevel.getBlockEntity(filtererPos);
            if (fbe instanceof NetworkFiltererBlockEntity filterer) {
                filterer.applyFiltersToNetwork();
            }
        }
    }

    private static BlockPos lastShownPos = null;
    private static AABB lastShownAABB = null;

    @OnlyIn(Dist.CLIENT)
    public static void clientTick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack heldItemMainhand = player.getMainHandItem();
        if (!(heldItemMainhand.getItem() instanceof DataLinkBlockItem)) {
            return;
        }

        CustomData customData = heldItemMainhand.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return;
        }

        CompoundTag stackTag = customData.copyTag();

        BlockPos selectedPos = NbtUtils.readBlockPos(stackTag, SELECTED_POS).orElse(null);
        if (selectedPos == null) {
            return;
        }

        if (!selectedPos.equals(lastShownPos)) {
            lastShownAABB = getBounds(selectedPos);
            lastShownPos = selectedPos;
        }

        Outliner.getInstance().showAABB("target", lastShownAABB)
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
