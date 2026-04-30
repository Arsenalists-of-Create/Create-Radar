package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.registry.ModSounds;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock.ON_SHIP;

public class RadarWarningReceiverBlockEntity extends SmartBlockEntity {
    boolean hasPlayed;
    private static final int LOCK_BEEP_PERIOD_TICKS = 31;

    private int inRangeCooldownTicks = 0;
    private int lockBeepTicks = 0;

    private boolean wasInRange = false;

    public RadarWarningReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            refreshOnShip(level, worldPosition);
        }

        if (!(level instanceof ServerLevel sl)) return;
        if (!getBlockState().getValue(ON_SHIP)) {
            resetSoundState();
            return;
        }

        if (inRangeCooldownTicks > 0) inRangeCooldownTicks--;
        if (lockBeepTicks > 0) lockBeepTicks--;

        Long shipId = VS2Utils.getShipId(level, worldPosition);
        if (shipId == null) {
            resetSoundState();
            return;
        }

        boolean locked = RadarContactRegistry.isLocked(sl, shipId);
        boolean inRange = RadarContactRegistry.isInRange(sl, shipId);

        if (locked) {
            wasInRange = inRange;
            if (lockBeepTicks == 0) {
                Vec3 p = VS2Utils.getWorldVec(this);
                sl.playSound(null, p.x, p.y, p.z, ModSounds.RWR_LOCK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                lockBeepTicks = LOCK_BEEP_PERIOD_TICKS;
            }
            return;
        }

        lockBeepTicks = 0;
        if (inRange && !hasPlayed) {
            boolean firstSpotted = !wasInRange;
            if (firstSpotted || inRangeCooldownTicks == 0) {
                Vec3 p = VS2Utils.getWorldVec(this);
                sl.playSound(null, p.x, p.y, p.z, ModSounds.RWR_IN_RANGE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                hasPlayed = true;
            }
        } else {
            inRangeCooldownTicks = 0;
        }
        wasInRange = inRange;
    }

    private void resetSoundState() {
        inRangeCooldownTicks = 0;
        lockBeepTicks = 0;
        wasInRange = false;
    }

    private static boolean computeOnShip(net.minecraft.world.level.Level level, BlockPos pos) {
        return VS2Utils.isBlockInShipyard(level, pos);
    }

    private static void refreshOnShip(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(ON_SHIP)) return;

        boolean onShip = computeOnShip(level, pos);
        if (state.getValue(ON_SHIP) != onShip) {
            level.setBlock(pos, state.setValue(ON_SHIP, onShip), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        refreshOnShip(level, worldPosition);
        setLazyTickRate(10);
    }
}