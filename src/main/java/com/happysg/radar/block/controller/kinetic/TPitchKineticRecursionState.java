
package com.happysg.radar.block.controller.kinetic;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

public final class TPitchKineticRecursionState {

    private static final ThreadLocal<State> CURRENT =
            ThreadLocal.withInitial(State::new);

    private TPitchKineticRecursionState() {}

    public static State get() {
        return CURRENT.get();
    }

    public static final class State {
        public final IdentityHashMap<KineticBlockEntity, Integer> active =
                new IdentityHashMap<>();

        public final ArrayDeque<KineticBlockEntity> path =
                new ArrayDeque<>();

        public int depth;
        public boolean touchedTPitch;
        public boolean warned;

        public void reset() {
            active.clear();
            path.clear();
            depth = 0;
            touchedTPitch = false;
            warned = false;
        }
    }
}
