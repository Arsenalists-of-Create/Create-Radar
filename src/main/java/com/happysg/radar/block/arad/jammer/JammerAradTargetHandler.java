package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.api.arad.ARADTargetDesignationEvent;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RadarType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

/** Delivers monitor designations to every directional jammer on the same ARAD group. */
@EventBusSubscriber(modid = CreateRadar.MODID)
public final class JammerAradTargetHandler {
    private JammerAradTargetHandler() {
    }

    @SubscribeEvent
    public static void onTargetDesignation(ARADTargetDesignationEvent event) {
        ARADData.Group group = ARADData.get(event.level())
                .getGroup(event.level().dimension(), event.rwrPos());
        if (group == null || group.jammerEndpoints.isEmpty()) {
            return;
        }

        RadarType radarType = event.sourceId() == null
                ? RadarType.GROUND
                : ARADTargeting.resolveNativeRadar(event.level(), event.sourceId())
                .map(radar -> radar.getRadarTypeEnum())
                .orElseGet(() -> ExternalRwrEmitterRegistry
                        .resolveSelectable(event.level(), event.sourceId())
                        .map(ExternalRwrEmitterRegistry.EmitterState::radarType)
                        .orElse(RadarType.GROUND));
        for (BlockPos jammerPos : List.copyOf(group.jammerEndpoints)) {
            BlockEntity blockEntity = event.level().getBlockEntity(jammerPos);
            if (!(blockEntity instanceof JammerBlockEntity jammer)) {
                continue;
            }
            if (event.action() == ARADTargetDesignationEvent.Action.ASSIGN) {
                jammer.receiveAradSelection(event.monitorPos(), event.sourceId(),
                        event.target(), radarType);
            } else {
                jammer.clearAradSelection(event.monitorPos());
            }
        }
    }
}
