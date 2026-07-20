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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        Vec3 eyePosition = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 end = eyePosition.add(dir.scale(maxDistance));
        Vec3 start = eyePosition;

        // Sable replaces Level#clip with a sublevel-aware implementation. Aeronautics
        // contraptions live in those sublevels, and the returned BlockPos deliberately
        // remains in sublevel coordinates so the firing controller can follow its motion.
        for (int passThroughs = 0; passThroughs < 256; passThroughs++) {
            BlockHitResult hit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return null;
            }

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !isTransparentPassThrough(level, pos, state)) {
                return pos;
            }

            Vec3 worldHit = hit.getLocation();
            if (Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(level, pos)) {
                worldHit = SableUtils.getWorldVec(level, worldHit);
            }

            start = worldHit.add(dir.scale(Math.max(0.01D, step)));
            if (start.distanceToSqr(eyePosition) > maxDistance * maxDistance) {
                return null;
            }
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
