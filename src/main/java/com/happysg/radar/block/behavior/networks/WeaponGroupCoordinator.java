package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CreateRadar.MODID)
public final class WeaponGroupCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        Set<BlockPos> processedMounts = new HashSet<>();
        for (WeaponNetworkRuntime.WeaponGroupView group : WeaponNetworkRuntime.getGroups(level)) {
            BlockPos mountPos = group.mountPos();
            if (!processedMounts.add(mountPos)) continue;

            if (group.pitchPos() == null) {
                if (group.yawPos() == null || !level.hasChunkAt(group.yawPos())) continue;
                BlockEntity blockEntity = level.getBlockEntity(group.yawPos());
                if (!(blockEntity instanceof AutoYawControllerBlockEntity yaw)) continue;
                WeaponFiringControl control = yaw.getYawOnlyAimControl();
                if (control == null) continue;
                try {
                    control.refreshControllers();
                    control.tick();
                } catch (RuntimeException exception) {
                    control.resetTarget();
                    yaw.failClosedRadarAim();
                    LOGGER.error("Yaw-only targeting failed at {}", mountPos, exception);
                }
                continue;
            }

            if (!level.hasChunkAt(group.pitchPos())) continue;
            BlockEntity blockEntity = level.getBlockEntity(group.pitchPos());
            if (!(blockEntity instanceof AutoPitchControllerBlockEntity pitch)) continue;
            if (pitch.firingControl == null) pitch.getFiringControl();
            if (pitch.firingControl == null) continue;
            try {
                pitch.firingControl.refreshControllers();
                pitch.firingControl.tick();
            } catch (RuntimeException exception) {
                pitch.firingControl.resetTarget();
                pitch.failClosedRadarAim();
                LOGGER.error("Weapon targeting failed at {}", mountPos, exception);
            }
        }
    }
}
