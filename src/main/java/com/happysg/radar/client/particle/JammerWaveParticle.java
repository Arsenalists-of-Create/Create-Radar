package com.happysg.radar.client.particle;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.registry.ModParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class JammerWaveParticle extends TextureSheetParticle {
    private static final int DURATION = 12;
    private static final float START_RADIUS = 0.125F;
    private static final float END_RADIUS = 0.65F;
    private final SpriteSet sprites;

    private JammerWaveParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);

        this.sprites = sprites;
        this.lifetime = DURATION;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        this.quadSize = START_RADIUS;
        this.setSpriteFromAge(sprites);
    }

    private static float waveGrowth(float progress) {
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return 1.0F - (1.0F - progress) * (1.0F - progress);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.removed) {
            float progress = Math.min(1.0F, (float) this.age / this.lifetime);
            this.quadSize = START_RADIUS + (END_RADIUS - START_RADIUS) * waveGrowth(progress);
            this.alpha = 1.0F - progress * 0.85F;

            this.setSpriteFromAge(sprites);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Math.min(1.0F, (this.age + partialTick) / this.lifetime);
        return START_RADIUS + (END_RADIUS - START_RADIUS) * waveGrowth(progress);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vector3f forward = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        if (forward.lengthSquared() < 1.0e-8F) {
            forward.set(0, 0, 1);
        }

        forward.normalize();
        Vector3f toCamera = new Vector3f((float) (camera.getPosition().x - this.x), (float) (camera.getPosition().y - this.y), (float) (camera.getPosition().z - this.z));
        if (toCamera.lengthSquared() < 1.0e-8F) {
            toCamera.set(forward);
        }

        toCamera.normalize();
        if (forward.dot(toCamera) < 0.0F) {
            forward.negate();
        }

        Vector3f normal = new Vector3f(forward).mul(0.65F).add(new Vector3f(toCamera).mul(0.35F)).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), normal);

        this.renderRotatedQuad(buffer, camera, rotation, partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new JammerWaveParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    @EventBusSubscriber(modid = CreateRadar.MODID, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void registerProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.JAMMER_WAVE.get(), Provider::new);
        }
    }
}