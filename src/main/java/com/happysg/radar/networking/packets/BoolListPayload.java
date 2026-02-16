package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BoolListPayload(boolean mainHand, boolean[] flags, String key) implements CustomPacketPayload {

    private static final int EXPECTED_FLAG_COUNT = 7;

    public static final Type<BoolListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "bool_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoolListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg.mainHand);
                        buf.writeUtf(msg.key == null ? "" : msg.key, 32767);
                        boolean[] f = msg.flags == null ? new boolean[0] : msg.flags;
                        buf.writeVarInt(f.length);
                        for (boolean b : f) buf.writeBoolean(b);
                    },
                    buf -> {
                        boolean main = buf.readBoolean();
                        String key = buf.readUtf(32767);
                        int len = buf.readVarInt();
                        boolean[] f = new boolean[len];
                        for (int i = 0; i < len; i++) f[i] = buf.readBoolean();
                        return new BoolListPayload(main, f, key);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BoolListPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (pkt.flags == null) return;
            if (pkt.flags.length != EXPECTED_FLAG_COUNT) return;

            InteractionHand hand = pkt.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) return;

            try {
                if ("detectBools".equals(pkt.key)) {
                    CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND) ? root.getCompound("Filters") : new CompoundTag();

                    CompoundTag det = new CompoundTag();
                    det.putBoolean("player", pkt.flags[0]);
                    det.putBoolean("vs2", pkt.flags[1]);
                    det.putBoolean("contraption", pkt.flags[2]);
                    det.putBoolean("mob", pkt.flags[3]);
                    det.putBoolean("animal", pkt.flags[4]);
                    det.putBoolean("projectile", pkt.flags[5]);
                    det.putBoolean("item", pkt.flags[6]);

                    filters.put("detection", det);
                    root.put("Filters", filters);
                    root.putByteArray(pkt.key, toByteArray(pkt.flags));

                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

                } else if ("TargetBools".equals(pkt.key)) {
                    CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND) ? root.getCompound("Filters") : new CompoundTag();

                    CompoundTag tgt = new CompoundTag();
                    tgt.putBoolean("player", pkt.flags[0]);
                    tgt.putBoolean("contraption", pkt.flags[1]);
                    tgt.putBoolean("mob", pkt.flags[2]);
                    tgt.putBoolean("animal", pkt.flags[3]);
                    tgt.putBoolean("projectile", pkt.flags[4]);
                    tgt.putBoolean("lineSight", pkt.flags[5]);
                    tgt.putBoolean("autoTarget", pkt.flags[6]);

                    filters.put("targeting", tgt);
                    root.put("Filters", filters);
                    root.putByteArray(pkt.key, toByteArray(pkt.flags));

                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                }

                player.setItemInHand(hand, stack);
                player.inventoryMenu.broadcastChanges();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private static byte[] toByteArray(boolean[] flags) {
        byte[] arr = new byte[flags.length];
        for (int i = 0; i < flags.length; i++) arr[i] = (byte) (flags[i] ? 1 : 0);
        return arr;
    }
}
