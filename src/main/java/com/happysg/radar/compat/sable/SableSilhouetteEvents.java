package com.happysg.radar.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class SableSilhouetteEvents {
    private static boolean registered;

    private SableSilhouetteEvents() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.register(SableSilhouetteEvents.class);
        }
    }

    @SubscribeEvent
    public static void onContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        event.getContainer().addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                if (subLevel.getLevel() instanceof ServerLevel serverLevel) {
                    SableSilhouetteServerCache.remove(serverLevel, subLevel.getUniqueId());
                }
            }

            @Override
            public void tick(SubLevelContainer subLevels) {
                if (subLevels.getLevel() instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 200 == 0) {
                    SableSilhouetteServerCache.prune(serverLevel);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        markDirty(event);
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        markDirty(event);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SableSilhouetteServerCache.clearLevel(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SableSilhouetteServerCache.clearAll();
    }

    private static void markDirty(BlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(serverLevel, event.getPos());
        if (subLevel != null) {
            SableSilhouetteServerCache.markDirty(serverLevel, subLevel.getUniqueId());
        }
    }
}
