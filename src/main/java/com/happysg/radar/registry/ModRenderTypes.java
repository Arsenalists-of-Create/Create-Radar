package com.happysg.radar.registry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.BiFunction;

//credit Technomancy
public class ModRenderTypes extends RenderType {

    public ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    private static final BiFunction<ResourceLocation, Boolean, RenderType> POLYGON_OFFSET = Util.memoize((texture, affectsOutline) -> {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false) {
                    @Override
                    public void setupRenderState() {
                        super.setupRenderState();
                        //clamp the UV coordinates to prevent the texture from wrapping
                        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                    }
                })
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLayeringState(POLYGON_OFFSET_LAYERING)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(affectsOutline);
        return create("polygon_offset", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, state);
    });

    /**
     * Returns a RenderType with polygon offset applied to the specified texture.
     * Used by Monitor to render texture on screen.
     *
     * @param texture The ResourceLocation of the texture to be used.
     * @return A RenderType configured with polygon offset for the given texture.
     */
    public static RenderType polygonOffset(ResourceLocation texture) {
        return POLYGON_OFFSET.apply(texture, true);
    }
}