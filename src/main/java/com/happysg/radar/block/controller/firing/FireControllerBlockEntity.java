package com.happysg.radar.block.controller.firing;


import com.happysg.radar.block.behavior.networks.NetworkContext;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.List;

public class FireControllerBlockEntity extends SmartBlockEntity  {
    private BlockPos lastKnownPos = BlockPos.ZERO;

    private static final Logger LOGGER = LogUtils.getLogger();
    boolean powered = false;

    // server-time when we last got a target update
    private long lastCommandTick = -1;
    private static final long FAILSAFE_TICKS = 10;

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
        // i hard fail-safe: if nobody has told me to keep firing recently, i turn off
        if (isPowered() && lastCommandTick >= 0 && (level.getGameTime() - lastCommandTick) > FAILSAFE_TICKS) {
            setPowered(false);
        }
        if (!level.isClientSide && level.getGameTime() % 40 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                if (lastKnownPos.equals(worldPosition))
                    return;

                ResourceKey<Level> dim = serverLevel.dimension();
                WeaponNetworkData data = WeaponNetworkData.get(serverLevel);
                boolean updated = data.updateWeaponEndpointPosition(
                        dim,
                        lastKnownPos,
                        worldPosition
                );

                // only commit the new position if the network accepted it
                if (updated) {
                    lastKnownPos = worldPosition;
                    LOGGER.debug("Controller moved {} -> {}", lastKnownPos, worldPosition);
                    setChanged();
                }
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
        tag.putLong("LastKnownPos", lastKnownPos.asLong());
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
        if (tag.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(tag.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if(level instanceof ServerLevel serverLevel) {
            ResourceKey<Level> dim = serverLevel.dimension();
            WeaponNetworkData data = WeaponNetworkData.get(serverLevel);
            data.updateWeaponEndpointPosition(dim,lastKnownPos,worldPosition);
        }
        powered = false;
        if (level != null && !level.isClientSide) {
            setPowered(false);
        }
    }




}

