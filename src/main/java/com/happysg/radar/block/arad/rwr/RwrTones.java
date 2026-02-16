package com.happysg.radar.block.arad.rwr;

public final class RwrTones {
    private RwrTones() {}

    //Style 1
    public static final int[] STYLE_1_NEW_CONTACT = { 19, 23 };
    public static final int[] STYLE_1_LOCK_BURST  = { 22, 23, 22, 23, 22, 23, 22, 23 };
    public static final int[] STYLE_1_MISSILE_BURST = { 24, 21, 24, 21, 24, 21, 24, 21, 24 };

    //Style 2
    public static final int[] STYLE_2__NEW_CONTACT = { 17, 20 };
    public static final int[] STYLE_2__NEW_AIR_CONTACT = {17,20,18};
    public static final int[] STYLE_2__LOCK_BEEP = { 21, 21, 21, 21 };
    public static final int[] STYLE_2__LOCK_TWO_TONE = { 20, 20, 22, 22 };
    public static final int[] STYLE_2__MISSILE_BURST = { 24, 19, 24, 19, 24, 19, 24, 19, 24 };
    public static final int[] STYLE_2__MISSILE_SUSTAIN = { 23, 23, 23, 23, 23, 23 };
}

