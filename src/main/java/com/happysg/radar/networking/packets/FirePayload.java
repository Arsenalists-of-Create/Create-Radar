package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record FirePayload(boolean enable) implements CustomPacketPayload {

    private static final String TAG_FILTERER_POS = "filtererPos";

    public static final Type<FirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FirePayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBoolean(msg.enable),
                    buf -> new FirePayload(buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FirePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            ItemStack binos = player.getMainHandItem();
            if (!(binos.getItem() instanceof Binoculars)) return;

            BlockPos filtererPos = getFiltererPos(binos);
            if (filtererPos == null) return;
            if (!serverLevel.isLoaded(filtererPos)) return;

            if (!(serverLevel.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity filtererBe)) return;

            if (msg.enable) {
                BlockPos hit = Binoculars.getLastHit(binos);
                if (hit == null) return;
                filtererBe.onBinocularsTriggered(player, binos, false);
            } else {
                filtererBe.onBinocularsTriggered(player, binos, true);
            }

            filtererBe.setChanged();
        });
    }

    @Nullable
    private static BlockPos getFiltererPos(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        CompoundTag tag = custom.getUnsafe();
        return NbtUtils.readBlockPos(tag, TAG_FILTERER_POS).orElse(null);
    }
}
