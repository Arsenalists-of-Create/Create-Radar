package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.track.TrackLinkBehavior;
import com.happysg.radar.block.datalink.ControllersDataLinkBehavior;
import com.happysg.radar.block.datalink.DataController;
import com.happysg.radar.block.datalink.DataLinkBehavior;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.block.monitor.MonitorRadarBehavior;
import com.happysg.radar.block.network.RadarNetworkLinkBehaviour;
import com.happysg.radar.block.network.WeaponNetworkLinkBehaviour;
import com.happysg.radar.block.radar.behavior.RadarScannerLinkBehavior;
//import com.simibubi.create.foundation.utility.RegisteredObjects; //Deprecated
import net.minecraft.core.registries.BuiltInRegistries;

import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.index.CBCBlockEntities;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class AllDataBehaviors {
    public static final Map<ResourceLocation, DataLinkBehavior> GATHERER_BEHAVIOURS = new HashMap<>();

    public static final Map<ResourceLocation, DataPeripheral> PERIPHERAL_REGISTRY = new HashMap<>(); // CreateRadar.asResource("data_peripheral")
    public static final Map<ResourceLocation, DataController> CONTROLLER_REGISTRY = new HashMap<>();// CreateRadar.asResource("data_controller")

    private static final Map<Block, DataPeripheral> SOURCES_BY_BLOCK = new HashMap<>();
    private static final Map<BlockEntityType<?>, DataPeripheral> SOURCES_BY_BLOCK_ENTITY = new HashMap<>();

    private static final Map<Block, DataController> TARGETS_BY_BLOCK = new HashMap<>();
    private static final Map<BlockEntityType<?>, DataController> TARGETS_BY_BLOCK_ENTITY = new HashMap<>();

    public static void registerDefaults() {
        assignBlockEntity(register(CreateRadar.asResource("monitor"), new MonitorRadarBehavior()), ModBlockEntityTypes.MONITOR.get());
        assignBlockEntity(register(CreateRadar.asResource("monitor"), new RadarNetworkLinkBehaviour()), ModBlockEntityTypes.MONITOR.get());
        assignBlockEntity(register(CreateRadar.asResource("radar"), new RadarScannerLinkBehavior()), ModBlockEntityTypes.RADAR_BEARING.get());
        assignBlockEntity(register(CreateRadar.asResource("radar"), new DataController()), ModBlockEntityTypes.RADAR_BEARING.get());
        assignBlockEntity(register(CreateRadar.asResource("track"), new TrackLinkBehavior()), ModBlockEntityTypes.TRACK_CONTROLLER.get());
        assignBlockEntity(register(CreateRadar.asResource("plane_radar"), new RadarScannerLinkBehavior()), ModBlockEntityTypes.PLANE_RADAR.get());
        assignBlockEntity(register(CreateRadar.asResource("auto_pitch_controller_link"), new ControllersDataLinkBehavior()), ModBlockEntityTypes.AUTO_PITCH_CONTROLLER.get());
        assignBlockEntity(register(CreateRadar.asResource("fire_controller_link"), new ControllersDataLinkBehavior()), ModBlockEntityTypes.FIRE_CONTROLLER.get());
        assignBlockEntity(register(CreateRadar.asResource("auto_yaw_controller_link"), new ControllersDataLinkBehavior()), ModBlockEntityTypes.AUTO_YAW_CONTROLLER.get());
        assignBlockEntity(register(CreateRadar.asResource("cannon_mount_link"), new DataController()), CBCBlockEntities.CANNON_MOUNT.get());
        assignBlockEntity(register(CreateRadar.asResource("cannon_mount_link"), new WeaponNetworkLinkBehaviour()), CBCBlockEntities.CANNON_MOUNT.get());


    }

    public static DataLinkBehavior register(ResourceLocation id, DataLinkBehavior behaviour) {
        behaviour.id = id;
        GATHERER_BEHAVIOURS.put(id, behaviour);
        if (behaviour instanceof DataPeripheral dp) {
            PERIPHERAL_REGISTRY.put(id, dp);
        }
        if (behaviour instanceof DataController dc) {
            CONTROLLER_REGISTRY.put(id, dc);
        }
        return behaviour;
    }

    public static void assignBlock(DataLinkBehavior behaviour, Block block) {
        if (behaviour instanceof DataPeripheral source) {
            SOURCES_BY_BLOCK.put(block, source);
        }
        if (behaviour instanceof DataController target) {
            TARGETS_BY_BLOCK.put(block, target);
        }
    }

    public static void assignBlockEntity(DataLinkBehavior behaviour, BlockEntityType<?> beType) {
        if (behaviour instanceof DataPeripheral source) {
            SOURCES_BY_BLOCK_ENTITY.put(beType, source);
        }
        if (behaviour instanceof DataController target) {
            TARGETS_BY_BLOCK_ENTITY.put(beType, target);
        }
    }

    public static <B extends Block> NonNullConsumer<? super B> assignDataBehaviour(DataLinkBehavior behaviour, String... suffix) {
        return b -> {
            ResourceLocation registryName = BuiltInRegistries.BLOCK.getKey(b);
            String idSuffix = behaviour instanceof DataPeripheral ? "_source" : "_target";
            if (suffix.length > 0)
                idSuffix += "_" + suffix[0];
            assignBlock(register(new ResourceLocation(registryName.getNamespace(), registryName.getPath() + idSuffix), behaviour), b);
        };
    }

    public static <B extends BlockEntityType<?>> NonNullConsumer<? super B> assignDataBehaviourBE(DataLinkBehavior behaviour, String... suffix) {
        return b -> {
            ResourceLocation registryName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b); // Unsure if works
            String idSuffix = behaviour instanceof DataPeripheral ? "_source" : "_target";
            if (suffix.length > 0)
                idSuffix += "_" + suffix[0];
            assignBlockEntity(register(new ResourceLocation(registryName.getNamespace(), registryName.getPath() + idSuffix), behaviour), b);
        };
    }

    @Nullable
    public static DataPeripheral getSource(ResourceLocation id) {
        DataLinkBehavior available = GATHERER_BEHAVIOURS.get(id);
        return (available instanceof DataPeripheral source) ? source : null;
    }

    @Nullable
    public static DataController getTarget(ResourceLocation id) {
        DataLinkBehavior available = GATHERER_BEHAVIOURS.get(id);
        return (available instanceof DataController target) ? target : null;
    }

    public static DataPeripheral sourcesOf(Block block) {
        return SOURCES_BY_BLOCK.get(block);
    }

    public static DataPeripheral sourcesOf(BlockState state) {
        return sourcesOf(state.getBlock());
    }

    public static DataPeripheral sourcesOf(BlockEntityType<?> type) {
        return SOURCES_BY_BLOCK_ENTITY.get(type);
    }

    public static DataPeripheral sourcesOf(BlockEntity entity) {
        return sourcesOf(entity.getType());
    }

    @Nullable
    public static DataController targetOf(Block block) {
        return TARGETS_BY_BLOCK.get(block);
    }

    @Nullable
    public static DataController targetOf(BlockState state) {
        return targetOf(state.getBlock());
    }

    @Nullable
    public static DataController targetOf(BlockEntityType<?> type) {
        return TARGETS_BY_BLOCK_ENTITY.get(type);
    }

    @Nullable
    public static DataController targetOf(BlockEntity entity) {
        return targetOf(entity.getType());
    }

    public static DataPeripheral sourcesOf(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        DataPeripheral fromBlock = sourcesOf(state);
        DataPeripheral fromEntity = (entity != null) ? sourcesOf(entity) : null;
        return (fromEntity != null) ? fromEntity : fromBlock;
    }

    @Nullable
    public static DataController targetOf(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        DataController fromBlock = targetOf(state);
        DataController fromEntity = (entity != null) ? targetOf(entity) : null;
        return (fromEntity != null) ? fromEntity : fromBlock;
    }
}
