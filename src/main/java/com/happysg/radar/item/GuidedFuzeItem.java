package com.happysg.radar.item;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.config.RadarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.server.level.ServerLevel;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.List;

public class GuidedFuzeItem extends FuzeItem {

    public GuidedFuzeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(clickedPos);
        ItemStack stack = pContext.getItemInHand();
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();

        if (be instanceof NetworkFiltererBlockEntity filterer) {
            tag.put("monitorPos", NbtUtils.writeBlockPos(filterer.getBlockPos()));
            if (level instanceof ServerLevel sl) {
                tag.putString("monitorDim", sl.dimension().location().toString());
            }
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            if (pContext.getPlayer() != null)
                pContext.getPlayer().displayClientMessage(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", filterer.getBlockPos().toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        if (be instanceof MonitorBlockEntity monitor) {
            MonitorBlockEntity controller = monitor.getController();
            if (level instanceof ServerLevel sl) {
                BlockPos filtererPos = com.happysg.radar.block.behavior.networks.NetworkData.get(sl).getFiltererForEndpoint(sl.dimension(), controller.getBlockPos());
                if (filtererPos != null) {
                    tag.put("monitorPos", NbtUtils.writeBlockPos(filtererPos));
                    tag.putString("monitorDim", sl.dimension().location().toString());
                    
                    // Snapshot the target coordinates if one is selected
                    if (controller.selectedEntity != null && controller.activetrack != null) {
                        Vec3 pos = controller.activetrack.position();
                        tag.putDouble("targetX", pos.x);
                        tag.putDouble("targetY", pos.y);
                        tag.putDouble("targetZ", pos.z);
                        tag.putString("targetId", controller.selectedEntity);
                        if (pContext.getPlayer() != null)
                            pContext.getPlayer().displayClientMessage(Component.translatable(CreateRadar.MODID + ".guided_fuze.target_locked"), true);
                    } else {
                        if (pContext.getPlayer() != null)
                            pContext.getPlayer().displayClientMessage(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", filtererPos.toShortString()), true);
                    }
                    
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.useOn(pContext);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        boolean detonate = super.onProjectileTick(stack, projectile);

        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!tag.contains("monitorPos"))
            return detonate;

        Vec3 vel = projectile.getDeltaMovement();

        // i only start guidance after the projectile has passed the apex and is descending
        if (vel.y > 0 && !RadarConfig.server().guidedFuzeSeekBeforeApex.get())
            return detonate;

        BlockPos monitorPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "monitorPos").orElse(net.minecraft.core.BlockPos.ZERO);
        Level monitorLevel = projectile.level();
        if (tag.contains("monitorDim") && projectile.level().getServer() != null) {
            net.minecraft.resources.ResourceKey<Level> dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(tag.getString("monitorDim")));
            ServerLevel sl = projectile.level().getServer().getLevel(dimKey);
            if (sl != null) monitorLevel = sl;
        }

        NetworkFiltererBlockEntity monitor = (monitorLevel.getBlockEntity(monitorPos) instanceof NetworkFiltererBlockEntity m) ? m : null;

        Vec3 target = null;
        if (tag.contains("targetId") && monitor != null) {
            String id = tag.getString("targetId");
            // Try to find the specific target in the monitor's group
            target = monitor.getCachedTracks().stream()
                    .filter(t -> id.equals(t.getId()))
                    .map(RadarTrack::position)
                    .findFirst()
                    .orElse(null);
        }

        if (target == null && tag.contains("targetX")) {
            target = new Vec3(tag.getDouble("targetX"), tag.getDouble("targetY"), tag.getDouble("targetZ"));
        }

        if (target == null && monitor != null && monitor.activeTrackCache != null) {
            target = monitor.activeTrackCache.getPosition();
        }

        if (target == null)
            return detonate;

        // --- Guidance Logic ---
        Vec3 toTarget = target.subtract(projectile.position());
        
        // Optional: Simple Lead Calculation
        // If we have a monitor, we can try to get the target's velocity for better accuracy
        Vec3 targetVel = Vec3.ZERO;
        if (monitor != null && monitor.getCachedTracks() != null) {
             String id = tag.getString("targetId");
             targetVel = monitor.getCachedTracks().stream()
                     .filter(t -> id != null && id.equals(t.getId()))
                     .map(RadarTrack::velocity)
                     .findFirst()
                     .orElse(Vec3.ZERO);
        }
        
        // Predict target position based on travel time
        double dist = toTarget.length();
        double speed = Math.max(0.1, vel.length());
        double timeToTarget = dist / speed;
        Vec3 predictedTarget = target.add(targetVel.scale(timeToTarget));
        Vec3 desiredDir = predictedTarget.subtract(projectile.position()).normalize();

        // --- seeker cone (90 degrees from current velocity) ---
        // we don't want the shell to try to pull a 180 and fly backwards
        Vec3 currentDir = vel.normalize();
        double cosAngle = currentDir.dot(desiredDir);
        if (cosAngle < 0) { // More than 90 degrees away
            return detonate;
        }

        // --- turn limiting ---
        double maxTurnDeg = RadarConfig.server().guidedFuzeMaxDegreesPerTick.get();
        double maxTurnRad = Math.toRadians(maxTurnDeg);

        double dot = currentDir.dot(desiredDir);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.acos(dot);

        Vec3 newDir;
        if (angle <= maxTurnRad) {
            newDir = desiredDir;
        } else {
            Vec3 axis = currentDir.cross(desiredDir);
            if (axis.lengthSqr() < 1e-9) {
                // Parallel or anti-parallel (anti-parallel handled by cosAngle < 0)
                newDir = desiredDir;
            } else {
                newDir = rotateAroundAxis(currentDir, axis.normalize(), maxTurnRad);
            }
        }

        projectile.setDeltaMovement(newDir.scale(speed));
        return detonate;
    }

    private static double yawFromHorizontal(Vec3 v) {
        // i compute yaw from the XZ projection (left/right cone)
        double x = v.x;
        double z = v.z;

        if (Math.abs(x) < 1e-9 && Math.abs(z) < 1e-9)
            return 0.0;

        // Minecraft-ish yaw: atan2(-x, z) gives 0 when facing +Z
        return Math.toDegrees(Math.atan2(-x, z));
    }

    private static double wrapDegrees(double degrees) {
        // i wrap to [-180, 180]
        degrees = degrees % 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return degrees;
    }

    private static Vec3 rotateAroundAxis(Vec3 v, Vec3 axisUnit, double angleRad) {
        // i use Rodrigues' rotation formula
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        Vec3 term1 = v.scale(cos);
        Vec3 term2 = axisUnit.cross(v).scale(sin);
        Vec3 term3 = axisUnit.scale(axisUnit.dot(v) * (1.0 - cos));

        return term1.add(term2).add(term3);
    }

    @Override
    public boolean onProjectileImpact(ItemStack stack, AbstractCannonProjectile projectile, HitResult hitResult, AbstractCannonProjectile.ImpactResult impactResult, boolean baseFuze) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack pStack, net.minecraft.world.item.Item.TooltipContext context, List<Component> pTooltipComponents, net.minecraft.world.item.TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, context, pTooltipComponents, pIsAdvanced);
        CompoundTag tag = pStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag.contains("monitorPos")) {
            BlockPos monitorPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "monitorPos").orElse(net.minecraft.core.BlockPos.ZERO);
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor").append(monitorPos.toShortString()));
            if (tag.contains("targetId")) {
                pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.target_locked").withStyle(net.minecraft.ChatFormatting.GOLD));
            } else if (tag.contains("targetX")) {
                pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.coords_locked").withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        } else
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
    }


}