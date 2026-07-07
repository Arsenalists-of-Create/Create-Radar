package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.rwr.RwrLockLoopSoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RwrLockSoundPacket(BlockPos rwrPos, boolean locked, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<RwrLockSoundPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "rwr_lock_sound")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RwrLockSoundPacket> STREAM_CODEC =
            StreamCodec.ofMember(RwrLockSoundPacket::encode, RwrLockSoundPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(rwrPos);
        buf.writeBoolean(locked);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    private static RwrLockSoundPacket decode(RegistryFriendlyByteBuf buf) {
        return new RwrLockSoundPacket(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RwrLockSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(RwrLockSoundPacket packet) {
            RwrLockLoopSoundManager.setLocked(
                    packet.rwrPos(),
                    new Vec3(packet.x(), packet.y(), packet.z()),
                    packet.locked()
            );
        }
    }
}
