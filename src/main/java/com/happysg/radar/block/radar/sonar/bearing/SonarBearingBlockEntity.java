package com.happysg.radar.block.radar.sonar.bearing;

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
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SonarBearingBlockEntity extends KineticBlockEntity implements IRadar, IControlContraption {

    public static final int RANGE_PER_PANEL = 8;
    public static final int MAX_EFFECTIVE_PANELS = 8;
    public static final int MAX_RANGE = 64;

    public static final int MAX_BEARING_Y_EXCLUSIVE = 64;

    public static final int MAX_GROUND_AIR_GAP = 2;

    private SonarScanningBlockBehavior scanningBehavior;
    private ControlledContraptionEntity movedContraption;

    private int panelCount;

    private boolean structureValid;
    private boolean assembled;

    private UUID emitterId = UUID.randomUUID();

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

        scanningBehavior = new SonarScanningBlockBehavior(this);
        behaviours.add(scanningBehavior);
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

    public void assemble() {
        tryAssemble();
    }

    private boolean tryAssemble() {
        if (level == null || level.isClientSide) {
            return false;
        }

        if (!(level.getBlockState(getBlockPos()).getBlock()
                instanceof SonarBearingBlock)) {
            return false;
        }

        if (assembled || movedContraption != null) {
            return true;
        }

        SonarContraption contraption = createContraption();

        if (contraption == null) {
            return false;
        }

        panelCount = contraption.getPanelCount();
        assembled = true;

        refreshStructureValidity();
        updateScanningBehavior();

        setChanged();
        notifyUpdate();

        return true;
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

    public void disassemble() {
        if (level == null) {
            return;
        }

        if (!assembled && movedContraption == null) {
            return;
        }

        if (movedContraption != null) {
            movedContraption.setAngle(0.0f);
            movedContraption.disassemble();

            if (!level.isClientSide) {
                AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, getBlockPos());
            }
        }

        movedContraption = null;
        assembled = false;
        panelCount = 0;
        structureValid = false;

        updateScanningBehavior();

        setChanged();
        notifyUpdate();
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            disassemble();
        }

        super.remove();
    }

    private void refreshStructureValidity() {
        if (level == null) {
            structureValid = false;
            return;
        }

        boolean oldValid = structureValid;
        boolean yValid = worldPosition.getY() < MAX_BEARING_Y_EXCLUSIVE;
        boolean hasPanels = assembled && panelCount > 0;
        boolean surfaceValid = false;

        if (hasPanels) {
            Direction sensorDirection = getSensorDirection();
            BlockPos endSensor = worldPosition.relative(sensorDirection, panelCount);
            surfaceValid = isEndSensorNearSurface(endSensor, sensorDirection);
        }

        structureValid = yValid && hasPanels && surfaceValid;

        if (oldValid != structureValid) {
            setChanged();

            if (!level.isClientSide) {
                notifyUpdate();
            }
        }
    }

    private boolean isEndSensorNearSurface(
            BlockPos endSensor,
            Direction sensorDirection
    ) {
        if (level == null) {
            return false;
        }

        for (int gap = 0; gap <= MAX_GROUND_AIR_GAP; gap++) {

            BlockPos surfacePos = endSensor.relative(sensorDirection, gap + 1);

            if (surfacePos.getY() < level.getMinBuildHeight()) {
                return true;
            }

            if (surfacePos.getY() >= level.getMaxBuildHeight()) {
                return false;
            }

            BlockState state = level.getBlockState(surfacePos);

            if (state.isFaceSturdy(level, surfacePos, sensorDirection.getOpposite())) {
                return true;
            }
        }

        return false;
    }

    private Direction getSensorDirection() {
        BlockState state = getBlockState();

        if (state.hasProperty(SonarBearingBlock.FACING)) {
            return state.getValue(SonarBearingBlock.FACING);
        }

        return Direction.DOWN;
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

        if (data.updateRadarPosition(dimension, lastKnownPos, worldPosition)) {
            lastKnownPos = worldPosition;
            setChanged();
        }
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        if (!(contraption.getContraption() instanceof SonarContraption sonar)) {
            return;
        }

        movedContraption = contraption;
        assembled = true;
        panelCount = sonar.getPanelCount();

        Direction sensorDirection = sonar.getFacingDirection();

        movedContraption.setRotationAxis(sensorDirection.getAxis());

        movedContraption.setAngle(0.0f);

        updateScanningBehavior();
    }

    @Override
    public void onStall() {
        notifyUpdate();
    }

    @Override
    public boolean isValid() {
        return !isRemoved() && level != null && ModBlocks.SONAR_BEARING.has(getBlockState());
    }

    @Override
    public BlockPos getBlockPosition() {
        return getBlockPos();
    }

    public Optional<SonarContraption> getContraption() {
        return Optional.ofNullable(movedContraption)
                .map(ControlledContraptionEntity::getContraption)
                .filter(SonarContraption.class::isInstance)
                .map(SonarContraption.class::cast);
    }

    public boolean isAssembled() {
        return assembled;
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
        return assembled && movedContraption != null && structureValid && getRange() > 0 && getSpeed() != 0;
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
        assembled = tag.getBoolean("Assembled");

        if (tag.hasUUID("EmitterId")) {
            emitterId = tag.getUUID("EmitterId");
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("PanelCount", panelCount);
        tag.putBoolean("StructureValid", structureValid);
        tag.putBoolean("Assembled", assembled);
        tag.putUUID("EmitterId", emitterId);
    }
}