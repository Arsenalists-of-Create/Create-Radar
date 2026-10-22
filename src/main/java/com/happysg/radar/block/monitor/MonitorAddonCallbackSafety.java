
package com.happysg.radar.block.monitor;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class MonitorAddonCallbackSafety {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> WARNED =
            ConcurrentHashMap.newKeySet();

    private MonitorAddonCallbackSafety() {}

    static void warnOnce(String operation, Object callback, RuntimeException exception) {
        String callbackName = callback == null ? "unknown" : callback.getClass().getName();
        String key = operation + ":" + callbackName;

        if (WARNED.add(key)) {
            LOGGER.warn("Monitor addon callback '{}' failed in {}", operation, callbackName, exception);
        }
    }
}
