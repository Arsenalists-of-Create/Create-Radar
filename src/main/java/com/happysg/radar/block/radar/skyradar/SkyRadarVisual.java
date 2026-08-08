package com.happysg.radar.block.radar.skyradar;

import com.happysg.radar.registry.ModPartials;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;

import java.util.function.Consumer;

public class SkyRadarVisual extends AbstractBlockEntityVisual<SkyRadarBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance mount;
    private final RotatingInstance verticalShaft;
    private final RotatingInstance horizontalShaft;

    public SkyRadarVisual(VisualizationContext context, SkyRadarBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        mount = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartials.ROTATING_MOUNT))
                .createInstance();
        verticalShaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                .createInstance();
        horizontalShaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(ModPartials.SKY_RADAR_TOP_SHAFT))
                .createInstance();
        applyYaw(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (doDistanceLimitThisFrame(ctx)) {
            return;
        }
        applyYaw(ctx.partialTick());
    }

    private void applyYaw(float partialTick) {
        boolean unlocked = blockEntity.isVisualUnlocked();
        float yaw = blockEntity.getInterpolatedVisualYaw(partialTick);
        float shaftSpeed = unlocked ? blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER : 0.0f;
        mount.setIdentityTransform()
                .translate(visualPos.getX(), visualPos.getY() + 1, visualPos.getZ())
                .rotateYCentered((float) Math.toRadians(yaw))
                .setChanged();

        verticalShaft.rotation.identity();
        verticalShaft.rotateToFace(net.minecraft.core.Direction.Axis.Y)
                .setRotationAxis(net.minecraft.core.Direction.Axis.Y)
                .setRotationalSpeed(shaftSpeed)
                .setRotationOffset(0)
                .setPosition(visualPos.getX(), visualPos.getY(), visualPos.getZ())
                .setChanged();

        double yawRad = Math.toRadians(yaw);
        float axisX = (float) Math.cos(yawRad);
        float axisZ = (float) -Math.sin(yawRad);
        horizontalShaft.rotation.identity();
        horizontalShaft.rotateTo(0, 1, 0, axisX, 0, axisZ)
                .setRotationAxis(axisX, 0, axisZ)
                .setRotationalSpeed(shaftSpeed)
                .setRotationOffset(0)
                .setPosition(visualPos.getX(), visualPos.getY() + 2, visualPos.getZ())
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(mount, verticalShaft, horizontalShaft);
    }

    @Override
    protected void _delete() {
        mount.delete();
        verticalShaft.delete();
        horizontalShaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(mount);
        consumer.accept(verticalShaft);
        consumer.accept(horizontalShaft);
    }
}
