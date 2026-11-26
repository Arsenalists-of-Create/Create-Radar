package com.happysg.radar.block.controller.firing;

import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FireControllerBlockEntity extends SmartBlockEntity {
    boolean powered = false;

    private long lastCommandTick = -1;
    private static final long FAILSAFE_TICKS = 10;

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

        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.get(serverLevel).advertiseContactController(this);
        }

        long now = level.getGameTime();

        if ((isPowered() || pulsing) && lastCommandTick >= 0 && (now - lastCommandTick) > FAILSAFE_TICKS) {
            setPowered(false);
            return;
        }

        if (pulsing) {
            if (pulseOffTick >= 0 && now >= pulseOffTick) {
                pulseOffTick = -1;
                setPoweredInternal(false);
            }

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

        lastCommandTick = level.getGameTime();

        if (powered && hasRepeaterAbove()) {
            pulsing = true;

            long now = level.getGameTime();
            if (nextPulseTick < 0 || now >= nextPulseTick) {
                startPulseNow(now);
            }

            return;
        }

        pulsing = false;
        nextPulseTick = -1;
        pulseOffTick = -1;

        setPoweredInternal(powered);
    }

    private void startPulseNow(long now) {
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

        int delaySteps = above.getValue(RepeaterBlock.DELAY);
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
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("Powered", powered);

        tag.putBoolean("Pulsing", pulsing);
        tag.putLong("NextPulseTick", nextPulseTick);
        tag.putLong("PulseOffTick", pulseOffTick);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

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

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.peek(serverLevel);
            if (runtime != null) {
                runtime.unregisterContactController(worldPosition);
            }
        }
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.peek(serverLevel);
            if (runtime != null) {
                runtime.unregisterContactController(worldPosition);
            }
        }
        super.remove();
    }
}
