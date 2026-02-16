package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateRadar.MODID);
    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        // i use variable range events so minecraft handles distance falloff normally
        return SOUND_EVENTS.register(id, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, id)));
    }
    public static final DeferredHolder<SoundEvent, SoundEvent> RWR_LOCK =
            register("rwr.lock");

    public static final DeferredHolder<SoundEvent, SoundEvent> RWR_IN_RANGE =
            register("rwr.in_range");

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}

