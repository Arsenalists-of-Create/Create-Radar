package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
//import com.happysg.radar.block.behavior.linking.PitchLinkBehavior;
import com.happysg.radar.block.controller.track.TrackLinkBehavior;

import com.happysg.radar.block.behavior.networks.NetworkData;
import net.minecraft.server.level.ServerLevel;
import com.happysg.radar.block.datalink.DataController;
import com.happysg.radar.block.datalink.DataLinkBehavior;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataLinkContext;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.block.datalink.screens.AbstractDataLinkScreen;
import com.happysg.radar.block.monitor.MonitorRadarBehavior;
import org.jetbrains.annotations.NotNull;

//import com.simibubi.create.foundation.utility.RegisteredObjects; //Deprecated
import net.minecraft.core.registries.BuiltInRegistries;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class AllDataBehaviors {
    public static final Map<ResourceLocation, DataLinkBehavior> GATHERER_BEHAVIOURS = new HashMap<>();

    public static final SimpleRegistry<ResourceLocation, DataPeripheral> PERIPHERAL_REGISTRY = SimpleRegistry.create(); // CreateRadar.asResource("data_peripheral")
    public static final SimpleRegistry<ResourceLocation, DataController> CONTROLLER_REGISTRY = SimpleRegistry.create(); // CreateRadar.asResource("data_controller")

    private static final Map<Block, DataPeripheral> SOURCES_BY_BLOCK = new HashMap<>();
    private static final Map<BlockEntityType<?>, DataPeripheral> SOURCES_BY_BLOCK_ENTITY = new HashMap<>();

    private static final Map<Block, DataController> TARGETS_BY_BLOCK = new HashMap<>();
    private static final Map<BlockEntityType<?>, DataController> TARGETS_BY_BLOCK_ENTITY = new HashMap<>();

    public static void registerDefaults() {
        CreateRadar.getLogger().info("Registering Default Data Behaviors...");
        
        // Monitor
        DataLinkBehavior monitorBehavior = new MonitorRadarBehavior();
        DataLinkBehavior registeredMonitor = register(CreateRadar.asResource("monitor"), monitorBehavior);
        assignBlockEntity(registeredMonitor, ModBlockEntityTypes.MONITOR.get());

        // Radars
        DataPeripheral radarBehavior = new DataPeripheral() {
            @Override
            protected AbstractDataLinkScreen getScreen(DataLinkBlockEntity be) {
                return null;
            }

            @Override
            protected void transferData(@NotNull DataLinkContext context, @NotNull DataController activeTarget) {
                if (!(context.level() instanceof ServerLevel sl)) return;
                BlockPos sourcePos = context.getSourcePos();
                BlockPos targetPos = context.getTargetPos();
                BlockEntity targetBE = sl.getBlockEntity(targetPos);
                
                if (targetBE instanceof com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity filterer) {
                    NetworkData data = NetworkData.get(sl);
                    NetworkData.Group group = data.getOrCreateGroup(sl.dimension(), targetPos);
                    if (data.canAttachRadar(group, sourcePos, NetworkData.RadarKind.BEARING)) {
                        data.attachRadar(group, sourcePos, NetworkData.RadarKind.BEARING);
                        data.addDataLinkToGroup(group, context.blockEntity().getBlockPos(), sourcePos);
                    }
                }
            }
        };
        DataLinkBehavior registeredRadar = register(CreateRadar.asResource("radar"), radarBehavior);
        assignBlockEntity(registeredRadar, ModBlockEntityTypes.RADAR_BEARING.get());
        assignBlockEntity(registeredRadar, ModBlockEntityTypes.SKY_RADAR_BE.get());

        // Controllers
        DataController controllerBehavior = new DataController(); // This one is not abstract, but just in case
        DataLinkBehavior registeredController = register(CreateRadar.asResource("controller"), controllerBehavior);
        assignBlockEntity(registeredController, ModBlockEntityTypes.AUTO_YAW_CONTROLLER.get());
        assignBlockEntity(registeredController, ModBlockEntityTypes.AUTO_PITCH_CONTROLLER.get());
        assignBlockEntity(registeredController, ModBlockEntityTypes.FIRE_CONTROLLER.get());

        // Filterers
        DataPeripheral filtererBehavior = new DataPeripheral() {
            @Override
            protected com.happysg.radar.block.datalink.screens.AbstractDataLinkScreen getScreen(DataLinkBlockEntity be) {
                return null;
            }

            @Override
            protected void transferData(@NotNull DataLinkContext context, @NotNull DataController activeTarget) {
                if (!(context.level() instanceof ServerLevel sl)) return;
                BlockPos sourcePos = context.getSourcePos();
                BlockPos targetPos = context.getTargetPos();
                BlockEntity targetBE = sl.getBlockEntity(targetPos);
                
                NetworkData data = NetworkData.get(sl);
                NetworkData.Group group = data.getOrCreateGroup(sl.dimension(), sourcePos);

                if (targetBE instanceof com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity pitch) {
                    BlockPos mountPos = pitch.getBlockPos().relative(pitch.getBlockState().getValue(com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock.HORIZONTAL_FACING));
                    if (data.canAttachWeaponEndpoint(group, targetPos, mountPos)) {
                        data.attachWeaponEndpoint(group, targetPos, mountPos);
                        data.addDataLinkToGroup(group, context.blockEntity().getBlockPos(), targetPos);
                    }
                } else if (targetBE instanceof com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity yaw) {
                    // For Yaw, find the mount pos. Usually, the Yaw controller faces the mount too.
                    BlockPos mountPos = yaw.getBlockPos().relative(yaw.getBlockState().getValue(com.happysg.radar.block.controller.yaw.AutoYawControllerBlock.FACING));
                    if (data.canAttachWeaponEndpoint(group, targetPos, mountPos)) {
                        data.attachWeaponEndpoint(group, targetPos, mountPos);
                        data.addDataLinkToGroup(group, context.blockEntity().getBlockPos(), targetPos);
                    }
                } else if (targetBE instanceof com.happysg.radar.block.monitor.MonitorBlockEntity monitor) {
                    if (data.canAttachMonitor(group, targetPos)) {
                        data.attachMonitor(sl, group, targetPos);
                        data.addDataLinkToGroup(group, context.blockEntity().getBlockPos(), targetPos);
                    }
                }
            }
        };
        DataLinkBehavior registeredFilterer = register(CreateRadar.asResource("network_filterer"), filtererBehavior);
        assignBlockEntity(registeredFilterer, ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get());
        
        // Filterer MUST also be a target for Radars
        DataController filtererTarget = new DataController();
        DataLinkBehavior registeredFiltererTarget = register(CreateRadar.asResource("network_filterer_target"), filtererTarget);
        assignBlockEntity(registeredFiltererTarget, ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get());

        CreateRadar.getLogger().info("Finished Registering Default Data Behaviors.");
    }

    public static DataLinkBehavior register(ResourceLocation id, DataLinkBehavior behaviour) {
        behaviour.id = id;
        GATHERER_BEHAVIOURS.put(id, behaviour);
        if (behaviour instanceof DataPeripheral dp) {
            PERIPHERAL_REGISTRY.register(id, dp);
        }
        if (behaviour instanceof DataController dc) {
            CONTROLLER_REGISTRY.register(id, dc);
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
            assignBlock(register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(registryName.getNamespace(), registryName.getPath() + idSuffix), behaviour), b);
        };
    }

    public static <B extends BlockEntityType<?>> NonNullConsumer<? super B> assignDataBehaviourBE(DataLinkBehavior behaviour, String... suffix) {
        return b -> {
            ResourceLocation registryName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b); // Unsure if works
            String idSuffix = behaviour instanceof DataPeripheral ? "_source" : "_target";
            if (suffix.length > 0)
                idSuffix += "_" + suffix[0];
            assignBlockEntity(register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(registryName.getNamespace(), registryName.getPath() + idSuffix), behaviour), b);
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