package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.IdentificationConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.packets.RwrEngagedSoundPacket;
import com.happysg.radar.networking.packets.RwrLockSoundPacket;
import com.happysg.radar.registry.ModSounds;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlock.ON_SHIP;

public class RadarWarningReceiverBlockEntity extends SmartBlockEntity {
    boolean hasPlayed;
    private static final int LOCK_SOUND_UPDATE_PERIOD_TICKS = 10;
    private static final int ENGAGED_SOUND_UPDATE_PERIOD_TICKS = 10;
    private static final int SWEEP_START_DELAY_TICKS = 20;
    private static final int EXACT_LOCK_HOLD_TICKS = 20;
    private static final double LOOP_SOUND_PACKET_RANGE = 64.0;

    private int sweepStartDelayTicks = 0;
    private int lockSoundUpdateTicks = 0;
    private int engagedSoundUpdateTicks = 0;
    private int engagedPulseTicks = 0;
    private int redstoneSignal = 0;
    private boolean lockSoundActive = false;
    private boolean engagedSoundActive = false;
    private boolean engagedRedstoneActive = false;
    private boolean engagedPulseHigh = true;

    private boolean wasInRange = false;
    private final Set<String> knownInRangeSources = new HashSet<>();
    private final Map<String, Float> lastSweepBeamAngles = new HashMap<>();
    private final Map<String, Integer> exactLockHoldTicks = new HashMap<>();

    private record ReceiverTarget(UUID shipId, RwrTargetReference reference, Vec3 position) {
    }

    private record AggregatedRwrContact(RwrRadarContact contact, List<Vec3> receiverPositions) {
    }

    private static final class ContactAccumulator {
        private final String sourceId;
        private final BlockPos radarPos;
        private final RadarType radarType;
        private final float bearingDegrees;
        private final List<Vec3> receiverPositions = new ArrayList<>();
        private float signalStrength = 0.0F;
        private boolean lockCapable = false;
        private boolean withinRadarRange = false;
        private boolean exactLocked = false;
        private boolean engaged = false;
        private boolean friendly = false;

        private ContactAccumulator(String sourceId, IRadar radar, Vec3 displayReceiverPos, Vec3 radarWorldPos) {
            this(sourceId, radar.getWorldPos(), radar.getRadarTypeEnum(),
                    bearingDegrees(displayReceiverPos, radarWorldPos));
        }

        private ContactAccumulator(String sourceId, BlockPos radarPos, RadarType radarType, float bearingDegrees) {
            this.sourceId = sourceId;
            this.radarPos = radarPos.immutable();
            this.radarType = radarType;
            this.bearingDegrees = bearingDegrees;
        }

        private void accept(RwrContactEvaluation evaluation, boolean withinRadarRange, boolean engaged,
                            boolean friendly, Vec3 receiverPosition) {
            signalStrength = Math.max(signalStrength, evaluation.signalStrength());
            lockCapable |= evaluation.lockCapable();
            this.withinRadarRange |= withinRadarRange;
            exactLocked |= evaluation.lockedOnExactTarget();
            this.engaged |= engaged;
            this.friendly |= friendly;
            receiverPositions.add(receiverPosition);
        }

        private AggregatedRwrContact toContact() {
            return new AggregatedRwrContact(
                    new RwrRadarContact(
                            sourceId,
                            radarPos,
                            radarType,
                            bearingDegrees,
                            signalStrength,
                            lockCapable,
                            withinRadarRange,
                            exactLocked,
                            engaged,
                            friendly
                    ),
                    List.copyOf(receiverPositions)
            );
        }
    }

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

        if (!(level instanceof ServerLevel sl)) return;
        if (level.getGameTime() % 5 == 0) {
            reconcileAradContacts(sl);
        }

        if (sweepStartDelayTicks > 0) sweepStartDelayTicks--;
        if (lockSoundUpdateTicks > 0) lockSoundUpdateTicks--;
        if (engagedSoundUpdateTicks > 0) engagedSoundUpdateTicks--;

        // ship = VSGameUtilsKt.getShipManagingPos(level, worldPosition);\
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (ship == null) {
            resetSoundState();
            return;
        }

        if(!Mods.SABLE.isLoaded()) {
            resetSoundState();
            return;
        }
        List<ReceiverTarget> receiverChain = resolveReceiverChain(sl, ship);
        if (receiverChain.isEmpty()) {
            resetSoundState();
            return;
        }

        List<AggregatedRwrContact> contacts = stabilizeExactLocks(buildAggregatedContacts(sl, receiverChain), true);
        List<AggregatedRwrContact> alertContacts = alertContacts(contacts);
        Set<String> alertSources = sourceIds(alertContacts);
        boolean inRange = !alertContacts.isEmpty();
        boolean engaged = inRange && (isAnyEngaged(sl, receiverChain) || hasEngagedContact(alertContacts));
        boolean locked = inRange && (isAnyLocked(sl, receiverChain) || hasExactLockedContact(alertContacts));
        //if (locked) LogUtils.getLogger().warn("locked");
        if (inRange) {
            playSpottedSoundForNewSources(sl, alertSources);
        } else {
            hasPlayed = false;
            knownInRangeSources.clear();
            sweepStartDelayTicks = 0;
            lastSweepBeamAngles.clear();
        }

        if (engaged) {
            wasInRange = inRange;
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
            setRedstoneSignal(getAlertSignal(alertContacts));
            playSweepSound(sl, alertContacts);
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
        lastSweepBeamAngles.clear();
        exactLockHoldTicks.clear();
        resetEngagedRedstonePulse();
        setRedstoneSignal(0);
        wasInRange = false;
        hasPlayed = false;
        knownInRangeSources.clear();
    }

    public int getRedstoneSignal() {
        return redstoneSignal;
    }

    public List<RwrRadarContact> getRadarContacts(ServerLevel level) {
        if (!Mods.SABLE.isLoaded()) {
            return List.of();
        }

        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (ship == null) {
            return List.of();
        }

        List<ReceiverTarget> receiverChain = resolveReceiverChain(level, ship);
        if (receiverChain.isEmpty()) {
            return List.of();
        }

        List<RwrRadarContact> contacts = new ArrayList<>();
        for (AggregatedRwrContact aggregated : stabilizeExactLocks(buildAggregatedContacts(level, receiverChain), false)) {
            contacts.add(aggregated.contact());
        }
        return List.copyOf(contacts);
    }

    private List<ReceiverTarget> resolveReceiverChain(ServerLevel level, SubLevelAccess containingShip) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of(receiverTarget(containingShip));
        }

        SubLevel source = container.getSubLevel(containingShip.getUniqueId());
        if (source == null) {
            return List.of(receiverTarget(containingShip));
        }

        List<ReceiverTarget> targets = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        targets.add(receiverTarget(containingShip));
        seen.add(containingShip.getUniqueId());
        for (SubLevel subLevel : SubLevelHelper.getConnectedChain(source)) {
            if (subLevel == null || !seen.add(subLevel.getUniqueId())) {
                continue;
            }
            targets.add(receiverTarget(subLevel));
        }

        if (targets.isEmpty()) {
            targets.add(receiverTarget(containingShip));
        }
        return List.copyOf(targets);
    }

    private static ReceiverTarget receiverTarget(SubLevelAccess subLevel) {
        UUID shipId = subLevel.getUniqueId();
        return new ReceiverTarget(shipId, RwrTargetReference.sableShip(shipId), RadarTrackUtil.getPosition(subLevel));
    }

    private List<AggregatedRwrContact> buildAggregatedContacts(ServerLevel level, List<ReceiverTarget> receivers) {
        if (receivers.isEmpty()) {
            return List.of();
        }

        Vec3 displayReceiverPos = receivers.get(0).position();
        Set<String> receiverSecrets = receiverSecrets(receivers);
        Map<String, ContactAccumulator> contactsBySource = new LinkedHashMap<>();

        for (ReceiverTarget receiver : receivers) {
            for (String sourceId : RadarContactRegistry.getInRangeSources(level, receiver.shipId())) {
                Optional<BlockPos> radarPos = parseRadarSource(level, sourceId);
                if (radarPos.isEmpty()) {
                    RadarContactRegistry.removeInRangeSource(level, receiver.shipId(), sourceId);
                    continue;
                }

                BlockEntity be = level.getBlockEntity(radarPos.get());
                if (!(be instanceof IRadar radar) || !radar.isRunning()) {
                    RadarContactRegistry.removeInRangeSource(level, receiver.shipId(), sourceId);
                    continue;
                }

                RwrContactEvaluation evaluation = radar.evaluateRwrContact(level, receiver.reference(), receiver.reference());
                if (!evaluation.emitting() || !evaluation.detectableByReceiver()) {
                    RadarContactRegistry.removeInRangeSource(level, receiver.shipId(), sourceId);
                    continue;
                }

                Vec3 radarWorldPos = PhysicsHandler.getWorldVec(level, radar.getWorldPos());
                boolean withinRadarRange = horizontalDistanceSqr(receiver.position(), radarWorldPos) <= radar.getRange() * radar.getRange();
                boolean engaged = RadarContactRegistry.isSourceEngaged(level, receiver.shipId(), sourceId);
                boolean friendly = isFriendlyRadar(level, radarPos.get(), receiverSecrets);
                contactsBySource
                        .computeIfAbsent(sourceId, ignored -> new ContactAccumulator(sourceId, radar, displayReceiverPos, radarWorldPos))
                        .accept(evaluation, withinRadarRange, engaged, friendly, receiver.position());
            }

            for (RwrRadarContact contact : ExternalRwrEmitterRegistry.contactsFor(
                    level, receiver.shipId(), receiver.reference(), displayReceiverPos)) {
                RwrContactEvaluation evaluation = new RwrContactEvaluation(
                        true,
                        true,
                        contact.lockCapable(),
                        contact.exactLocked(),
                        contact.signalStrength()
                );
                contactsBySource
                        .computeIfAbsent(contact.sourceId(), ignored -> new ContactAccumulator(
                                contact.sourceId(), contact.radarPos(), contact.radarType(), contact.bearingDegrees()))
                        .accept(evaluation, contact.withinRadarRange(), contact.engaged(),
                                contact.friendly(), receiver.position());
            }
        }

        List<AggregatedRwrContact> contacts = new ArrayList<>();
        for (ContactAccumulator accumulator : contactsBySource.values()) {
            contacts.add(accumulator.toContact());
        }
        return List.copyOf(contacts);
    }

    private static List<AggregatedRwrContact> alertContacts(List<AggregatedRwrContact> contacts) {
        List<AggregatedRwrContact> alertContacts = new ArrayList<>();
        for (AggregatedRwrContact contact : contacts) {
            if (!contact.contact().friendly() || contact.contact().exactLocked()) {
                alertContacts.add(contact);
            }
        }
        return List.copyOf(alertContacts);
    }

    private List<AggregatedRwrContact> stabilizeExactLocks(List<AggregatedRwrContact> contacts, boolean advanceTicks) {
        Set<String> liveSources = new HashSet<>();
        List<AggregatedRwrContact> stabilized = new ArrayList<>(contacts.size());

        for (AggregatedRwrContact contact : contacts) {
            String sourceId = contact.contact().sourceId();
            liveSources.add(sourceId);

            boolean exactLocked = contact.contact().exactLocked();
            boolean externalSource = level instanceof ServerLevel serverLevel
                    && ExternalRwrEmitterRegistry.isActiveSource(serverLevel, sourceId);
            if (exactLocked) {
                exactLockHoldTicks.put(sourceId, EXACT_LOCK_HOLD_TICKS);
            } else if (externalSource) {
                exactLockHoldTicks.remove(sourceId);
            } else {
                int holdTicks = exactLockHoldTicks.getOrDefault(sourceId, 0);
                if (holdTicks > 0) {
                    exactLocked = true;
                    if (advanceTicks) {
                        holdTicks = Math.max(0, holdTicks - 1);
                        if (holdTicks > 0) {
                            exactLockHoldTicks.put(sourceId, holdTicks);
                        } else {
                            exactLockHoldTicks.remove(sourceId);
                        }
                    }
                }
            }

            stabilized.add(withExactLocked(contact, exactLocked));
        }

        if (advanceTicks) {
            exactLockHoldTicks.keySet().removeIf(source -> !liveSources.contains(source));
        }
        return List.copyOf(stabilized);
    }

    private static AggregatedRwrContact withExactLocked(AggregatedRwrContact aggregated, boolean exactLocked) {
        RwrRadarContact contact = aggregated.contact();
        if (contact.exactLocked() == exactLocked) {
            return aggregated;
        }

        return new AggregatedRwrContact(
                new RwrRadarContact(
                        contact.sourceId(),
                        contact.radarPos(),
                        contact.radarType(),
                        contact.bearingDegrees(),
                        contact.signalStrength(),
                        contact.lockCapable(),
                        contact.withinRadarRange(),
                        exactLocked,
                        contact.engaged(),
                        contact.friendly()
                ),
                aggregated.receiverPositions()
        );
    }

    private static Set<String> sourceIds(List<AggregatedRwrContact> contacts) {
        Set<String> sourceIds = new HashSet<>();
        for (AggregatedRwrContact contact : contacts) {
            sourceIds.add(contact.contact().sourceId());
        }
        return sourceIds;
    }

    private static int getAlertSignal(List<AggregatedRwrContact> contacts) {
        int strongest = 0;
        for (AggregatedRwrContact contact : contacts) {
            strongest = Math.max(strongest, Math.max(0, Math.min(14, (int) Math.ceil(contact.contact().signalStrength()))));
        }
        return strongest;
    }

    private static boolean hasExactLockedContact(List<AggregatedRwrContact> contacts) {
        for (AggregatedRwrContact contact : contacts) {
            if (contact.contact().exactLocked()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEngagedContact(List<AggregatedRwrContact> contacts) {
        for (AggregatedRwrContact contact : contacts) {
            if (contact.contact().engaged()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnyLocked(ServerLevel level, List<ReceiverTarget> receivers) {
        for (ReceiverTarget receiver : receivers) {
            if (RadarContactRegistry.isLocked(level, receiver.shipId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnyEngaged(ServerLevel level, List<ReceiverTarget> receivers) {
        for (ReceiverTarget receiver : receivers) {
            if (RadarContactRegistry.isEngaged(level, receiver.shipId())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> receiverSecrets(List<ReceiverTarget> receivers) {
        Set<String> secrets = new HashSet<>();
        for (ReceiverTarget receiver : receivers) {
            String secret = normalizeSecret(getReceiverSecret(receiver.shipId()));
            if (!secret.isBlank()) {
                secrets.add(secret);
            }
        }
        return secrets;
    }

    private static Optional<BlockPos> parseRadarSource(ServerLevel level, String sourceId) {
        int separator = sourceId == null ? -1 : sourceId.indexOf('|');
        if (separator <= 0 || separator >= sourceId.length() - 1) {
            return Optional.empty();
        }
        if (!level.dimension().location().toString().equals(sourceId.substring(0, separator))) {
            return Optional.empty();
        }

        try {
            return Optional.of(BlockPos.of(Long.parseLong(sourceId.substring(separator + 1))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isFriendlyRadar(ServerLevel level, BlockPos radarPos, Set<String> receiverSecrets) {
        if (receiverSecrets.isEmpty()) {
            return false;
        }

        String radarSecret = normalizeSecret(getRadarNetworkSecret(level, radarPos));
        return !radarSecret.isBlank() && receiverSecrets.contains(radarSecret);
    }

    private static String getReceiverSecret(UUID receiverShipId) {
        IDManager.IDRecord record = IDManager.getIDRecordByShipId(receiverShipId);
        return record == null ? "" : record.secretID();
    }

    private static String getRadarNetworkSecret(ServerLevel level, BlockPos radarPos) {
        NetworkData data = NetworkData.get(level);
        BlockPos filtererPos = data.getFiltererForEndpoint(level.dimension(), radarPos);
        if (filtererPos == null) {
            return "";
        }

        NetworkData.Group group = data.getGroup(level.dimension(), filtererPos);
        if (group == null) {
            return "";
        }

        return IdentificationConfig.fromTag(group.identificationTag).label();
    }

    private static String normalizeSecret(String secret) {
        return secret == null ? "" : secret.trim().toLowerCase(Locale.ROOT);
    }

    private static float bearingDegrees(Vec3 from, Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(to.x() - from.x(), to.z() - from.z()));
        angle %= 360.0;
        if (angle < 0.0) {
            angle += 360.0;
        }
        return (float) angle;
    }

    private static double horizontalDistanceSqr(Vec3 from, Vec3 to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        return dx * dx + dz * dz;
    }

    private void setRedstoneSignal(int signal) {
        signal = Math.max(0, Math.min(15, signal));
        if (redstoneSignal == signal) {
            updateEmittingBlockState(signal > 0);
            return;
        }

        redstoneSignal = signal;
        setChanged();
        if (level != null && !level.isClientSide) {
            updateEmittingBlockState(signal > 0);
            level.updateNeighborsAt(worldPosition, level.getBlockState(worldPosition).getBlock());
        }
    }

    private void updateEmittingBlockState(boolean emitting) {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(ON_SHIP) || state.getValue(ON_SHIP) == emitting) {
            return;
        }

        level.setBlock(worldPosition, state.setValue(ON_SHIP, emitting), 3);
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
            Vec3 soundPos = getSoundPos();
            level.playSound(
                    null,
                    soundPos.x,
                    soundPos.y,
                    soundPos.z,
                    ModSounds.RWR_IN_RANGE.get(),
                    SoundSource.BLOCKS,
                    2.0f,
                    1.0f
            );
            hasPlayed = true;
            sweepStartDelayTicks = SWEEP_START_DELAY_TICKS;
            lastSweepBeamAngles.clear();
        }

        knownInRangeSources.clear();
        knownInRangeSources.addAll(inRangeSources);
    }

    private void playSweepSound(ServerLevel level, List<AggregatedRwrContact> alertContacts) {
        boolean shouldPlay = false;
        Set<String> inRangeSources = sourceIds(alertContacts);
        lastSweepBeamAngles.keySet().retainAll(inRangeSources);
        String sweepThreatSource = sweepThreatSource(alertContacts);

        for (AggregatedRwrContact contact : alertContacts) {
            boolean canPlay = sweepStartDelayTicks <= 0 && contact.contact().sourceId().equals(sweepThreatSource);
            shouldPlay |= updateSweepBeam(level, contact, canPlay);
        }

        if (!shouldPlay) return;

        Vec3 soundPos = getSoundPos();
        level.playSound(
                null,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                ModSounds.RWR_SWEEP.get(),
                SoundSource.BLOCKS,
                0.75f,
                1.0f
        );
    }

    private static String sweepThreatSource(List<AggregatedRwrContact> alertContacts) {
        String strongestInRange = null;
        float strongestInRangeSignal = Float.NEGATIVE_INFINITY;
        String strongestAny = null;
        float strongestAnySignal = Float.NEGATIVE_INFINITY;

        for (AggregatedRwrContact aggregated : alertContacts) {
            RwrRadarContact contact = aggregated.contact();
            if (strongestAny == null || contact.signalStrength() > strongestAnySignal) {
                strongestAny = contact.sourceId();
                strongestAnySignal = contact.signalStrength();
            }
            if (contact.withinRadarRange() && (strongestInRange == null || contact.signalStrength() > strongestInRangeSignal)) {
                strongestInRange = contact.sourceId();
                strongestInRangeSignal = contact.signalStrength();
            }
        }

        return strongestInRange != null ? strongestInRange : strongestAny;
    }

    private boolean updateSweepBeam(ServerLevel level, AggregatedRwrContact contact, boolean canPlay) {
        String sourceId = contact.contact().sourceId();
        Optional<BlockPos> radarPos = parseRadarSource(level, sourceId);
        if (radarPos.isEmpty()) {
            lastSweepBeamAngles.remove(sourceId);
            return false;
        }

        BlockEntity be = level.getBlockEntity(radarPos.get());
        if (!(be instanceof IRadar radar) || !radar.isRunning()) {
            lastSweepBeamAngles.remove(sourceId);
            return false;
        }

        float speed = radar.getSweepAngularSpeedDegreesPerTick();
        if (Math.abs(speed) < 0.001f) {
            lastSweepBeamAngles.put(sourceId, wrapDegrees360(radar.getGlobalAngle()));
            return false;
        }

        float currentAngle = wrapDegrees360(radar.getGlobalAngle());
        float previousAngle = lastSweepBeamAngles.getOrDefault(sourceId, wrapDegrees360(currentAngle - speed));
        lastSweepBeamAngles.put(sourceId, currentAngle);

        if (!canPlay) {
            return false;
        }

        Vec3 radarWorldPos = PhysicsHandler.getWorldVec(level, radar.getWorldPos());
        for (Vec3 receiverPos : contact.receiverPositions()) {
            float receiverBearing = bearingDegrees(radarWorldPos, receiverPos);
            if (sweptPast(previousAngle, currentAngle, receiverBearing, speed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sweptPast(float previousAngle, float currentAngle, float targetAngle, float speed) {
        if (Math.abs(speed) >= 360.0f) {
            return true;
        }

        if (speed > 0.0f) {
            float swept = clockwiseDistance(previousAngle, currentAngle);
            float toTarget = clockwiseDistance(previousAngle, targetAngle);
            return toTarget > 0.0f && toTarget <= swept;
        }

        float swept = clockwiseDistance(currentAngle, previousAngle);
        float toTarget = clockwiseDistance(currentAngle, targetAngle);
        return toTarget > 0.0f && toTarget <= swept;
    }

    private static float clockwiseDistance(float from, float to) {
        float distance = wrapDegrees360(to) - wrapDegrees360(from);
        if (distance < 0.0f) {
            distance += 360.0f;
        }
        return distance;
    }

    private static float wrapDegrees360(float angle) {
        angle %= 360.0f;
        if (angle < 0.0f) {
            angle += 360.0f;
        }
        return angle;
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
        Vec3 soundPos = getSoundPos();
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
        Vec3 soundPos = getSoundPos();
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

    private Vec3 getSoundPos() {
        return Vec3.atCenterOf(SableUtils.getWorldPos(this));
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
        if (level != null && !level.isClientSide) {
            updateEmittingBlockState(false);
        }

        setLazyTickRate(10);
    }

}
