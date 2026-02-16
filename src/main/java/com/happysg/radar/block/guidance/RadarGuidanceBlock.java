
package com.happysg.radar.block.guidance;

import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;
import com.happysg.radar.config.RadarConfig;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import riftyboi.cbcmodernwarfare.index.CBCModernWarfareBlocks;
import riftyboi.cbcmodernwarfare.index.CBCModernWarfareMunitionPropertiesHandlers;
import riftyboi.cbcmodernwarfare.munitions.contraptions.MunitionsPhysicsContraptionEntity;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.MunitionsLauncherGuidanceBlock;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.infrared_homing.InfraredSeekerProperties;

import java.util.Map;

public class RadarGuidanceBlock extends MunitionsLauncherGuidanceBlock implements IBE<RadarGuidanceBlockEntity> {
    public RadarGuidanceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidAddition(Map<BlockPos, StructureTemplate.StructureBlockInfo> total, StructureTemplate.StructureBlockInfo data) {
        return true;//todo add check for build config
    }

    @Override
    public BlockEntityType<RadarGuidanceBlockEntity> getBlockEntityType() {
        return (BlockEntityType<RadarGuidanceBlockEntity>) CBCMWCompatRegister.RADAR_GUIDANCE_BLOCK_ENTITY.get();
    }

    @Override
    public Class<RadarGuidanceBlockEntity> getBlockEntityClass() {
        return RadarGuidanceBlockEntity.class;
    }


    @Override
    public boolean canFire(Level level, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity, PitchOrientedContraptionEntity pitchOrientedContraptionEntity) {
        if (blockEntity instanceof RadarGuidanceBlockEntity radarGuidanceBlockEntity) {
            return radarGuidanceBlockEntity.canFire(pitchOrientedContraptionEntity);
        }
        return false;
    }

    @Override
    public void tickGuidance(Level level, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity, OrientedContraptionEntity orientedContraptionEntity) {
        if (orientedContraptionEntity instanceof MunitionsPhysicsContraptionEntity munitionsPhysicsContraptionEntity) {
            if (blockEntity instanceof RadarGuidanceBlockEntity radarGuidanceBlockEntity) {
                radarGuidanceBlockEntity.tickMissileGuidance(munitionsPhysicsContraptionEntity);
            }
        }
    }


    //fixme placeholder copy of InfraredSeekerProperties
    public InfraredSeekerProperties getInfraredProperties() {
        return CBCModernWarfareMunitionPropertiesHandlers.INFRARED_SEEKER.getPropertiesOf(CBCModernWarfareBlocks.INFRARED_SEEKER_GUIDANCE.get());
    }

    public BallisticPropertiesComponent getBallistics() {
        return this.getInfraredProperties().ballisticPropertiesComponent();
    }

    public EntityDamagePropertiesComponent getDamage() {
        return this.getInfraredProperties().entityDamagePropertiesComponent();
    }

    public float turnRate() {
        return RadarConfig.server().radarGuidanceTurnRate.getF();
    }

    public float addedGravity() {
        return this.getInfraredProperties().guidanceBlockProperties().addedGravity();
    }

    public float addedSpread() {
        return this.getInfraredProperties().guidanceBlockProperties().addedSpread();
    }

    public float maxSpeed() {
        return this.getInfraredProperties().guidanceBlockProperties().maxSpeed();
    }

}
