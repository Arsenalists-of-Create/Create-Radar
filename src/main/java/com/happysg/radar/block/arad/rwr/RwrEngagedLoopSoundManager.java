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
public final class RwrEngagedLoopSoundManager {
    private static final Map<BlockPos, RwrEngagedLoopSound> ACTIVE_ENGAGED_SOUNDS = new HashMap<>();

    private RwrEngagedLoopSoundManager() {
    }

    public static void setEngaged(BlockPos rwrPos, Vec3 soundPos, boolean engaged) {
        if (engaged) {
            startOrUpdate(rwrPos, soundPos);
        } else {
            stop(rwrPos);
        }
    }

    public static void clear() {
        for (RwrEngagedLoopSound sound : ACTIVE_ENGAGED_SOUNDS.values()) {
            sound.stopLoop();
        }
        ACTIVE_ENGAGED_SOUNDS.clear();
    }

    private static void startOrUpdate(BlockPos rwrPos, Vec3 soundPos) {
        Minecraft mc = Minecraft.getInstance();
        RwrEngagedLoopSound existing = ACTIVE_ENGAGED_SOUNDS.get(rwrPos);
        if (existing != null && !existing.isStopped()) {
            existing.updatePosition(soundPos);
            return;
        }

        RwrEngagedLoopSound sound = new RwrEngagedLoopSound(rwrPos, soundPos);
        ACTIVE_ENGAGED_SOUNDS.put(rwrPos, sound);
        mc.getSoundManager().play(sound);
        pruneStopped();
    }

    private static void stop(BlockPos rwrPos) {
        RwrEngagedLoopSound sound = ACTIVE_ENGAGED_SOUNDS.remove(rwrPos);
        if (sound != null) {
            sound.stopLoop();
        }
    }

    private static void pruneStopped() {
        Iterator<Map.Entry<BlockPos, RwrEngagedLoopSound>> it = ACTIVE_ENGAGED_SOUNDS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isStopped()) {
                it.remove();
            }
        }
    }
}
