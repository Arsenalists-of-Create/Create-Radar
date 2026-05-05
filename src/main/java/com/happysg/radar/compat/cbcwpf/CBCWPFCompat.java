package com.happysg.radar.compat.cbcwpf;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import java.util.Map;
import java.util.WeakHashMap;

import static com.happysg.radar.compat.cbcwpf.CBCWPFShupapiumImpl.findBreech;
import static com.happysg.radar.compat.cbcwpf.CBCWPFShupapiumImpl.peekLoadedOrNext;

public final class CBCWPFCompat {
    private CBCWPFCompat() {}

    private static final Logger LOGGER = CreateRadar.getLogger();
    private static final Map<AbstractMountedCannonContraption, Boolean> loggedLoaded = new WeakHashMap<>();

    public static boolean isShupapiumAutocannon(AbstractMountedCannonContraption cannon) {
        return cannon != null
                && Mods.SHUPAPIUM.isLoaded()
                && cannon instanceof IShupapiumACContraptionAccess;
    }

    public static AutocannonMaterial resolveAutocannonMaterial(AbstractMountedCannonContraption cannon) {
        if (!isShupapiumAutocannon(cannon)) return null;
        try {
            return CBCWPFShupapiumImpl.resolveMaterial(cannon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static float resolveShupapiumMuzzleSpeed(AbstractMountedCannonContraption cannon) {
        if (!isShupapiumAutocannon(cannon)) return 0f;
        try {
            return CBCWPFShupapiumImpl.muzzleSpeed(cannon);
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static int resolveLifetimeTicks(AbstractMountedCannonContraption cannon) {
        if (!isShupapiumAutocannon(cannon)) return 0;
        try {
            return CBCWPFShupapiumImpl.lifetimeTicks(cannon);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Used by CannonUtil */
    public static BallisticPropertiesComponent resolveAutocannonBallistics(AbstractMountedCannonContraption cannon, net.minecraft.world.level.Level level) {
        if (!isShupapiumAutocannon(cannon) || level == null) return null;
        try {
            return CBCWPFShupapiumImpl.ballistics(cannon, level);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void debugLogLoadedState(AbstractMountedCannonContraption cannon, net.minecraft.world.level.Level level) {
        if (!isShupapiumAutocannon(cannon) || level == null || level.isClientSide) return;
        if (loggedLoaded.getOrDefault(cannon, false)) return;

        try {
            var breech = findBreech(cannon);
            if (breech == null) return;

            var round = peekLoadedOrNext(cannon, breech);
            if (round.isEmpty()) return;

            int life = resolveLifetimeTicks(cannon);
            float muzzle = resolveShupapiumMuzzleSpeed(cannon);



            loggedLoaded.put(cannon, true);
        } catch (Throwable ignored) {}
    }
}