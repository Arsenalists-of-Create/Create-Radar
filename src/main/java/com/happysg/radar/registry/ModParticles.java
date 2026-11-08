package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, CreateRadar.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> JAMMER_WAVE = PARTICLES.register("jammer_wave", () -> new SimpleParticleType(false));

    private ModParticles() {}

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}