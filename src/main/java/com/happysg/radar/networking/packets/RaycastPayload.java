package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record RaycastPayload() implements CustomPacketPayload {

    private static final double MAX_DISTANCE = 256.0;
    private static final double STEP = 0.25;

    public static final Type<RaycastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "raycast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaycastPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new RaycastPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaycastPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            if (!player.isUsingItem()) return;
            if (!(player.getUseItem().getItem() instanceof Binoculars)) return;

            BlockPos hit = raycastFirstNonTransparentBlock(serverLevel, player, MAX_DISTANCE, STEP);

            if (hit != null) {
                Binoculars.setLastHit(player.getUseItem(), hit);
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".binoculars.hit").append(hit.toShortString()),
                        true
                );
            } else {
                Binoculars.clearLastHit(player.getUseItem());
                player.displayClientMessage(
                        Component.translatable(CreateRadar.MODID + ".binoculars.out_of_range"),
                        true
                );
            }
        });
    }

    @Nullable
    private static BlockPos raycastFirstNonTransparentBlock(ServerLevel level, Player player, double maxDistance, double step) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        BlockPos lastPos = BlockPos.containing(start);

        for (double t = 0.0; t <= maxDistance; t += step) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);

            if (pos.equals(lastPos)) continue;
            lastPos = pos;

            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (isTransparentPassThrough(level, pos, state)) continue;

            return pos;
        }
        return null;
    }

    private static boolean isTransparentPassThrough(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return true;
        if (!state.canOcclude() || !state.isSolidRender(level, pos)) return true;
        if (!state.getFluidState().isEmpty()) return true;
        return false;
    }
}
