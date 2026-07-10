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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BoolListPacket(boolean mainHand, boolean[] flags, String key) implements CustomPacketPayload {
    private static final int DETECTION_FLAG_COUNT = 7;
    private static final int TARGETING_FLAG_COUNT = 8;

    public static final Type<BoolListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "bool_list")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BoolListPacket> STREAM_CODEC =
            StreamCodec.ofMember(BoolListPacket::encode, BoolListPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(mainHand);
        buf.writeUtf(key);
        buf.writeInt(flags.length);

        for (boolean flag : flags) {
            buf.writeBoolean(flag);
        }
    }

    private static BoolListPacket decode(RegistryFriendlyByteBuf buf) {
        boolean mainHand = buf.readBoolean();
        String key = buf.readUtf();

        int length = buf.readInt();
        boolean[] flags = new boolean[length];

        for (int i = 0; i < length; i++) {
            flags[i] = buf.readBoolean();
        }

        return new BoolListPacket(mainHand, flags, key);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BoolListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (packet.flags == null || packet.flags.length != expectedFlagCount(packet.key)) {
                return;
            }

            InteractionHand hand = packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);

            if (stack.isEmpty()) {
                return;
            }

            CompoundTag root = getCustomTag(stack);
            CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND)
                    ? root.getCompound("Filters")
                    : new CompoundTag();

            if ("detectBools".equals(packet.key)) {
                CompoundTag det = new CompoundTag();
                det.putBoolean("player", packet.flags[0]);
                det.putBoolean("sable", packet.flags[1]);
                det.putBoolean("vs2", packet.flags[1]);
                det.putBoolean("contraption", packet.flags[2]);
                det.putBoolean("mob", packet.flags[3]);
                det.putBoolean("animal", packet.flags[4]);
                det.putBoolean("projectile", packet.flags[5]);
                det.putBoolean("item", packet.flags[6]);

                root.putByteArray(packet.key, toByteArray(packet.flags));
                filters.put("detection", det);
                root.put("Filters", filters);

                setCustomTag(stack, root);
            } else if ("TargetBools".equals(packet.key)) {
                CompoundTag tgt = new CompoundTag();
                tgt.putBoolean("player", packet.flags[0]);
                tgt.putBoolean("contraption", packet.flags[1]);
                tgt.putBoolean("mob", packet.flags[2]);
                tgt.putBoolean("animal", packet.flags[3]);
                tgt.putBoolean("projectile", packet.flags[4]);
                tgt.putBoolean("lineSight", packet.flags[5]);
                tgt.putBoolean("autoTarget", packet.flags[6]);
                tgt.putBoolean("preferHighArc", packet.flags[7]);

                root.putByteArray(packet.key, toByteArray(packet.flags));
                filters.put("targeting", tgt);
                root.put("Filters", filters);

                setCustomTag(stack, root);
            }

            player.setItemInHand(hand, stack);
            player.inventoryMenu.broadcastChanges();
        });
    }

    public static void send(boolean mainHand, boolean[] flags, String key) {
        PacketDistributor.sendToServer(new BoolListPacket(mainHand, flags, key));
    }

    private static int expectedFlagCount(String key) {
        if ("detectBools".equals(key)) {
            return DETECTION_FLAG_COUNT;
        }
        if ("TargetBools".equals(key)) {
            return TARGETING_FLAG_COUNT;
        }
        return -1;
    }

    private static byte[] toByteArray(boolean[] flags) {
        byte[] arr = new byte[flags.length];

        for (int i = 0; i < flags.length; i++) {
            arr[i] = (byte) (flags[i] ? 1 : 0);
        }

        return arr;
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
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
