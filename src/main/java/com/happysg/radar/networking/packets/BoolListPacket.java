package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class BoolListPacket extends SimplePacketBase {
    public static final Type<BoolListPacket> TYPE = new Type<>(CreateRadar.asResource("bool_list_packet"));
    public static final StreamCodec<FriendlyByteBuf, BoolListPacket> STREAM_CODEC = createCodec(BoolListPacket::new);

    private final boolean flag;
    private final List<Boolean> list;
    private final String key;

    public BoolListPacket(boolean flag, boolean[] list, String key) {
        this.flag = flag;
        this.list = new ArrayList<>();
        for (boolean b : list) this.list.add(b);
        this.key = key;
    }

    public BoolListPacket(List<Boolean> list) {
        this.flag = false;
        this.list = list;
        this.key = "";
    }

    public BoolListPacket(FriendlyByteBuf buffer) {
        this.flag = buffer.readBoolean();
        int size = buffer.readInt();
        this.list = new ArrayList<>();
        for (int i = 0; i < size; i++) this.list.add(buffer.readBoolean());
        this.key = buffer.readUtf();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(flag);
        buffer.writeInt(list.size());
        for (boolean b : list) buffer.writeBoolean(b);
        buffer.writeUtf(key);
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty() || !stack.getItem().equals(com.happysg.radar.registry.ModItems.RADAR_FILTER_ITEM.get()) 
                && !stack.getItem().equals(com.happysg.radar.registry.ModItems.IDENT_FILTER_ITEM.get())
                && !stack.getItem().equals(com.happysg.radar.registry.ModItems.TARGET_FILTER_ITEM.get())) {
                stack = player.getOffhandItem();
            }
            if (stack.isEmpty()) return;
            
            boolean[] flagsArr = new boolean[list.size()];
            for (int i = 0; i < list.size(); i++) flagsArr[i] = list.get(i);
            
            com.happysg.radar.networking.networkhandlers.BoolNBThelper.saveBooleansAsBytes(stack, flagsArr, key);
            player.getInventory().setChanged();
        });
    }

    public static void send(boolean flag, boolean[] list, String key) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload)new BoolListPacket(flag, list, key));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}