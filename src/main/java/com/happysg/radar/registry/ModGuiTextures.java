package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.mojang.blaze3d.systems.RenderSystem;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public enum ModGuiTextures implements ScreenElement {

    RADAR_FILTER("filter", 219, 113),
    CANNON_TARGETING("targeting", 256, 120),
    X("targeting", 23, 123, 18, 18),
    CHECKMARK("targeting", 3, 123, 18, 18),
    AUTO_FIRE("targeting", 44, 124, 18, 18),
    MANUAL_FIRE("targeting", 64, 124, 18, 18),
    ID_SCREEN("id_block", 225, 95),
    PLAYER_INPUT("radar_iff_list", 0, 136, 225, 120),
    SABLE_INPUT("radar_iff_list", 0, 0, 225, 120),
    FRIENDLY("radar_iff_list", 238, 18, 18, 18),
    HOSTILE("radar_iff_list", 238, 0, 18, 18),
    SHALLOW_MODE("targeting", 104, 124, 18, 18),
    ARTILLERY_MODE("targeting", 84, 124, 18, 18),
    TARGETING_FILTER("targeting_filter",224,107),
    PLAYER_BUTTON("targeting_filter",23,44,16,16),
    SABLE_BUTTON("targeting_filter", 43,44,16,16),
    MOB_BUTTON("targeting_filter",63,44,16,16),
    ANIMAL_BUTTON("targeting_filter",83,44,16,16),
    PROJECTILE_BUTTON("targeting_filter",103,44,16,16),
    LOS_BUTTON("targeting_filter",123,44,16,16),
    AUTO_TARGET("targeting_filter",171,43,16,16),
    CHECK("targeting_filter",192,84,16,16),
    ARTILLERY("targeting_filter",143,44,16,16),
    DETECTION_FILTER("detection_filter",256,96),
    CONTRAPTION_BUTTON("detection_filter",89,39,16,16),
    MISSILE_BUTTON("detection_filter", 173,39,16,16),
    ITEM_BUTTON("detection_filter",201,39,16,16),
    IDENT_FILTER( "identification_filter",159,83),
    FILTER_BUTTON("identification_filter",42 ,26,16,16),
    PLAYER_LIST("identification_filter_0",225,124),
    SCROLL("identification_filter_0",0,147,5,20),
    ID_SMILE("identification_filter_0",156,129,11,11),
    ID_FROWN("identification_filter_0",5,147,11,11),
    ID_X("identification_filter_0",168,129,11,11),
    ID_ADD("identification_filter_0", 183, 125,25,20),
    ID_CARD("identification_filter_0", 0,125,183,22),
    CARD_ADD("identification_filter_0", 183, 125,25,20),
    SHIP_LIST("identification_filter_1",191,83),
    BINOCULAR_OVERLAY("binoculars_scope",0,127,512,256),
    MONITOR_BACKGROUND("monitor_gui",0,0,48,48)
    ;

    public static final int FONT_COLOR = 0x575F7A;

    public final ResourceLocation location;
    public final int width, height;
    public final int startX, startY;
    public final int textureWidth, textureHeight;

    ModGuiTextures(String location, int width, int height) {
        this(location, 0, 0, width, height);
    }

    ModGuiTextures(int startX, int startY) {
        this("icons", startX * 16, startY * 16, 16, 16);
    }

    ModGuiTextures(String location, int startX, int startY, int width, int height) {
        this(CreateRadar.MODID, location, startX, startY, width, height, 256, 256);
    }

    ModGuiTextures(String namespace, String location, int startX, int startY, int width, int height, int textureWidth, int textureHeight) {
        this.location =  ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/" + location + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @OnlyIn(Dist.CLIENT)
    public void bind() {
        RenderSystem.setShaderTexture(0, location);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, startX, startY, width, height);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y, Color c) {
        bind();
        UIRenderHelper.drawColoredTexture(graphics, c, x, y, startX, startY, width, height);
    }
}
