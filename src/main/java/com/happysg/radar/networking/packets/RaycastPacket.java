package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record RaycastPacket() implements CustomPacketPayload {

    public static final Type<RaycastPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "raycast")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaycastPacket> STREAM_CODEC =
            StreamCodec.unit(new RaycastPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RaycastPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            if (!player.isUsingItem()) {
                return;
            }

            ItemStack usedStack = player.getUseItem();
            if (!(usedStack.getItem() instanceof Binoculars)) {
                return;
            }

            double maxDistance = RadarConfig.server().binoRaycastRange.get();
            double step = 0.25;

            BlockPos hit = raycastFirstNonTransparentBlock(serverLevel, player, maxDistance, step);

            if (hit != null) {
                Binoculars.setLastHit(usedStack, hit);

                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".binoculars.hit")
                                .append(hit.toShortString()),
                        true
                );
            } else {
                Binoculars.clearLastHit(usedStack);

                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".binoculars.out_of_range"),
                        true
                );
            }
        });
    }

    public static void send() {
        PacketDistributor.sendToServer(new RaycastPacket());
    }

    @Nullable
    private static BlockPos raycastFirstNonTransparentBlock(ServerLevel level, ServerPlayer player, double maxDistance, double step) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        BlockPos lastPos = BlockPos.containing(start);

        for (double t = 0.0; t <= maxDistance; t += step) {
            Vec3 point = start.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(point);

            if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(level, pos)) {
                pos = SableUtils.getWorldPos(level, pos);
            }

            if (pos.equals(lastPos)) {
                continue;
            }

            lastPos = pos;

            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            if (isTransparentPassThrough(level, pos, state)) {
                continue;
            }

            return pos;
        }

        return null;
    }

    private static boolean isTransparentPassThrough(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }

        if (!state.canOcclude() || !state.isSolidRender(level, pos)) {
            return true;
        }

        return !state.getFluidState().isEmpty();
    }

    private static void storeLastHit(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getCustomTag(stack);
        tag.put("LastHitPos", NbtUtils.writeBlockPos(pos));
        setCustomTag(stack, tag);
    }

    private static void clearStoredLastHit(ItemStack stack) {
        CompoundTag tag = getCustomTagOrNull(stack);
        if (tag == null) {
            return;
        }

        tag.remove("LastHitPos");
        setCustomTag(stack, tag);
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    @Nullable
    private static CompoundTag getCustomTagOrNull(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }

        return data.copyTag();
    }

    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
    }
}