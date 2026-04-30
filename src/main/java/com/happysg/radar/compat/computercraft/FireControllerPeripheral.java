package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;

public class FireControllerPeripheral implements GenericPeripheral {

    @Override
    public String id() {
        return CreateRadar.asResource("fire_controller").toString();
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered(FireControllerBlockEntity be) {
        return be.isPowered();
    }

    // ON
    @LuaFunction(mainThread = true)
    public void fireOn(FireControllerBlockEntity be) {
        be.setPowered(true);
    }

    // OFF
    @LuaFunction(mainThread = true)
    public void fireOff(FireControllerBlockEntity be) {
        be.setPowered(false);
    }

    // Convenience toggle
    @LuaFunction(mainThread = true)
    public void setPowered(FireControllerBlockEntity be, boolean powered) {
        be.setPowered(powered);
    }

    // Keep firing pulse
    @LuaFunction(mainThread = true)
    public void keepFiring(FireControllerBlockEntity be) {
        // Calling setPowered(true) refreshes lastCommandTick even if already powered
        be.setPowered(true);
    }
}