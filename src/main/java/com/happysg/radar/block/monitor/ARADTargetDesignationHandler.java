package com.happysg.radar.block.monitor;

import com.happysg.radar.api.arad.ARADTargetDesignationEvent;
import com.happysg.radar.api.arad.ARADTargeting;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.compat.Mods;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
        ARADTargetDesignationEvent.Target target;
        BlockPos emitterPos;
        if (contact != null) {
            target = ARADTargeting.createNoisyTarget(level, contact, level.getRandom());
            emitterPos = contact.radarPos();
        } else {
            ExternalRwrEmitterRegistry.EmitterState external = ExternalRwrEmitterRegistry
                    .resolveSelectable(level, sourceId)
                    .orElse(null);
            if (external == null || external.selectionMetadata() == null) {
                return false;
            }
            Vec3 rangeOrigin = external.targetShipId() == null
                    ? receiver.worldPosition()
                    : ARADTargeting.sableReceiver(level, external.targetShipId())
                    .map(ARADTargeting.Receiver::worldPosition)
                    .orElse(receiver.worldPosition());
            double rangeRatio = external.position().distanceTo(rangeOrigin) / external.range();
            if (!Double.isFinite(rangeRatio)) {
                return false;
            }
            emitterPos = BlockPos.containing(external.position());
            target = new ARADTargetDesignationEvent.Target(
                    emitterPos,
                    external.selectionMetadata().emitterId(),
                    Math.max(0.0D, Math.min(ARADTargeting.MAX_PASSIVE_RANGE_RATIO, rangeRatio)),
                    external.position(),
                    null,
                    null
            );
        }
        if (target == null) {
            return false;
        }
        controller.setRwrSelectionState(sourceId, emitterPos, rwrPos);
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
                && (ARADTargeting.resolveNativeRadar(level, sourceId)
                .filter(radar -> radar.getWorldPos().equals(selectedRadarPos))
                .isPresent()
                || ExternalRwrEmitterRegistry.resolveSelectable(level, sourceId).isPresent()
                && rwr.getRadarContacts(level).stream()
                .anyMatch(contact -> sourceId.equals(contact.sourceId())));
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
