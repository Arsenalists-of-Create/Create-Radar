package com.happysg.radar.compat.cbcmw;

import com.happysg.radar.compat.cbc.CannonMountContext;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import riftyboi.cbcmodernwarfare.munitions.contraptions.MunitionsPhysicsContraptionEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CBCMWGuidanceCompat {
    private static final String TARGET_TAG = "CreateRadarCBCMWTarget";
    private record Designation(UUID targetId, long expiresAt) {}
    private static final Map<PitchOrientedContraptionEntity, Designation> PENDING = new WeakHashMap<>();
    private CBCMWGuidanceCompat() {}

    public static boolean prepareLaunch(@Nullable CannonMountContext mount, ServerLevel level, @Nullable Entity target) {
        if (mount == null || !mount.isCurrent()) {
            return false;
        }

        PitchOrientedContraptionEntity mounted = mount.getContraption();

        if (mounted == null || !mounted.isAlive() || !(mounted.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return false;
        }

        CBCMWCannonCompat.LauncherLoadout loadout = CBCMWCannonCompat.resolveLauncherLoadout(cannon, level);

        if (loadout == null) {
            return false;
        }

        if (!loadout.infraredGuidance()) {
            PENDING.remove(mounted);
            return true;
        }

        if (!(target instanceof LivingEntity living) || !living.isAlive() || living.level() != level) {
            PENDING.remove(mounted);
            return false;
        }

        PENDING.put(mounted, new Designation(living.getUUID(), level.getGameTime() + 2));
        return true;
    }

    @Nullable
    private static UUID pendingTarget(PitchOrientedContraptionEntity mounted, Level level) {
        Designation designation = PENDING.get(mounted);

        if (designation == null) {
            return null;
        }

        if (level.getGameTime() > designation.expiresAt()) {
            PENDING.remove(mounted);
            return null;
        }

        return designation.targetId();
    }

    @Nullable
    private static UUID designatedTarget(OrientedContraptionEntity owner, Level level) {
        if (owner instanceof PitchOrientedContraptionEntity mounted) {
            return pendingTarget(mounted, level);
        }

        if (owner instanceof MunitionsPhysicsContraptionEntity missile) {
            var data = missile.getPersistentData();

            return data.hasUUID(TARGET_TAG) ? data.getUUID(TARGET_TAG) : null;
        }

        return null;
    }

    public static List<Entity> restrictSeekerCandidates(List<Entity> nativeEligible, OrientedContraptionEntity owner, Level level) {
        UUID selected = designatedTarget(owner, level);

        if (selected == null) {
            return nativeEligible;
        }

        for (Entity candidate : nativeEligible) {
            if (selected.equals(candidate.getUUID())) {
                return List.of(candidate);
            }
        }

        return List.of();
    }

    public static void attachToSpawn(AbstractMountedCannonContraption launcher, Entity spawned) {
        if (!(spawned instanceof MunitionsPhysicsContraptionEntity missile) || !(launcher.entity instanceof PitchOrientedContraptionEntity mounted)) {
            return;
        }

        UUID selected = pendingTarget(mounted, missile.level());

        if (selected != null) {
            missile.getPersistentData().putUUID(TARGET_TAG, selected);
            PENDING.remove(mounted);
        }
    }
}