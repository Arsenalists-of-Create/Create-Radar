package com.happysg.radar.block.arad.aradnetworks;

import com.happysg.radar.block.arad.jammer.JammerBlock;
import com.happysg.radar.block.arad.jammer.JammerBlockEntity;
import com.happysg.radar.block.arad.jammer.RadarJamRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

public class JamRegistry {

    private static final Set<BlockPos> SPOOFERS = new HashSet<>();

    public static void register(BlockPos pos) {
        SPOOFERS.add(pos);
    }

    public static void unregister(BlockPos pos) {
        SPOOFERS.remove(pos);
    }

    public static boolean isRadarSpoofed(ServerLevel level, BlockPos radarPos) {
        for (BlockPos spooferPos : SPOOFERS) {
            BlockEntity be = level.getBlockEntity(spooferPos);
            if (be instanceof JammerBlockEntity spoofer &&
                    spoofer.affects(radarPos)) {
                return true;
            }
        }
        return false;
    }
}
