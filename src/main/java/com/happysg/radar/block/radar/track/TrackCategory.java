package com.happysg.radar.block.radar.track;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.entity.Entity;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;

public enum TrackCategory {
    PLAYER,
    MOB,
    HOSTILE,
    ANIMAL,
    VS2,
    PROJECTILE,
    CONTRAPTION,
    ITEM,
    MISC;


    public static TrackCategory get(Entity entity) {
        if (entity instanceof Player) {
            return PLAYER;
        } else if (entity instanceof Animal) {
            return ANIMAL;
        } else if (entity instanceof Enemy){
            return HOSTILE;
        }else if (entity instanceof ItemEntity){
            return ITEM;
        } else if (entity instanceof AbstractContraptionEntity) {
            return CONTRAPTION;
        } else if (entity instanceof Projectile) {
            return PROJECTILE;
        }
        return MISC;
    }
}