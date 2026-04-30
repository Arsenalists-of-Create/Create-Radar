package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;

public class PitchControllerPeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return CreateRadar.asResource("pitch_controller").toString();
    }

    /**
     * Sets pitch angle
     * NOTE: This does not stop WeaponFiringControl from overriding it
     * Use stopAuto() first if you want manual control to stick
     */
    @LuaFunction(mainThread = true)
    public void setAngle(AutoPitchControllerBlockEntity entity, double angle) {
        entity.setTargetAngle((float) angle);
    }

    @LuaFunction(mainThread = true)
    public double getAngle(AutoPitchControllerBlockEntity entity) {
        return entity.getTargetAngle();
    }

    /**
     * Stops auto-aim/autofire control from overriding pitch by clearing the WeaponFiringControl target
     * This is the "manual mode" toggle without changing the BE
     */
    @LuaFunction(mainThread = true)
    public void stopAuto(AutoPitchControllerBlockEntity entity) {
        if (entity.firingControl != null) {
            entity.firingControl.resetTarget();
        }
        entity.isRunning = false;
    }
}