package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public record RwrTargetReference(
        Kind kind,
        @Nullable UUID uuid,
        @Nullable BlockPos blockPos,
        @Nullable Vec3 worldPosition
) {
    public enum Kind {
        ENTITY,
        SABLE_SHIP,
        BLOCK,
        WORLD_POSITION
    }

    public static RwrTargetReference entity(Entity entity) {
        return entity(entity.getUUID());
    }

    public static RwrTargetReference entity(UUID uuid) {
        return new RwrTargetReference(Kind.ENTITY, uuid, null, null);
    }

    public static RwrTargetReference sableShip(UUID uuid) {
        return new RwrTargetReference(Kind.SABLE_SHIP, uuid, null, null);
    }

    public static RwrTargetReference block(BlockPos pos) {
        return new RwrTargetReference(Kind.BLOCK, null, pos.immutable(), null);
    }

    public static RwrTargetReference worldPosition(Vec3 pos) {
        return new RwrTargetReference(Kind.WORLD_POSITION, null, null, pos);
    }

    public Optional<Vec3> resolvePosition(ServerLevel level) {
        return Optional.ofNullable(switch (kind) {
            case ENTITY -> {
                Entity entity = resolveEntity(level);
                yield entity != null && entity.isAlive() ? entity.position() : null;
            }
            case SABLE_SHIP -> {
                SubLevelAccess ship = resolveSableShip(level);
                yield ship == null ? null : RadarTrackUtil.getPosition(ship);
            }
            case BLOCK -> blockPos == null ? null : PhysicsHandler.getWorldVec(level, blockPos);
            case WORLD_POSITION -> worldPosition;
        });
    }

    public @Nullable Entity resolveEntity(ServerLevel level) {
        return uuid == null ? null : level.getEntity(uuid);
    }

    public @Nullable SubLevelAccess resolveSableShip(ServerLevel level) {
        if (!Mods.SABLE.isLoaded() || uuid == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(uuid);
    }

    public @Nullable TrackCategory resolveTrackCategory(ServerLevel level) {
        return switch (kind) {
            case ENTITY -> {
                Entity entity = resolveEntity(level);
                yield entity == null || !entity.isAlive() ? null : TrackCategory.get(entity);
            }
            case SABLE_SHIP -> resolveSableShip(level) == null ? null : TrackCategory.SABLE;
            case BLOCK, WORLD_POSITION -> null;
        };
    }

    public Optional<RwrTargetKey> lockKey(ServerLevel level) {
        return Optional.ofNullable(switch (kind) {
            case ENTITY -> {
                Entity entity = resolveEntity(level);
                yield entity != null && entity.isAlive()
                        ? new RwrTargetKey(RwrTargetKey.Kind.ENTITY, entity.getUUID())
                        : null;
            }
            case SABLE_SHIP -> resolveSableShip(level) == null
                    ? null
                    : new RwrTargetKey(RwrTargetKey.Kind.SABLE_SHIP, uuid);
            case BLOCK, WORLD_POSITION -> null;
        });
    }

    public boolean isLiveLockTarget(ServerLevel level) {
        return switch (kind) {
            case ENTITY -> {
                Entity entity = resolveEntity(level);
                yield entity != null && entity.isAlive()
                        && (entity instanceof Player
                        || entity instanceof Projectile
                        || entity instanceof ItemEntity
                        || entity instanceof AbstractContraptionEntity
                        || TrackCategory.get(entity) != TrackCategory.MISC);
            }
            case SABLE_SHIP -> resolveSableShip(level) != null;
            case BLOCK, WORLD_POSITION -> false;
        };
    }
}
