package com.happysg.radar.compat.cbcwpf;

public class CBCModernWarfareMunitionPropertiesHandlers {
    public static Handler INFRARED_SEEKER;
    public interface Handler {
        InfraredSeekerProperties getPropertiesOf(Object obj);
    }
}
