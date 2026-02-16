package com.happysg.radar.block.controller.networkcontroller;

import com.happysg.radar.block.behavior.networks.INetworkNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class NetworkFiltererRenderer implements BlockEntityRenderer<NetworkFiltererBlockEntity> {
    private ItemRenderer itemRenderer;

    public NetworkFiltererRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }
    // UV coordinates for the three slots (u, v) in 0..16 (texture pixels).
    private static final float[][] UVS = {
            {5f, 11f},   // slot 0 -> UV(4,4)
            {11f, 11f},   // slot 1 -> UV(8,8)
            {11f, 5f}   // slot 2 -> UV(12,4)
    };

    // small offset so the item sits slightly outside the face (avoid z-fight)
    private static final double OUT_OFFSET = 0.01d;



    @Override
    public void render(NetworkFiltererBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be == null) return;

        // determine face to render on. Try to get from block state; default NORTH.
        BlockState state = be.getBlockState();
        Direction face = Direction.NORTH;
        if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            // safe try: many blocks use FACING;
            face = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        } else {
            // try common "facing" property with DirectionalBlock / FacingBlock fallback
            try {
                if (state.getValues().containsKey(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                    face = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                }
            } catch (Exception ignored) {}
        }

        // Access stacks: prefer direct BE inventory accessor, else fall back to capability.
        ItemStack[] stacks = new ItemStack[3];
        boolean usedCapability = false;

        try {
            // try direct accessor: getStackInSlot(int) or public inventory
            for (int i = 0; i < 3; i++) {
                IItemHandler inv = be.getItemHandler();
                if (inv == null) return;

                stacks[i] = inv.getStackInSlot(i);
            }
        } catch (NoSuchMethodError | AbstractMethodError e) {
            // fallback to capability
            usedCapability = true;
        } catch (Throwable t) {
            // If getStackInSlot doesn't exist, fall back below
            usedCapability = true;
        }

        if (usedCapability) {
            var level = be.getLevel();
            if (level == null) return;

            IItemHandler inv = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
            if (inv == null) return;

            for (int i = 0; i < 3; i++) {
                stacks[i] = inv.getStackInSlot(i);
            }
        }

        // Render each non-empty slot
        for (int i = 0; i < 3; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;

            float u = UVS[i][0];
            float v = UVS[i][1];

            ms.pushPose();

            // center the origin at block center
            ms.translate(0.5d, 0.5d, 0.5d);

            // local coords in -0.5..+0.5 from UV
            double localX = (u / 16.0d) - 0.5d;       // u: left->right maps to X+
            double localY = 0.5d - (v / 16.0d);       // v: top->bottom maps to Y-

            switch (face) {
                case NORTH -> {
                    // outward = -Z
                    ms.translate(localX, localY, OUT_OFFSET);
                    // rotate so item faces outward (-Z). flip so it's upright.
                    ms.mulPose(Axis.YP.rotationDegrees(180f));
                    ms.mulPose(Axis.XP.rotationDegrees(180f));
                }
                case SOUTH -> {
                    // outward = +Z
                    ms.translate(-localX, localY, OUT_OFFSET);
                    ms.mulPose(Axis.XP.rotationDegrees(180f));
                }
                case WEST -> {
                    // outward = -X
                    ms.translate(OUT_OFFSET, localY, -localX);
                    // rotate to make +Z point outward (-X originally)
                    ms.mulPose(Axis.YP.rotationDegrees(90f));
                    ms.mulPose(Axis.XP.rotationDegrees(180f));
                }
                case EAST -> {
                    // outward = +X
                    ms.translate(OUT_OFFSET, localY, localX);
                    ms.mulPose(Axis.YP.rotationDegrees(-90f));
                    ms.mulPose(Axis.XP.rotationDegrees(180f));
                }
                case UP -> {
                    // outward = +Y (top face)
                    ms.translate(localX, OUT_OFFSET, localY);
                    // rotate so item faces upward (rotate -90 around X)
                    ms.mulPose(Axis.XP.rotationDegrees(-90f));
                }
                case DOWN -> {
                    // outward = -Y (bottom face)
                    ms.translate(localX, OUT_OFFSET, -localY);
                    ms.mulPose(Axis.XP.rotationDegrees(90f));
                }
            }


            final float scale = 0.3f; // smaller = tighter to face
            ms.scale(scale, scale, scale);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, ms, buffers,null, 0);


            ms.popPose();
        }
    }


}

