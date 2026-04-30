package com.happysg.radar.compat.stub.simibubi;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.Function;

public abstract class SimplePacketBase implements CustomPacketPayload {
    public abstract void write(FriendlyByteBuf buffer);
    public abstract void handle(IPayloadContext context);

    public static <T extends SimplePacketBase> StreamCodec<FriendlyByteBuf, T> createCodec(Function<FriendlyByteBuf, T> decoder) {
        return CustomPacketPayload.codec(SimplePacketBase::write, (net.minecraft.network.codec.StreamDecoder<FriendlyByteBuf, T>)decoder::apply);
    }
}
