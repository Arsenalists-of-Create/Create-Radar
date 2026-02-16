package com.happysg.radar.block.radar.track;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;
import org.valkyrienskies.core.api.ships.Ship;

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
            if (entity instanceof Player) return PLAYER;
            if (Mods.VALKYRIENSKIES.isLoaded() && entity instanceof Ship) return VS2;
            if (entity instanceof Enemy) return HOSTILE;
            if (entity instanceof Animal) return ANIMAL;
            if (entity instanceof Mob) return MOB;
            if (entity instanceof Projectile) return PROJECTILE;
            if (entity instanceof AbstractContraptionEntity) return CONTRAPTION;
            if (entity instanceof ItemEntity) return ITEM;
            return MISC;
    }
}
