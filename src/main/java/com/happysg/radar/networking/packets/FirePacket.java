package com.happysg.radar.networking.packets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.NbtHelper;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FirePacket extends SimplePacketBase {
    public static final Type<FirePacket> TYPE = new Type<>(CreateRadar.asResource("fire_packet"));
    public static final StreamCodec<FriendlyByteBuf, FirePacket> STREAM_CODEC = createCodec(FirePacket::new);

    private final boolean enable;

    public FirePacket(boolean enable) {
        this.enable = enable;
    }

    public FirePacket(FriendlyByteBuf buffer) {
        this.enable = buffer.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enable);
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            ItemStack binos = player.getMainHandItem();
            if (!(binos.getItem() instanceof Binoculars)) return;

            BlockPos filtererPos = getFiltererPos(binos);
            if (filtererPos == null) return;
            if (!serverLevel.isLoaded(filtererPos)) return;

            if (!(serverLevel.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity filtererBe)) return;

            if (enable) {
                BlockPos hit = Binoculars.getLastHit(binos);
                if (hit == null) return;
                filtererBe.onBinocularsTriggered(player, binos, false);
            } else {
                filtererBe.onBinocularsTriggered(player, binos, true);
            }
            filtererBe.setChanged();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static BlockPos getFiltererPos(ItemStack stack) {
        CompoundTag tag = NbtHelper.getTag(stack);
        if (!tag.contains("filtererPos")) return null;
        return NbtUtils.readBlockPos(tag, "filtererPos").orElse(null);
    }
}