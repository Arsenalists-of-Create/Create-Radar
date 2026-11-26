package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.aradnetworks.JamRegistry;
import com.happysg.radar.block.arad.jammer.FakeRadarTrackFactory;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RadarBearingBlockEntity extends MechanicalBearingBlockEntity implements IRadar {
    private BlockPos lastKnownPos;
    private int dishCount;
    private boolean creative;
    private Direction receiverFacing = Direction.NORTH;
    private RadarScanningBlockBehavior scanningBehavior;
    private Collection<RadarTrack> networkFilteredTracks = List.of();
    private long lastFilterTick = -1;


    public RadarBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        lastKnownPos = pos.immutable();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        movementMode.setValue(MovementMode.MOVE_NEVER_PLACE.ordinal());
        scanningBehavior = new RadarScanningBlockBehavior(this);
        behaviours.add(scanningBehavior);
    }

    @Override
    public BlockPos getWorldPos() {
        return getBlockPos();
    }
    @Override
    public void tick() {
        super.tick();

        if (running) {
            scanningBehavior.setRange(getRange());
            scanningBehavior.setAngle(getGlobalAngle());
        }

        // Server-only: recompute at scan cadence (lazyTickRate = 5)
        if (!level.isClientSide) {
            long gt = level.getGameTime();
            if (gt % 5 == 0 && gt != lastFilterTick) {
                lastFilterTick = gt;
                recomputeNetworkFilteredTracks();
            }
        }
        if (!level.isClientSide && level.getGameTime() % 40 == 0) {
            if (level instanceof ServerLevel serverLevel) {

                // nothing to do if we didnt move
                if (lastKnownPos.equals(worldPosition))
                    return;

                ResourceKey<Level> dim = serverLevel.dimension();
                NetworkData data = NetworkData.get(serverLevel);

                if (data.getFiltererForEndpoint(dim, lastKnownPos) == null
                        || (serverLevel.hasChunkAt(lastKnownPos)
                        && serverLevel.getBlockEntity(lastKnownPos) instanceof IRadar)) {
                    lastKnownPos = worldPosition.immutable();
                    setChanged();
                    return;
                }

                if (data.updateRadarPosition(dim, lastKnownPos, worldPosition)) {
                    lastKnownPos = worldPosition.immutable();
                    setChanged();
                }
            }
        }
    }


    public float getGlobalAngle() {
        Vec3 receiverVector = new Vec3(receiverFacing.getStepX(), receiverFacing.getStepY(), receiverFacing.getStepZ());
        double receiverAngle = Math.atan2(receiverVector.x, receiverVector.z);
        double localYaw = receiverAngle + Math.toRadians(angle);
        Vec3 localForward = new Vec3(Math.sin(localYaw), 0.0, Math.cos(localYaw));
        Vec3 worldForward = PhysicsHandler.getWorldVecDirectionTransform(localForward, this);
        if (worldForward == null || !Double.isFinite(worldForward.x) || !Double.isFinite(worldForward.z)
                || worldForward.horizontalDistanceSqr() < 1.0e-12) {
            return (float) ((Math.toDegrees(localYaw) + 540.0) % 360.0);
        }
        return (float) ((Math.toDegrees(Math.atan2(worldForward.x, worldForward.z)) + 540.0) % 360.0);
    }

    public float getAngularSpeed() {
        if (!RadarConfig.server().gearRadarBearingSpeed.get())
            return super.getAngularSpeed();

        float speed = convertToAngular(getSpeed());
        if (getSpeed() == 0)
            speed = 0;
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientAngleDiff / 3f;
        }

        return speed / (4f + getDishCount() / 10);
    }

    @Override
    public void assemble() {
        if (!(level.getBlockState(getBlockPos()).getBlock() instanceof RadarBearingBlock))
            return;

        RadarContraption contraption = createContraption();
        if (contraption == null)
            return;

        if (isWindmill())
            award(AllAdvancements.WINDMILL);
        if (contraption.getSailBlocks() >= 16 * 8)
            award(AllAdvancements.WINDMILL_MAXED);

        updateGeneratedRotation();
        updateContraptionData();
        notifyUpdate();
    }

    @Override
    public void disassemble() {
        super.disassemble();
        updateContraptionData();
    }

    @Override
    public float calculateStressApplied() {
        float impact = (float) BlockStressValues.getImpact(getStressConfigKey()) + getDishCount();
        this.lastStressApplied = impact;
        return impact;
    }

    private RadarContraption createContraption() {
        RadarContraption contraption = new RadarContraption();
        try {
            if (!contraption.assemble(level, getBlockPos()))
                return null;
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return null;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        BlockPos anchor = getBlockPosition().above();
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(Direction.Axis.Y);
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, getBlockPos());


        running = true;
        angle = 0;
        return contraption;
    }

    private void updateContraptionData() {
        dishCount = getContraption().map(RadarContraption::getDishCount).orElse(0);
        receiverFacing = getContraption().map(RadarContraption::getReceiverFacing).orElse(Direction.NORTH);
        creative = getContraption().map(RadarContraption::isCreative).orElse(false);
        scanningBehavior.setRange(getRange());
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));
        scanningBehavior.setRunning(running);
        scanningBehavior.setAngle(getGlobalAngle());
        notifyUpdate();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.translatable(CreateRadar.MODID + ".radar.dish_count", dishCount));
        tooltip.add(Component.translatable(CreateRadar.MODID + ".radar.range", getRange()));
        return true;
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        lastKnownPos = compound.contains("LastKnownPos") ? BlockPos.of(compound.getLong("LastKnownPos")) : worldPosition.immutable();
        dishCount = compound.getInt("dishCount");
        creative = compound.getBoolean("creative");
        if (compound.contains("receiverFacing"))
            receiverFacing = Direction.from3DDataValue(compound.getInt("receiverFacing"));
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putLong("LastKnownPos", lastKnownPos.asLong());
        compound.putInt("dishCount", dishCount);
        compound.putBoolean("creative", creative);
        if (receiverFacing != null)
            compound.putInt("receiverFacing", receiverFacing.get3DDataValue());
    }

    public int getDishCount() {
        return dishCount;
    }

    public Optional<RadarContraption> getContraption() {
        return Optional.ofNullable(movedContraption)
                .map(ControlledContraptionEntity::getContraption)
                .filter(c -> c instanceof RadarContraption)
                .map(c -> (RadarContraption) c);
    }

    public float getAngle() {
        return angle;
    }

    public Direction getReceiverFacing() {
        return receiverFacing;
    }

    public float getRange() {
        if (creative)
            return RadarConfig.server().maxRadarRange.get();
        return Math.min(RadarConfig.server().radarBaseRange.get() + dishCount * RadarConfig.server().dishRangeIncrease.get(),
                RadarConfig.server().maxRadarRange.get());
    }

    public Collection<RadarTrack> getTracks() {
        Collection<RadarTrack> real = scanningBehavior.getRadarTracks();

            if (level instanceof ServerLevel sl &&
                    JamRegistry.isRadarSpoofed(sl, worldPosition)) {
                return FakeRadarTrackFactory.generate(sl, worldPosition, 8);
            }
            return real;
    }
    @Nullable
    private NetworkData.Group getNetworkGroup() {
        if (level == null || level.isClientSide) return null;
        if (!(level instanceof ServerLevel sl)) return null;

        NetworkData data = NetworkData.get(sl);
        BlockPos filtererPos = data.getFiltererForEndpoint(sl.dimension(), worldPosition);
        if (filtererPos == null) return null;

        return data.getGroup(sl.dimension(), filtererPos);
    }

    private DetectionConfig getDetectionFilterFromNetworkOrDefault() {
        NetworkData.Group g = getNetworkGroup();
        if (g == null) return DetectionConfig.DEFAULT;
        return DetectionConfig.fromTag(g.detectionTag);
    }

    private void recomputeNetworkFilteredTracks() {
        if (level == null || level.isClientSide) return;

        // Not networked? Expose raw tracks.
        if (getNetworkGroup() == null) {
            networkFilteredTracks = scanningBehavior.getRadarTracks();
            return;
        }

        DetectionConfig det = getDetectionFilterFromNetworkOrDefault();

        // TODO: add IdentificationConfig filter when implemented
        networkFilteredTracks = scanningBehavior.getRadarTracks().stream()
                .filter(det::test)
                .toList();
    }
    @Override
    public String getRadarType(){
        return "spinning";
    }
    @Override
    public boolean renderRelativeToMonitor(){
        if(!Mods.VALKYRIENSKIES.isLoaded()) return false;
        return (VS2Utils.isBlockInShipyard(level,getBlockPos()));
    }
    @Override
    public Direction getradarDirection() {
        return this.receiverFacing;
    }

}
