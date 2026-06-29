package com.happysg.radar.block.controller.firing;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FireControllerBlockEntity extends SmartBlockEntity {
    boolean powered = false;

    // server-time when we last got a target update
    private long lastCommandTick = -1;
    private static final long FAILSAFE_TICKS = 10;

    // i use these to run a pulse train when a repeater is on top
    private boolean pulsing = false;
    private long nextPulseTick = -1;
    private long pulseOffTick = -1;

    public FireControllerBlockEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        long now = level.getGameTime();

        // i hard fail-safe: if nobody has told me to keep firing recently, i turn off
        if ((isPowered() || pulsing) && lastCommandTick >= 0 && (now - lastCommandTick) > FAILSAFE_TICKS) {
            setPowered(false);
            return;
        }

        // i run pulse train timing
        if (pulsing) {
            // i turn off after a 1-tick "on" window
            if (pulseOffTick >= 0 && now >= pulseOffTick) {
                pulseOffTick = -1;
                setPoweredInternal(false);
            }

            // i start the next pulse when scheduled
            if (nextPulseTick >= 0 && now >= nextPulseTick) {
                startPulseNow(now);
            }
        }

    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        if (level == null || level.isClientSide)
            return;

        // i treat every call as a command input, and remember when it happened
        lastCommandTick = level.getGameTime();

        if (powered && hasRepeaterAbove()) {
            // i enter pulsing mode; pulses keep coming as long as i'm being commanded (failsafe handles dropout)
            pulsing = true;

            long now = level.getGameTime();
            if (nextPulseTick < 0 || now >= nextPulseTick) {
                // i fire an immediate pulse when first commanded (or if we got desynced)
                startPulseNow(now);
            }

            return;
        }

        // i stop pulsing if i'm being set normally/off
        pulsing = false;
        nextPulseTick = -1;
        pulseOffTick = -1;

        setPoweredInternal(powered);
    }

    private void startPulseNow(long now) {
        // i generate a 1-tick pulse
        setPoweredInternal(true);
        pulseOffTick = now + 1;

        int period = getRepeaterPeriodTicks();
        nextPulseTick = now + period;
    }

    private boolean hasRepeaterAbove() {
        if (level == null) return false;
        return level.getBlockState(worldPosition.above()).is(Blocks.REPEATER);
    }

    private int getRepeaterPeriodTicks() {
        if (level == null) return 2;

        BlockState above = level.getBlockState(worldPosition.above());
        if (!above.is(Blocks.REPEATER)) {
            return 2;
        }

        // i map repeater DELAY (1..4) to game ticks (2..8)
        int delaySteps = above.getValue(RepeaterBlock.DELAY); // 1..4
        return Math.max(1, delaySteps) * 2;
    }

    private void setPoweredInternal(boolean powered) {
        if (level == null || level.isClientSide)
            return;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FireControllerBlock))
            return;

        if (state.getValue(FireControllerBlock.POWERED) == powered)
            return;

        this.powered = powered;

        level.setBlock(worldPosition, state.setValue(FireControllerBlock.POWERED, powered), 3);

        level.updateNeighborsAt(worldPosition, state.getBlock());
        for (Direction d : Direction.values())
            level.updateNeighborsAt(worldPosition.relative(d), state.getBlock());

        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());

        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Powered", powered);

        tag.putBoolean("Pulsing", pulsing);
        tag.putLong("NextPulseTick", nextPulseTick);
        tag.putLong("PulseOffTick", pulseOffTick);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        powered = tag.getBoolean("Powered");
        if (level != null) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof FireControllerBlock) {
                powered = state.getValue(FireControllerBlock.POWERED);
            }
        }

        pulsing = tag.getBoolean("Pulsing");
        nextPulseTick = tag.contains("NextPulseTick", Tag.TAG_LONG) ? tag.getLong("NextPulseTick") : -1;
        pulseOffTick = tag.contains("PulseOffTick", Tag.TAG_LONG) ? tag.getLong("PulseOffTick") : -1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        powered = false;
        pulsing = false;
        nextPulseTick = -1;
        pulseOffTick = -1;

        if (level != null && !level.isClientSide) {
            setPoweredInternal(false);
        }
    }
}
