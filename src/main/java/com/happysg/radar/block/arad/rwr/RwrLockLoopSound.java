package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RwrLockLoopSound extends AbstractTickableSoundInstance {
    private final BlockPos rwrPos;

    public RwrLockLoopSound(BlockPos rwrPos, Vec3 soundPos) {
        super(ModSounds.RWR_LOCK.get(), SoundSource.BLOCKS, RandomSource.create());
        this.rwrPos = rwrPos;
        this.looping = true;
        this.delay = 0;
        this.volume = 2.0f;
        this.pitch = 1.0f;
        updatePosition(soundPos);
    }

    public BlockPos getRwrPos() {
        return rwrPos;
    }

    public void updatePosition(Vec3 soundPos) {
        this.x = soundPos.x;
        this.y = soundPos.y;
        this.z = soundPos.z;
    }

    public void stopLoop() {
        stop();
    }

    @Override
    public void tick() {
    }
}
