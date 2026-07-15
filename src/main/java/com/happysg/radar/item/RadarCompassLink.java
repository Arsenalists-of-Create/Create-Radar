package com.happysg.radar.item;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.simulated.SimulatedRadarCompassCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Persistent link between a vanilla compass and a radar network controller.
 */
public final class RadarCompassLink {
    private static final String LINK_TAG = "CreateRadarCompass";
    private static final String CONTROLLER_DIMENSION_TAG = "ControllerDimension";
    private static final String CONTROLLER_POS_TAG = "ControllerPos";

    private RadarCompassLink() {
    }

    public static void bind(ItemStack stack, ServerLevel level, BlockPos controllerPos) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag link = new CompoundTag();
        link.putString(CONTROLLER_DIMENSION_TAG, level.dimension().location().toString());
        link.put(CONTROLLER_POS_TAG, NbtUtils.writeBlockPos(controllerPos));
        root.put(LINK_TAG, link);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        clearSimulatedLodestoneTracker(stack);
        refreshLodestoneTarget(stack, level);
    }

    public static boolean isLinked(ItemStack stack) {
        return readLink(stack) != null;
    }

    /**
     * Returns true when two radar compass stacks differ only in their live target data.
     */
    public static boolean matchesIgnoringLiveTarget(ItemStack first, ItemStack second) {
        if (!isLinked(first) || !isLinked(second)) {
            return false;
        }

        ItemStack firstComparison = first.copy();
        ItemStack secondComparison = second.copy();
        firstComparison.remove(DataComponents.LODESTONE_TRACKER);
        secondComparison.remove(DataComponents.LODESTONE_TRACKER);
        clearSimulatedLodestoneTracker(firstComparison);
        clearSimulatedLodestoneTracker(secondComparison);
        return ItemStack.matches(firstComparison, secondComparison);
    }

    /**
     * Refreshes the vanilla compass component while the stack is ticking in an inventory.
     */
    public static void refreshLodestoneTarget(ItemStack stack, ServerLevel inventoryLevel) {
        Link link = readLink(stack);
        if (link == null) {
            return;
        }

        clearSimulatedLodestoneTracker(stack);

        Vec3 target = resolveTarget(inventoryLevel.getServer(), link);
        Optional<GlobalPos> globalTarget = target == null
                ? Optional.empty()
                : Optional.of(GlobalPos.of(link.dimension(), BlockPos.containing(target)));
        LodestoneTracker desired = new LodestoneTracker(globalTarget, false);
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (!desired.equals(current)) {
            stack.set(DataComponents.LODESTONE_TRACKER, desired);
        }
    }

    /**
     * Resolves a target for a navigation device. Cross-dimensional links are inactive.
     */
    public static @Nullable Vec3 resolveNavigationTarget(Level navigationLevel, ItemStack stack) {
        if (!(navigationLevel instanceof ServerLevel serverLevel)) {
            return null;
        }

        Link link = readLink(stack);
        if (link == null || !serverLevel.dimension().equals(link.dimension())) {
            return null;
        }

        return resolveTarget(serverLevel.getServer(), link);
    }

    private static @Nullable Vec3 resolveTarget(MinecraftServer server, Link link) {
        ServerLevel controllerLevel = server.getLevel(link.dimension());
        if (controllerLevel == null || !controllerLevel.isLoaded(link.controllerPos())) {
            return null;
        }

        if (!(controllerLevel.getBlockEntity(link.controllerPos()) instanceof NetworkFiltererBlockEntity controller)) {
            return null;
        }

        return controller.resolveLiveSelectedTargetPosition();
    }

    private static void clearSimulatedLodestoneTracker(ItemStack stack) {
        if (Mods.SIMULATED.isLoaded()) {
            SimulatedRadarCompassCompat.clearPhysicalLodestoneTracker(stack);
        }
    }

    private static @Nullable Link readLink(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }

        CompoundTag root = customData.copyTag();
        if (!root.contains(LINK_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag link = root.getCompound(LINK_TAG);
        if (!link.contains(CONTROLLER_DIMENSION_TAG, Tag.TAG_STRING)) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(link.getString(CONTROLLER_DIMENSION_TAG));
        BlockPos controllerPos = NbtUtils.readBlockPos(link, CONTROLLER_POS_TAG).orElse(null);
        if (dimensionId == null || controllerPos == null) {
            return null;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new Link(dimension, controllerPos);
    }

    private record Link(ResourceKey<Level> dimension, BlockPos controllerPos) {
    }
}
