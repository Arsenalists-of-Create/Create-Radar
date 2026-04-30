package com.happysg.radar.ponder;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.registry.ModPonderIndex;
import com.happysg.radar.registry.ModPonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import rbasamoyai.createbigcannons.ponder.CBCPonderScenes;
import rbasamoyai.createbigcannons.ponder.CBCPonderTags;

public class RadarPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() { return CreateRadar.MODID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ModPonderIndex.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ModPonderTags.register(helper);
    }
}