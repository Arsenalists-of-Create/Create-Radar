package com.happysg.radar.block.arad.rwr;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class RwrLockLoopSoundManager {
    private static final Map<BlockPos, RwrLockLoopSound> ACTIVE_LOCK_SOUNDS = new HashMap<>();

    private RwrLockLoopSoundManager() {
    }

    public static void setLocked(BlockPos rwrPos, Vec3 soundPos, boolean locked) {
        if (locked) {
            startOrUpdate(rwrPos, soundPos);
        } else {
            stop(rwrPos);
        }
    }

    public static void clear() {
        for (RwrLockLoopSound sound : ACTIVE_LOCK_SOUNDS.values()) {
            sound.stopLoop();
        }
        ACTIVE_LOCK_SOUNDS.clear();
    }

    private static void startOrUpdate(BlockPos rwrPos, Vec3 soundPos) {
        Minecraft mc = Minecraft.getInstance();
        RwrLockLoopSound existing = ACTIVE_LOCK_SOUNDS.get(rwrPos);
        if (existing != null && !existing.isStopped()) {
            existing.updatePosition(soundPos);
            return;
        }

        RwrLockLoopSound sound = new RwrLockLoopSound(rwrPos, soundPos);
        ACTIVE_LOCK_SOUNDS.put(rwrPos, sound);
        mc.getSoundManager().play(sound);
        pruneStopped();
    }

    private static void stop(BlockPos rwrPos) {
        RwrLockLoopSound sound = ACTIVE_LOCK_SOUNDS.remove(rwrPos);
        if (sound != null) {
            sound.stopLoop();
        }
    }

    private static void pruneStopped() {
        Iterator<Map.Entry<BlockPos, RwrLockLoopSound>> it = ACTIVE_LOCK_SOUNDS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isStopped()) {
                it.remove();
            }
        }
    }
}
