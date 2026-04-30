package org.valkyrienskies.clockwork.platform.api;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;

public interface ContraptionController {
    enum LockedMode implements INamedIconOptions {
        FOLLOW_ANGLE;
        @Override public String getTranslationKey() { return ""; }
        @Override public com.simibubi.create.foundation.gui.AllIcons getIcon() { return null; }
    }
}
