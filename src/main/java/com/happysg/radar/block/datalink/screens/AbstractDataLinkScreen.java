package com.happysg.radar.block.datalink.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractDataLinkScreen extends Screen {
    protected AbstractDataLinkScreen(Component title) {
        super(title);
    }
}