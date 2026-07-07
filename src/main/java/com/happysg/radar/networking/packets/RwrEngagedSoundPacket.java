package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.rwr.RwrEngagedLoopSoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RwrEngagedSoundPacket(BlockPos rwrPos, boolean engaged, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<RwrEngagedSoundPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "rwr_engaged_sound")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RwrEngagedSoundPacket> STREAM_CODEC =
            StreamCodec.ofMember(RwrEngagedSoundPacket::encode, RwrEngagedSoundPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(rwrPos);
        buf.writeBoolean(engaged);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    private static RwrEngagedSoundPacket decode(RegistryFriendlyByteBuf buf) {
        return new RwrEngagedSoundPacket(
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

    public static void handle(RwrEngagedSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Client.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private static void handle(RwrEngagedSoundPacket packet) {
            RwrEngagedLoopSoundManager.setEngaged(
                    packet.rwrPos(),
                    new Vec3(packet.x(), packet.y(), packet.z()),
                    packet.engaged()
            );
        }
    }
}
