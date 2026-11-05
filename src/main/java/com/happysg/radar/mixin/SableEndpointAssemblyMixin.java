package com.happysg.radar.mixin;

import com.happysg.radar.block.arad.jammer.JammerBlock;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlock;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlock;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlock;
import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({
        StationaryRadarBlock.class,
        RadarBearingBlock.class,
        SkyRadarBlock.class,
        SonarBearingBlock.class,
        RadarWarningReceiverBlock.class,
        JammerBlock.class,
        AutoPitchControllerBlock.class,
        AutoYawControllerBlock.class,
        FireControllerBlock.class
})
public abstract class SableEndpointAssemblyMixin
        implements BlockSubLevelAssemblyListener {

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState oldState, BlockPos oldPos, BlockPos newPos) {
        SableDataLinkRelocation.prepareEndpointMove(originLevel, oldPos);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        SableDataLinkRelocation.recordEndpointMove(originLevel, resultingLevel, oldPos, newPos);
    }
}