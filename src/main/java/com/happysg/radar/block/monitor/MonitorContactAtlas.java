package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class MonitorContactAtlas {
    private static final MonitorSprite[] SPRITES = {
            MonitorSprite.CONTRAPTION_HITBOX,
            MonitorSprite.ENTITY_HITBOX,
            MonitorSprite.PROJECTILE,
            MonitorSprite.PLAYER,
            MonitorSprite.TARGET_SELECTED,
            MonitorSprite.TARGET_HOVERED
    };

    private static final int COLUMNS = 3;
    private static final int ROWS = 2;

    private static final ResourceLocation TEXTURE = CreateRadar.asResource("dynamic/monitor_contacts");

    private static boolean loaded;
    private static boolean attempted;

    private static final float CROP_MIN = 0.375f;
    private static final float CROP_MAX = 0.625f;

    private static final Bounds[] BOUNDS = new Bounds[SPRITES.length];
    public record Bounds(float minU, float minV, float maxU, float maxV) {}
    public static Bounds bounds(int index) {
        if (index < 0 || index >= BOUNDS.length) {
            return null;
        }
        return BOUNDS[index];
    }

    private MonitorContactAtlas() {}

    public static ResourceLocation texture() {
        if (loaded) return TEXTURE;
        if (attempted) return null;

        attempted = true;

        try {
            build();
            loaded = true;
            return TEXTURE;
        } catch (Exception e) {
            CreateRadar.getLogger().error("Failed to build monitor contact atlas", e);
            return null;
        }
    }

    public static void invalidate() {
        loaded = false;
        attempted = false;
        java.util.Arrays.fill(BOUNDS, null);
    }

    public static int index(MonitorSprite sprite) {
        for (int i = 0; i < SPRITES.length; i++) {
            if (SPRITES[i] == sprite) return i;
        }
        return -1;
    }

    public static float u(int index, float localU) {
        return ((index % COLUMNS) + localU) / COLUMNS;
    }

    public static float v(int index, float localV) {
        return ((index / COLUMNS) + localV) / ROWS;
    }

    private static void build() throws IOException {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager resources = mc.getResourceManager();

        List<NativeImage> images = new ArrayList<>();
        int tileSize = 1;

        try {
            for (MonitorSprite sprite : SPRITES) {
                try (InputStream stream = resources.getResourceOrThrow(sprite.getTexture()).open()) {

                    NativeImage image = NativeImage.read(stream);
                    images.add(image);

                    tileSize = Math.max(tileSize, Math.max(image.getWidth(), image.getHeight()));
                }
            }

            NativeImage atlas = new NativeImage(NativeImage.Format.RGBA, tileSize * COLUMNS, tileSize * ROWS, true);

            try {
                for (int i = 0; i < images.size(); i++) {
                    NativeImage source = images.get(i);

                    int startX = (i % COLUMNS) * tileSize;
                    int startY = (i / COLUMNS) * tileSize;

                    int cropStart = (int) Math.ceil(tileSize * CROP_MIN);
                    int cropEnd = (int) Math.ceil(tileSize * CROP_MAX);

                    int minX = cropEnd;
                    int minY = cropEnd;
                    int maxX = cropStart - 1;
                    int maxY = cropStart - 1;

                    for (int y = 0; y < tileSize; y++) {
                        int sourceY = y * source.getHeight() / tileSize;
                        for (int x = 0; x < tileSize; x++) {
                            int sourceX = x * source.getWidth() / tileSize;
                            int pixel = source.getPixelRGBA(sourceX, sourceY);

                            atlas.setPixelRGBA(startX + x, startY + y, pixel);

                            if (x >= cropStart && x < cropEnd && y >= cropStart && y < cropEnd && ((pixel >>> 24) & 0xFF) != 0) {
                                minX = Math.min(minX, x);
                                minY = Math.min(minY, y);
                                maxX = Math.max(maxX, x);
                                maxY = Math.max(maxY, y);
                            }
                        }
                    }

                    if (maxX >= minX && maxY >= minY) {
                        int left = Math.max(cropStart, minX - 1);
                        int top = Math.max(cropStart, minY - 1);
                        int right = Math.min(cropEnd, maxX + 2);
                        int bottom = Math.min(cropEnd, maxY + 2);
                        BOUNDS[i] = new Bounds((float) left / tileSize, (float) top / tileSize, (float) right / tileSize, (float) bottom / tileSize);
                    } else {
                        BOUNDS[i] = new Bounds(CROP_MIN, CROP_MIN, CROP_MAX, CROP_MAX);
                    }
                }

                DynamicTexture texture = new DynamicTexture(atlas);
                mc.getTextureManager().register(TEXTURE, texture);
                texture.upload();
            } catch (RuntimeException e) {
                atlas.close();
                throw e;
            }

        } finally {
            for (NativeImage image : images) image.close();
        }
    }
}