
package com.happysg.radar.block.radar.sonar.bearing;

import com.happysg.radar.api.physics.RadarPhysics;
import com.happysg.radar.client.RadarSoundVolume;
import com.happysg.radar.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class SonarHumLoopSoundManager {
    private static final double HEARING_DISTANCE_SQR = 48.0 * 48.0;
    private static final Map<SonarBearingBlockEntity, HumSound> ACTIVE = new IdentityHashMap<>();

    private SonarHumLoopSoundManager() {
    }

    public static void update(SonarBearingBlockEntity sonar) {
        Minecraft mc = Minecraft.getInstance();
        HumSound existing = ACTIVE.get(sonar);

        Vec3 position = RadarPhysics.worldPosition(sonar);
        boolean nearby = mc.player != null && mc.level != null && mc.player.position().distanceToSqr(position) <= HEARING_DISTANCE_SQR;

        if (!sonar.isRunning() || sonar.isRemoved() || !nearby) {
            if (existing != null) {
                existing.stopLoop();
                ACTIVE.remove(sonar);
            }
            return;
        }

        if (existing != null && !existing.isStopped()) {
            return;
        }

        HumSound sound = new HumSound(sonar);
        ACTIVE.put(sonar, sound);
        mc.getSoundManager().play(sound);
    }

    public static void pruneStopped() {
        Iterator<Map.Entry<SonarBearingBlockEntity, HumSound>> iterator = ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<SonarBearingBlockEntity, HumSound> entry = iterator.next();

            if (entry.getKey().isRemoved() || entry.getValue().isStopped()) {
                entry.getValue().stopLoop();
                iterator.remove();
            }
        }
    }

    public static void clear() {
        for (HumSound sound : ACTIVE.values()) {
            sound.stopLoop();
        }

        ACTIVE.clear();
    }

    private static final class HumSound extends AbstractTickableSoundInstance {

        private final SonarBearingBlockEntity sonar;
        private final Level sourceLevel;

        private HumSound(SonarBearingBlockEntity sonar) {
            super(ModSounds.SONAR_HUM.get(), SoundSource.BLOCKS, RandomSource.create());

            this.sonar = sonar;
            this.sourceLevel = sonar.getLevel();

            this.looping = true;
            this.delay = 0;
            this.volume = 0.65f;
            this.pitch = 1.0f;

            tick();
        }

        @Override
        public void tick() {
            if (sonar.isRemoved() || sonar.getLevel() != sourceLevel || !sonar.isRunning()) {
                stop();
                return;
            }

            Vec3 position = RadarPhysics.worldPosition(sonar);

            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
        }

        private void stopLoop() {
            stop();
        }

        @Override
        public float getVolume() {
            return super.getVolume() * RadarSoundVolume.multiplier(getLocation());
        }
    }
}
