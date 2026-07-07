package com.happysg.radar.block.radar.track;

import com.happysg.radar.CreateRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class RadarEntityTypeTags {

//    public static final TagKey<EntityType<?>> RADAR_PLAYER =
//            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(CreateRadar.MODID, "radar_player"));

    public static final TagKey<EntityType<?>> RADAR_HOSTILE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_hostile"));

    public static final TagKey<EntityType<?>> RADAR_ANIMAL =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_animal"));

    public static final TagKey<EntityType<?>> RADAR_MOB =
            TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_mob"));

    public static final TagKey<EntityType<?>> RADAR_PROJECTILE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_projectile"));

    public static final TagKey<EntityType<?>> RADAR_MISSILE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_missile"));

    public static final TagKey<EntityType<?>> RADAR_ITEM =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_item"));

    private RadarEntityTypeTags() {
    }
}
