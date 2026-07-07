package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.packets.RwrEngagedSoundPacket;
import com.happysg.radar.networking.packets.RwrLockSoundPacket;
import com.happysg.radar.registry.ModSounds;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock.ON_SHIP;

public class RadarWarningReceiverBlockEntity extends SmartBlockEntity {
    boolean hasPlayed;
    private static final int LOCK_SOUND_UPDATE_PERIOD_TICKS = 10;
    private static final int ENGAGED_SOUND_UPDATE_PERIOD_TICKS = 10;
    private static final int SWEEP_SOUND_PERIOD_TICKS = 20;
    private static final int SWEEP_START_DELAY_TICKS = 80;
    private static final double LOOP_SOUND_PACKET_RANGE = 64.0;

    private int sweepStartDelayTicks = 0;
    private int lockSoundUpdateTicks = 0;
    private int engagedSoundUpdateTicks = 0;
    private int sweepSoundTicks = 0;
    private int engagedPulseTicks = 0;
    private int redstoneSignal = 0;
    private boolean lockSoundActive = false;
    private boolean engagedSoundActive = false;
    private boolean engagedRedstoneActive = false;
    private boolean engagedPulseHigh = true;

    private boolean wasInRange = false;
    private final Set<String> knownInRangeSources = new HashSet<>();

    public RadarWarningReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void remove() {
        if (level instanceof ServerLevel sl) {
            stopLockSound(sl);
            stopEngagedSound(sl);
            setRedstoneSignal(0);
            ARADData.get(sl).dissolveRwr(sl, worldPosition);
        }
        super.remove();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            refreshOnShip(level, worldPosition);
        }

        if (!(level instanceof ServerLevel sl)) return;
        if (level.getGameTime() % 5 == 0) {
            reconcileAradContacts(sl);
        }
        if (!getBlockState().getValue(ON_SHIP)) {
            resetSoundState();
            return;
        }

        if (sweepStartDelayTicks > 0) sweepStartDelayTicks--;
        if (lockSoundUpdateTicks > 0) lockSoundUpdateTicks--;
        if (engagedSoundUpdateTicks > 0) engagedSoundUpdateTicks--;
        if (sweepSoundTicks > 0) sweepSoundTicks--;

        // ship = VSGameUtilsKt.getShipManagingPos(level, worldPosition);\
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (ship == null) {
            resetSoundState();
            return;
        }

        UUID key = ship.getUniqueId();
        if(!Mods.SABLE.isLoaded()) {
            resetSoundState();
            return;
        }
        boolean engaged = RadarContactRegistry.isEngaged(sl, key);
        boolean locked = RadarContactRegistry.isLocked(sl, key);
        //if (locked) LogUtils.getLogger().warn("locked");
        Set<String> inRangeSources = RadarContactRegistry.getInRangeSources(sl, key);
        boolean inRange = !inRangeSources.isEmpty();
        if (inRange) {
            playSpottedSoundForNewSources(sl, inRangeSources);
        } else {
            hasPlayed = false;
            knownInRangeSources.clear();
            sweepStartDelayTicks = 0;
            sweepSoundTicks = 0;
        }

        if (engaged) {
            wasInRange = inRange;
            sweepSoundTicks = 0;
            stopLockSound(sl);
            updateEngagedRedstoneSignal();

            if (!engagedSoundActive || engagedSoundUpdateTicks == 0) {
                sendEngagedSoundState(sl, true);
                engagedSoundActive = true;
                engagedSoundUpdateTicks = ENGAGED_SOUND_UPDATE_PERIOD_TICKS;
            }

            return;
        }

        resetEngagedRedstonePulse();
        stopEngagedSound(sl);

        // locked wins over sweep, but spotted has already been allowed above
        if (locked) {
            wasInRange = inRange;
            sweepSoundTicks = 0;
            setRedstoneSignal(15);

            if (!lockSoundActive || lockSoundUpdateTicks == 0) {
                sendLockSoundState(sl, true);
                lockSoundActive = true;
                lockSoundUpdateTicks = LOCK_SOUND_UPDATE_PERIOD_TICKS;
            }

            return;
        }

        stopLockSound(sl);

        if (inRange) {
            setRedstoneSignal(RadarContactRegistry.getInRangeSignal(sl, key));
            playSweepSound(sl);
        } else {
            setRedstoneSignal(0);
        }

        wasInRange = inRange;
    }

    private void resetSoundState() {
        sweepStartDelayTicks = 0;
        if (level instanceof ServerLevel sl) {
            stopLockSound(sl);
            stopEngagedSound(sl);
        }
        lockSoundUpdateTicks = 0;
        engagedSoundUpdateTicks = 0;
        sweepSoundTicks = 0;
        resetEngagedRedstonePulse();
        setRedstoneSignal(0);
        wasInRange = false;
        hasPlayed = false;
        knownInRangeSources.clear();
    }

    public int getRedstoneSignal() {
        return redstoneSignal;
    }

    private void setRedstoneSignal(int signal) {
        signal = Math.max(0, Math.min(15, signal));
        if (redstoneSignal == signal) {
            return;
        }

        redstoneSignal = signal;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void updateEngagedRedstoneSignal() {
        if (!engagedRedstoneActive) {
            engagedRedstoneActive = true;
            engagedPulseHigh = true;
            engagedPulseTicks = 10;
        } else {
            engagedPulseTicks--;
            if (engagedPulseTicks <= 0) {
                engagedPulseHigh = !engagedPulseHigh;
                engagedPulseTicks = 10;
            }
        }

        setRedstoneSignal(engagedPulseHigh ? 15 : 0);
    }

    private void resetEngagedRedstonePulse() {
        engagedRedstoneActive = false;
        engagedPulseHigh = true;
        engagedPulseTicks = 0;
    }

    private void playSpottedSoundForNewSources(ServerLevel level, Set<String> inRangeSources) {
        boolean newSource = !knownInRangeSources.containsAll(inRangeSources);

        if (!hasPlayed || newSource) {
            level.playSound(
                    null,
                    SableUtils.getWorldPos(this),
                    ModSounds.RWR_IN_RANGE.get(),
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f
            );
            hasPlayed = true;
            sweepStartDelayTicks = SWEEP_START_DELAY_TICKS;
            sweepSoundTicks = 0;
        }

        knownInRangeSources.clear();
        knownInRangeSources.addAll(inRangeSources);
    }

    private void playSweepSound(ServerLevel level) {
        if (sweepStartDelayTicks > 0) return;
        if (sweepSoundTicks > 0) return;

        level.playSound(
                null,
                SableUtils.getWorldPos(this),
                ModSounds.RWR_SWEEP.get(),
                SoundSource.BLOCKS,
                1.0f,
                1.0f
        );
        sweepSoundTicks = SWEEP_SOUND_PERIOD_TICKS;
    }

    private void stopLockSound(ServerLevel level) {
        if (!lockSoundActive) return;

        sendLockSoundState(level, false);
        lockSoundActive = false;
        lockSoundUpdateTicks = 0;
    }

    private void stopEngagedSound(ServerLevel level) {
        if (!engagedSoundActive) return;

        sendEngagedSoundState(level, false);
        engagedSoundActive = false;
        engagedSoundUpdateTicks = 0;
    }

    private void sendLockSoundState(ServerLevel level, boolean locked) {
        Vec3 soundPos = Vec3.atCenterOf(SableUtils.getWorldPos(this));
        RwrLockSoundPacket packet = new RwrLockSoundPacket(
                worldPosition,
                locked,
                soundPos.x,
                soundPos.y,
                soundPos.z
        );

        if (!locked) {
            NetworkHandler.sendToClients(packet);
            return;
        }

        double rangeSqr = LOOP_SOUND_PACKET_RANGE * LOOP_SOUND_PACKET_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(soundPos) <= rangeSqr) {
                NetworkHandler.sendToPlayer(player, packet);
            }
        }
    }

    private void sendEngagedSoundState(ServerLevel level, boolean engaged) {
        Vec3 soundPos = Vec3.atCenterOf(SableUtils.getWorldPos(this));
        RwrEngagedSoundPacket packet = new RwrEngagedSoundPacket(
                worldPosition,
                engaged,
                soundPos.x,
                soundPos.y,
                soundPos.z
        );

        if (!engaged) {
            NetworkHandler.sendToClients(packet);
            return;
        }

        double rangeSqr = LOOP_SOUND_PACKET_RANGE * LOOP_SOUND_PACKET_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(soundPos) <= rangeSqr) {
                NetworkHandler.sendToPlayer(player, packet);
            }
        }
    }

    private static boolean computeOnShip(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos) != null;
    }

    private static void refreshOnShip(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(ON_SHIP)) return;

        boolean onShip = computeOnShip(level, pos);
        if (state.getValue(ON_SHIP) != onShip) {
            level.setBlock(pos, state.setValue(ON_SHIP, onShip), 3);
        }
    }

    private void reconcileAradContacts(ServerLevel level) {
        ARADData data = ARADData.get(level);
        ARADData.Group group = data.getGroup(level.dimension(), worldPosition);
        if (group == null && !hasAdjacentMonitor(level)) {
            return;
        }
        data.reconcileContactLinks(level, data.getOrCreateGroup(level.dimension(), worldPosition));
    }

    private boolean hasAdjacentMonitor(ServerLevel level) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof MonitorBlockEntity) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        refreshOnShip(level, worldPosition);

        setLazyTickRate(10);
    }


}
