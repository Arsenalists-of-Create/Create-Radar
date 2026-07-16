package com.happysg.radar.block.monitor;

import com.happysg.radar.api.arad.ARADTargetDesignationEvent;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.compat.Mods;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Objects;

/** Server-authoritative assignment and liveness handling for ARAD-linked monitors. */
final class ARADTargetDesignationHandler {
    private ARADTargetDesignationHandler() {
    }

    static boolean assign(ServerLevel level, MonitorBlockEntity monitor, String sourceId) {
        MonitorBlockEntity controller = monitor.getController();
        if (!controller.isAradLinked() || sourceId == null || sourceId.isBlank()) {
            return false;
        }

        BlockPos monitorPos = controller.getControllerPos();
        BlockPos rwrPos = ARADData.get(level).getRwrForMonitor(level.dimension(), monitorPos);
        if (rwrPos == null
                || !(level.getBlockEntity(rwrPos) instanceof RadarWarningReceiverBlockEntity rwr)) {
            return false;
        }

        boolean displayedByRwr = rwr.getRadarContacts(level).stream()
                .anyMatch(contact -> sourceId.equals(contact.sourceId()));
        if (!displayedByRwr) {
            return false;
        }

        SubLevelAccess receiverSublevel = Mods.SABLE.isLoaded()
                ? SableCompanion.INSTANCE.getContaining(level, rwrPos)
                : null;
        if (receiverSublevel == null) {
            return false;
        }

        ARADTargeting.Receiver receiver = ARADTargeting
                .sableReceiver(level, receiverSublevel.getUniqueId())
                .orElse(null);
        if (receiver == null) {
            return false;
        }

        ARADTargeting.NativeRadarContact contact = ARADTargeting
                .resolveNativeContact(level, receiver, sourceId)
                .orElse(null);
        if (contact == null) {
            return false;
        }

        ARADTargetDesignationEvent.Target target = ARADTargeting.createNoisyTarget(
                level,
                contact,
                level.getRandom()
        );
        if (target == null) {
            return false;
        }
        controller.setRwrSelectionState(sourceId, contact.radarPos(), rwrPos);
        NeoForge.EVENT_BUS.post(new ARADTargetDesignationEvent(
                ARADTargetDesignationEvent.Action.ASSIGN,
                level,
                rwrPos,
                monitorPos,
                sourceId,
                target
        ));
        controller.setChanged();
        controller.sendData();
        return true;
    }

    static void validateSelection(ServerLevel level, MonitorBlockEntity monitor) {
        MonitorBlockEntity controller = monitor.getController();
        String sourceId = controller.getSelectedRwrSource();
        if (sourceId == null) {
            return;
        }

        BlockPos rwrPos = controller.getSelectedRwrPos();
        BlockPos selectedRadarPos = controller.getSelectedRwrRadarPos();
        BlockPos linkedRwrPos = controller.isAradLinked()
                ? ARADData.get(level).getRwrForMonitor(level.dimension(), controller.getControllerPos())
                : null;
        boolean live = rwrPos != null
                && selectedRadarPos != null
                && Objects.equals(rwrPos, linkedRwrPos)
                && level.getBlockEntity(rwrPos) instanceof RadarWarningReceiverBlockEntity rwr
                && ARADTargeting.resolveNativeRadar(level, sourceId)
                        .filter(radar -> radar.getWorldPos().equals(selectedRadarPos))
                        .isPresent();
        if (!live) {
            clear(level, controller);
        }
    }

    static void clear(ServerLevel level, MonitorBlockEntity monitor) {
        clear(level, monitor, false);
    }

    static void clearFromPlayer(ServerLevel level, MonitorBlockEntity monitor) {
        clear(level, monitor, true);
    }

    private static void clear(ServerLevel level, MonitorBlockEntity monitor, boolean force) {
        MonitorBlockEntity controller = monitor.getController();
        String sourceId = controller.getSelectedRwrSource();
        if (sourceId == null && !force) {
            return;
        }

        BlockPos rwrPos = controller.getSelectedRwrPos();
        if (rwrPos == null) {
            rwrPos = ARADData.get(level).getRwrForMonitor(level.dimension(), controller.getControllerPos());
        }
        controller.clearRwrSelectionState();
        if (rwrPos != null) {
            NeoForge.EVENT_BUS.post(new ARADTargetDesignationEvent(
                    ARADTargetDesignationEvent.Action.CLEAR,
                    level,
                    rwrPos,
                    controller.getControllerPos(),
                    sourceId,
                    null
            ));
        }
        controller.setChanged();
        controller.sendData();
    }

}
