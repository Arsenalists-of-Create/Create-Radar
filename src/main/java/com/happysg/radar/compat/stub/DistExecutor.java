package com.happysg.radar.compat.stub;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.util.function.Supplier;

public class DistExecutor {
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> supplier) {
        if (FMLEnvironment.dist == dist) {
            supplier.get().run();
        }
    }
    
    public static <T> T safeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return clientTarget.get().get();
        } else {
            return serverTarget.get().get();
        }
    }
}
