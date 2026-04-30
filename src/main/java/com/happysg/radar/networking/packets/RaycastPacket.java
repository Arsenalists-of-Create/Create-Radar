package com.happysg.radar.networking.packets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.compat.stub.simibubi.SimplePacketBase;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RaycastPacket extends SimplePacketBase {
    public static final Type<RaycastPacket> TYPE = new Type<>(CreateRadar.asResource("raycast_packet"));
    public static final StreamCodec<FriendlyByteBuf, RaycastPacket> STREAM_CODEC = createCodec(RaycastPacket::new);

    private final BlockPos pos;

    public RaycastPacket(BlockPos pos) {
        this.pos = pos;
    }

    public RaycastPacket(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            this.pos = buffer.readBlockPos();
        } else {
            this.pos = null;
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(pos != null);
        if (pos != null) {
            buffer.writeBlockPos(pos);
        }
    }

    @Override
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getMainHandItem();
            if (stack.getItem() instanceof Binoculars) {
                Binoculars.setLastHit(stack, pos);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}