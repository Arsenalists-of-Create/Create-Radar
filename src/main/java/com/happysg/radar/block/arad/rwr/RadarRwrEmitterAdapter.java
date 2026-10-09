package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.api.radar.rwr.RadarRwrEmitter;
import com.happysg.radar.api.radar.rwr.RadarRwrEvaluation;
import com.happysg.radar.api.radar.rwr.RadarRwrTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class RadarRwrEmitterAdapter {

    private RadarRwrEmitterAdapter() {}

    public static RwrContactEvaluation evaluate(RadarRwrEmitter emitter, ServerLevel level, Vec3 receiverPosition, Vec3 targetPosition, String targetId) {
        RadarRwrEvaluation evaluation = emitter.evaluateRwrContact(level,
                new RadarRwrTarget(receiverPosition, null),
                new RadarRwrTarget(targetPosition, targetId)
        );

        if (evaluation == null) {
            return RwrContactEvaluation.notEmitting();
        }

        return new RwrContactEvaluation(
                evaluation.emitting(),
                evaluation.detectable(),
                evaluation.lockCapable(),
                evaluation.locked(),
                evaluation.signalStrength()
        );
    }
}