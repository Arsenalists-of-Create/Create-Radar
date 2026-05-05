package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.ModMessages;
import com.happysg.radar.networking.packets.FirePacket;
import com.happysg.radar.networking.packets.RaycastPacket;
import com.happysg.radar.registry.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public class BinocularHandler {

    private static boolean wasActionPressed = false;
    private static boolean wasFirePressed = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.player.getMainHandItem().getItem() instanceof Binoculars)) return;

        boolean isUsing = mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof Binoculars;
        boolean isFirePressed = ModKeybinds.BINO_FIRE.isDown() || ModKeybinds.SCOPE_ACTION.isDown();

        if (isUsing) {
            net.minecraft.world.phys.Vec3 eyePosition = mc.player.getEyePosition();
            net.minecraft.world.phys.Vec3 lookVector = mc.player.getViewVector(1.0F);
            net.minecraft.world.phys.Vec3 reachVector = eyePosition.add(lookVector.x * 512.0, lookVector.y * 512.0, lookVector.z * 512.0);

            BlockHitResult hitResult;
                java.util.UUID subLevelId = null;

                if (com.happysg.radar.compat.Mods.SABLE.isLoaded()) {
                    com.happysg.radar.compat.aeronautics.SableUtils.ClipResult result = com.happysg.radar.compat.aeronautics.SableUtils.multiLevelClip(
                            mc.level, eyePosition, reachVector,
                            net.minecraft.world.level.ClipContext.Block.OUTLINE,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);
                    hitResult = result.hit();
                    if (result.subLevel() != null) {
                        subLevelId = result.subLevel().getUniqueId();
                    }
                } else {
                    hitResult = mc.level.clip(new net.minecraft.world.level.ClipContext(eyePosition, reachVector, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                }

                if (isFirePressed != wasFirePressed) {
                    if (hitResult.getType() == HitResult.Type.BLOCK) {
                        ModMessages.sendToServer(new FirePacket(isFirePressed, hitResult.getBlockPos(), subLevelId));
                    } else {
                        ModMessages.sendToServer(new FirePacket(isFirePressed));
                    }
                    wasFirePressed = isFirePressed;
                }

                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = hitResult.getBlockPos();
                    ModMessages.sendToServer(new RaycastPacket(pos, subLevelId));
                }
            } else {
                if (wasFirePressed) {
                ModMessages.sendToServer(new FirePacket(false));
                wasFirePressed = false;
            }
        }
    }
}