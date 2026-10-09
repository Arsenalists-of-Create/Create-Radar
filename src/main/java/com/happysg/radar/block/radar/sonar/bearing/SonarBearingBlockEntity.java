package com.happysg.radar.block.radar.sonar.bearing;

import com.happysg.radar.api.radar.RadarDetectionConfigurable;
import com.happysg.radar.api.radar.RadarDetectionSettings;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrContactEvaluation;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.SonarScanningBlockBehavior;
import com.happysg.radar.block.radar.sonar.SonarContraption;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SonarBearingBlockEntity extends MechanicalBearingBlockEntity implements IRadar, RadarDetectionConfigurable {

    public static final int RANGE_PER_PANEL = 8;
    public static final int MAX_EFFECTIVE_PANELS = 8;
    public static final int MAX_RANGE = 64;
    public static final int MAX_BEARING_Y_EXCLUSIVE = 64;
    public static final int MAX_GROUND_AIR_GAP = 2;

    private SonarScanningBlockBehavior scanningBehavior;
    private int panelCount;
    private boolean structureValid;
    private UUID emitterId = UUID.randomUUID();
    private final Set<BlockPos> savedSensorPositions = new HashSet<>();
    private BlockPos lastKnownPos = BlockPos.ZERO;

    public SonarBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateScanningBehavior();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        movementMode.setValue(MovementMode.MOVE_NEVER_PLACE.ordinal());
        scanningBehavior = new SonarScanningBlockBehavior(this);
        behaviours.add(scanningBehavior);
    }

    @Override
    public void applyRadarDetectionSettings(RadarDetectionSettings settings) {
        if (scanningBehavior != null) {
            scanningBehavior.applyDetectionSettings(settings);
        }
    }

    @Override
    public float getAngularSpeed() {
        return 0;
    }

    @Override
    protected void applyRotation() {
        angle = 0;

        if (movedContraption != null) {
            movedContraption.setAngle(0);
            movedContraption.setRotationAxis(getradarDirection().getAxis());
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) {
            return;
        }

        if (!level.isClientSide) {
            long gameTime = level.getGameTime();

            if (gameTime % 10 == 0) {
                refreshStructureValidity();
            }

            if (gameTime % 40 == 0 && level instanceof ServerLevel serverLevel) {
                updateNetworkPosition(serverLevel);
            }
        }

        updateScanningBehavior();
    }

    @Override
    public void assemble() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (!(getBlockState().getBlock() instanceof SonarBearingBlock)) {
            return;
        }

        if (running || movedContraption != null) {
            return;
        }

        SonarContraption contraption = new SonarContraption();

        try {
            if (!contraption.assemble(level, worldPosition)) {
                return;
            }

            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }

        panelCount = contraption.getPanelCount();

        savedSensorPositions.clear();
        savedSensorPositions.addAll(contraption.getSensorPositions());

        Direction direction = getradarDirection();
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        movedContraption.setCustomName(Component.literal("Ground Penetrating Sonar"));
        BlockPos anchor = worldPosition.relative(direction);

        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        movedContraption.setAngle(0);

        level.addFreshEntity(movedContraption);

        running = true;
        angle = 0;

        refreshStructureValidity();
        updateScanningBehavior();

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        setChanged();
        sendData();
        updateGeneratedRotation();
    }

    private SonarContraption createContraption() {
        if (level == null) {
            return null;
        }

        SonarContraption contraption = new SonarContraption();

        try {
            if (!contraption.assemble(level, getBlockPos())) {
                return null;
            }
        } catch (AssemblyException e) {
            return null;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        movedContraption = ControlledContraptionEntity.create(level, this, contraption);

        Direction sensorDirection = contraption.getFacingDirection();

        BlockPos anchor = getBlockPos().relative(sensorDirection);

        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());

        movedContraption.setRotationAxis(sensorDirection.getAxis());
        movedContraption.setAngle(0.0f);

        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, getBlockPos());

        return contraption;
    }

    @Override
    public void disassemble() {
        if (level == null || level.isClientSide) {
            return;
        }

        super.disassemble();

        panelCount = 0;
        structureValid = false;
        savedSensorPositions.clear();

        updateScanningBehavior();

        setChanged();
        sendData();
    }

    @Override
    public boolean isRwrEmitting() {
        return false;
    }

    private void refreshStructureValidity() {
        boolean oldValid = structureValid;

        structureValid = worldPosition.getY() < MAX_BEARING_Y_EXCLUSIVE
                && running
                && movedContraption != null
                && panelCount > 0
                && !savedSensorPositions.isEmpty();

        if (oldValid != structureValid) {
            setChanged();

            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    }

    public Collection<BlockPos> getSensorPositions() {
        return Set.copyOf(savedSensorPositions);
    }

    private void updateScanningBehavior() {
        if (scanningBehavior == null) {
            return;
        }

        scanningBehavior.setRange(getRange());
        scanningBehavior.setRunning(isRunning());
    }

    private void updateNetworkPosition(ServerLevel serverLevel) {
        if (lastKnownPos.equals(worldPosition)) {
            return;
        }

        ResourceKey<Level> dimension = serverLevel.dimension();
        NetworkData data = NetworkData.get(serverLevel);

        if (data.isEndpointLinked(dimension, worldPosition)) {
            lastKnownPos = worldPosition;
            setChanged();
            return;
        }

        if (data.updateRadarPosition(serverLevel, lastKnownPos, worldPosition)) {
            lastKnownPos = worldPosition;
            setChanged();
        }
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        super.attach(contraption);

        if (movedContraption != contraption) {
            return;
        }

        angle = 0;
        applyRotation();

        refreshStructureValidity();
        updateScanningBehavior();

        setChanged();

        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved() && level != null && ModBlocks.SONAR_BEARING.has(getBlockState());
    }

    public boolean isAssembled() {
        return running && movedContraption != null;
    }

    public int getPanelCount() {
        return panelCount;
    }

    public int getEffectivePanelCount() {
        return Math.min(panelCount, MAX_EFFECTIVE_PANELS);
    }

    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        if (scanningBehavior == null) {
            return List.of();
        }

        return scanningBehavior.getRadarTracks();
    }

    @Override
    public float getRange() {
        return Math.min(getEffectivePanelCount() * RANGE_PER_PANEL, MAX_RANGE);
    }

    @Override
    public boolean isRunning() {
        return running && movedContraption != null && structureValid && getRange() > 0 && getSpeed() != 0;
    }

    @Override
    public BlockPos getWorldPos() {
        return worldPosition;
    }

    @Override
    public float getGlobalAngle() {
        return 0f;
    }

    @Override
    public String getRadarType() {
        return "sonar";
    }

    @Override
    public UUID getEmitterId() {
        return emitterId;
    }

    @Override
    public RadarType getRadarTypeEnum() {
        /*
         * Compatibility value only.
         *
         * Sonar never emits into ARAD/RWR because
         * evaluateRwrContact() always returns notEmitting().
         */
        return RadarType.GROUND;
    }

    @Override
    public RwrContactEvaluation evaluateRwrContact(ServerLevel level, RwrTargetReference receiver, RwrTargetReference target) {
        return RwrContactEvaluation.notEmitting();
    }

    @Override
    public Direction getradarDirection() {
        BlockState state = getBlockState();

        if (state.hasProperty(SonarBearingBlock.FACING)) {
            return state.getValue(SonarBearingBlock.FACING);
        }

        return Direction.NORTH;
    }

    @Override
    public float getFovDegrees() {
        return 360f;
    }

    @Override
    public float getSweepAngularSpeedDegreesPerTick() {
        return 0f;
    }

    @Override
    public boolean renderRelativeToMonitor() {
        return false;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        panelCount = tag.getInt("PanelCount");
        structureValid = tag.getBoolean("StructureValid");
        savedSensorPositions.clear();

        for (long packed : tag.getLongArray("SensorPositions")) {
            savedSensorPositions.add(BlockPos.of(packed));
        }

        if (tag.hasUUID("EmitterId")) {
            emitterId = tag.getUUID("EmitterId");
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("PanelCount", panelCount);
        tag.putBoolean("StructureValid", structureValid);
        tag.putLongArray("SensorPositions", savedSensorPositions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putUUID("EmitterId", emitterId);
    }
}