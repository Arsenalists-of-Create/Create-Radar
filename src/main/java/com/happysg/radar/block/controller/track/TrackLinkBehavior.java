package com.happysg.radar.block.controller.track;

import com.happysg.radar.block.datalink.DataController;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataLinkContext;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.block.datalink.screens.AbstractDataLinkScreen;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class TrackLinkBehavior extends DataPeripheral {
    @Override
    protected AbstractDataLinkScreen getScreen(DataLinkBlockEntity be) {
        return null;
    }

    public void transferData(DataLinkContext context, @NotNull DataController activeTarget) {

        if (!(context.getSourceBlockEntity() instanceof TrackControllerBlockEntity controller))
            return;

        MonitorBlockEntity monitor = context.getMonitorBlockEntity();
        if (monitor == null)
            return;

        Vec3 targetPos = monitor.getTargetPos(TargetingConfig.DEFAULT);
        controller.setTarget(targetPos);
    }
}
