package com.happysg.radar.compat.hardcorerevival;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class HardcoreRevivalCompat {
    private static final String MOD_ID = "hardcorerevival";
    private static final String REVIVAL_DATA_TAG = "HardcoreRevivalData";
    private static final String KNOCKED_OUT_TAG = "KnockedOut";

    private static final Set<String> METHOD_HINTS = Set.of(
        "reviv", "down", "bleed", "dead", "incapac", "knock"
    );

    private static volatile boolean scanned = false;
    private static volatile MethodInvoker directKnockoutInvoker;
    private static volatile MethodInvoker directManagerInvoker;
    private static volatile MethodInvoker directApiInvoker;
    private static List<MethodInvoker> invokers = List.of();

    private HardcoreRevivalCompat() {
    }

    public static boolean isInReviveState(Player player) {
        if (player == null) {
            return false;
        }

        // Fast-paths that always mean "do not track".
        if (!player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0.0F) {
            return true;
        }

        if (!isModLoaded()) {
            return false;
        }

        ensureScanned();
        MethodInvoker direct = directKnockoutInvoker;
        if (direct != null && direct.invoke(player)) {
            return true;
        }

        MethodInvoker manager = directManagerInvoker;
        if (manager != null && manager.invoke(player)) {
            return true;
        }

        MethodInvoker api = directApiInvoker;
        if (api != null && player instanceof ServerPlayer serverPlayer && api.invoke(serverPlayer)) {
            return true;
        }

        for (MethodInvoker invoker : invokers) {
            if (invoker.invoke(player)) {
                return true;
            }
        }

        if (readPersistentKnockoutFlag(player)) {
            return true;
        }

        return matchesKnockoutStateFallback(player);
    }

    public static Player resolveTrackedPlayer(ServerLevel level, RadarTrack track) {
        if (level == null || track == null || track.trackCategory() != TrackCategory.PLAYER) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(track.getId());
            return level.getPlayerByUUID(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isTrackedPlayerInReviveState(ServerLevel level, RadarTrack track) {
        Player player = resolveTrackedPlayer(level, track);
        return player != null && isInReviveState(player);
    }

    private static synchronized void ensureScanned() {
        if (scanned) {
            return;
        }
        scanned = true;

        try {
            Class<?> manager = Class.forName("net.blay09.mods.hardcorerevival.PlayerHardcoreRevivalManager");
            Method exact = manager.getDeclaredMethod("isKnockedOut", Player.class);
            exact.setAccessible(true);
            directKnockoutInvoker = new MethodInvoker(null, exact);
        } catch (Throwable ignored) {
            // Optional compat: class may not exist in this modpack.
        }

        try {
            Class<?> manager = Class.forName("net.blay09.mods.hardcorerevival.HardcoreRevivalManager");
            Method exact = manager.getDeclaredMethod("isKnockedOut", Player.class);
            exact.setAccessible(true);
            directManagerInvoker = new MethodInvoker(null, exact);
        } catch (Throwable ignored) {
            // Optional compat: class may not exist in this modpack.
        }

        try {
            Class<?> api = Class.forName("net.blay09.mods.hardcorerevival.api.HardcoreRevivalAPI");
            Method exact = api.getDeclaredMethod("isKnockedOut", ServerPlayer.class);
            exact.setAccessible(true);
            directApiInvoker = new MethodInvoker(null, exact);
        } catch (Throwable ignored) {
            // Optional compat: class may not exist in this modpack.
        }

        List<String> candidateClasses = List.of(
            "net.blay09.mods.hardcorerevival.PlayerHardcoreRevivalManager",
            "net.blay09.mods.hardcorerevival.HardcoreRevivalManager",
            "net.blay09.mods.hardcorerevival.api.HardcoreRevivalAPI",
            "de.maxhenkel.hardcorerevival.PlayerState",
            "de.maxhenkel.hardcorerevival.state.PlayerState",
            "de.maxhenkel.hardcorerevival.ReviveManager",
            "de.maxhenkel.hardcorerevival.server.ReviveManager",
            "de.maxhenkel.hardcorerevival.Main"
        );

        List<MethodInvoker> found = new java.util.ArrayList<>();
        for (String className : candidateClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                found.addAll(scanClass(clazz));
            } catch (Throwable ignored) {
                // Optional compat: class may not exist in this mod version.
            }
        }

        invokers = List.copyOf(found);
    }

    private static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static boolean readPersistentKnockoutFlag(Player player) {
        try {
            CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.contains(REVIVAL_DATA_TAG)) {
                return false;
            }

            CompoundTag revivalData = persistentData.getCompound(REVIVAL_DATA_TAG);
            return revivalData.getBoolean(KNOCKED_OUT_TAG);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean matchesKnockoutStateFallback(Player player) {
        try {
            return player.getHealth() <= 1.0F
                    && !player.canBeSeenAsEnemy()
                    && !player.isSpectator();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<MethodInvoker> scanClass(Class<?> clazz) {
        List<MethodInvoker> out = new java.util.ArrayList<>();

        for (Method m : clazz.getDeclaredMethods()) {
            if (!looksLikeStateMethod(m)) {
                continue;
            }

            m.setAccessible(true);
            if (Modifier.isStatic(m.getModifiers())) {
                out.add(new MethodInvoker(null, m));
                continue;
            }

            Object instance = findSingletonInstance(clazz);
            if (instance != null) {
                out.add(new MethodInvoker(instance, m));
            }
        }

        return out;
    }

    private static Object findSingletonInstance(Class<?> clazz) {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!clazz.isAssignableFrom(f.getType())) {
                    continue;
                }
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (!name.equals("instance") && !name.equals("singleton")) {
                    continue;
                }
                f.setAccessible(true);
                Object value = f.get(null);
                if (value != null) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            // Optional compat path.
        }
        return null;
    }

    private static boolean looksLikeStateMethod(Method m) {
        if (m.getReturnType() != boolean.class) {
            return false;
        }
        if (m.getParameterCount() != 1) {
            return false;
        }

        String methodName = m.getName().toLowerCase(Locale.ROOT);
        boolean hinted = METHOD_HINTS.stream().anyMatch(methodName::contains);
        if (!hinted) {
            return false;
        }

        Class<?> param = m.getParameterTypes()[0];
        return param.isAssignableFrom(Player.class)
            || param.isAssignableFrom(java.util.UUID.class);
    }

    private record MethodInvoker(Object owner, Method method) {
        private boolean invoke(Player player) {
            try {
                Class<?> param = method.getParameterTypes()[0];
                Object arg = param.isAssignableFrom(Player.class) ? player : player.getUUID();
                Object result = method.invoke(owner, arg);
                return result instanceof Boolean b && b;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean invoke(ServerPlayer player) {
            try {
                Object result = method.invoke(owner, player);
                return result instanceof Boolean b && b;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
