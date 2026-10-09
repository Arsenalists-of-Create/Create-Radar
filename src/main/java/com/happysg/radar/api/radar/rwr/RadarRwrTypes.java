package com.happysg.radar.api.radar.rwr;

import com.happysg.radar.CreateRadar;
import net.minecraft.resources.ResourceLocation;

public final class RadarRwrTypes {
    public static final ResourceLocation GROUND = ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "ground");

    public static final ResourceLocation SKY = ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "sky");

    public static final ResourceLocation AIRBORNE = ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "airborne");

    public static final ResourceLocation GENERIC = ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "generic");

    private RadarRwrTypes() {}
}