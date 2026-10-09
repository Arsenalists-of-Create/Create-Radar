package com.happysg.radar.item;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.api.network.RadarNetworkApi;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.config.RadarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

import java.util.List;

public class GuidedFuzeItem extends FuzeItem {

    private static final String TAG_MONITOR_POS = "monitorPos";
    private static final String TAG_INITIAL_HEADING_YAW = "initialHeadingYaw";
    private static final String TAG_VALID = "valid";

    public GuidedFuzeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();

        if (pContext.getLevel().getBlockEntity(clickedPos) instanceof NetworkFiltererBlockEntity blockEntity) {
            if (!pContext.getLevel().isClientSide()) {
                ItemStack stack = pContext.getItemInHand();
                CompoundTag tag = getCustomData(stack);

                tag.put(TAG_MONITOR_POS, NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
                setCustomData(stack, tag);
            }

            return InteractionResult.sidedSuccess(pContext.getLevel().isClientSide());
        }

        return super.useOn(pContext);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        boolean detonate = super.onProjectileTick(stack, projectile);

        CompoundTag tag = getCustomData(stack);
        if (!tag.contains(TAG_MONITOR_POS))
            return detonate;

        Vec3 vel = projectile.getDeltaMovement();

        // i only start guidance after the projectile has passed the apex and is descending
        if (vel.y > 0 && !RadarConfig.server().guidedFuzeSeekBeforeApex.get())
            return detonate;

        BlockPos monitorPos = NbtUtils.readBlockPos(tag, TAG_MONITOR_POS).orElse(null);
        if (monitorPos == null)
            return detonate;

        if (!(projectile.level() instanceof ServerLevel serverLevel))
            return detonate;

        Vec3 target = RadarNetworkApi.getSelectedTargetPosition(serverLevel, monitorPos).orElse(null);

        if (target == null)
            return detonate;

        boolean dirty = false;

        // --- store initial heading at the top of the arc (first descending tick) ---
        if (!tag.contains(TAG_INITIAL_HEADING_YAW)) {
            double yaw = yawFromHorizontal(vel);
            tag.putDouble(TAG_INITIAL_HEADING_YAW, yaw);
            dirty = true;
        }

        // --- enforce +/- 30 degree seeker cone from initial heading ---
        double initialYaw = tag.getDouble(TAG_INITIAL_HEADING_YAW);
        Vec3 toTarget = target.subtract(projectile.position());

        double targetYaw = yawFromHorizontal(toTarget);
        double yawDelta = wrapDegrees(targetYaw - initialYaw);

        if (Math.abs(yawDelta) > RadarConfig.server().guidedFuzeMaxSeekDegrees.get()) {
            // i refuse to seek anything outside the initial +/- 30 degree cone
            if (dirty)
                setCustomData(stack, tag);

            return detonate;
        }

        // --- existing "valid" gating ---
        double dx = projectile.position().x - target.x;
        double dz = projectile.position().z - target.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (Math.abs(projectile.position().y - target.y) > horizontalDistance / 2 || tag.getBoolean(TAG_VALID)) {
            if (!tag.getBoolean(TAG_VALID)) {
                tag.putBoolean(TAG_VALID, true);
                dirty = true;
            }
        } else {
            if (dirty)
                setCustomData(stack, tag);

            return detonate;
        }

        // --- turn limiting (keeps it from snapping around) ---
        Vec3 desiredDir = toTarget.normalize();

        double originalSpeed = vel.length();
        double speed = Math.max(0.01, originalSpeed);
        Vec3 currentDir = originalSpeed < 1e-6 ? desiredDir : vel.normalize();

        double maxTurnDeg = RadarConfig.server().guidedFuzeMaxDegreesPerTick.get(); // i tune this for how "small" the adjustments should feel
        double maxTurnRad = Math.toRadians(maxTurnDeg);

        double dot = currentDir.dot(desiredDir);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.acos(dot);

        Vec3 newDir;
        if (angle <= maxTurnRad) {
            newDir = desiredDir;
        } else if (angle >= Math.PI - 1e-6) {
            Vec3 fallbackAxis = Math.abs(currentDir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 axis = currentDir.cross(fallbackAxis).normalize();
            newDir = rotateAroundAxis(currentDir, axis, maxTurnRad);
        } else {
            Vec3 axis = currentDir.cross(desiredDir).normalize();
            newDir = rotateAroundAxis(currentDir, axis, maxTurnRad);
        }

        projectile.setDeltaMovement(newDir.scale(speed));

        if (dirty)
            setCustomData(stack, tag);

        return detonate;
    }

    private static CompoundTag getCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setCustomData(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
    public void appendHoverText(ItemStack pStack, TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pTooltipFlag) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pTooltipFlag);

        CompoundTag tag = getCustomData(pStack);
        BlockPos monitorPos = NbtUtils.readBlockPos(tag, TAG_MONITOR_POS).orElse(null);

        if (monitorPos != null) {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor").append(monitorPos.toShortString()));
        } else {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
        }
    }
}