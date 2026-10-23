package com.happysg.radar.client;

import com.happysg.radar.CreateRadar;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

public final class RadarSoundSettingsHandler {
    private RadarSoundSettingsHandler() {}

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (original == null) return;

        ResourceLocation id = original.getLocation();

        if (!CreateRadar.MODID.equals(id.getNamespace())) {
            return;
        }

        float multiplier = RadarSoundVolume.multiplier(id);

        if (multiplier <= 0.0f) {
            event.setSound(null);
            return;
        }

        if (multiplier >= 1.0f) {
            return;
        }

        if (original instanceof TickableSoundInstance) {
            return;
        }

        event.setSound(new VolumeAdjustedSound(original, multiplier));
    }

    private record VolumeAdjustedSound(SoundInstance original, float multiplier) implements SoundInstance {

        @Override
        public ResourceLocation getLocation() {
            return original.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return original.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return original.getSound();
        }

        @Override
        public SoundSource getSource() {
            return original.getSource();
        }

        @Override
        public boolean isLooping() {
            return original.isLooping();
        }

        @Override
        public int getDelay() {
            return original.getDelay();
        }

        @Override
        public float getVolume() {
            return original.getVolume() * multiplier;
        }

        @Override
        public float getPitch() {
            return original.getPitch();
        }

        @Override
        public double getX() {
            return original.getX();
        }

        @Override
        public double getY() {
            return original.getY();
        }

        @Override
        public double getZ() {
            return original.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return original.getAttenuation();
        }

        @Override
        public boolean isRelative() {
            return original.isRelative();
        }

        @Override
        public boolean canStartSilent() {
            return original.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return original.canPlaySound();
        }
    }
}
