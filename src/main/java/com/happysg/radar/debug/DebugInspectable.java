package com.happysg.radar.debug;

/**
 * Implemented by blocks that can safely expose an explicit, read-only
 * diagnostic snapshot. Raw NBT should never be appended here.
 */
public interface DebugInspectable {
    void appendDebugInfo(DiagnosticSnapshotBuilder builder,
                         DiagnosticContext context);
}
