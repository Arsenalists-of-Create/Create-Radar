package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;

public class YawControllerPeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return CreateRadar.asResource("yaw_controller").toString();
    }

    /**
     * Sets yaw angle. Same deal: stopAuto() on pitch controller is what actually
     * prevents WFC from overriding aim.
     */
    @LuaFunction(mainThread = true)
    public void setAngle(AutoYawControllerBlockEntity entity, double angle) {
        entity.setTargetAngle((float) angle);
    }

    @LuaFunction(mainThread = true)
    public double getAngle(AutoYawControllerBlockEntity entity) {
        return entity.getTargetAngle();
    }


    @LuaFunction(mainThread = true)
    public void stopAuto(AutoPitchControllerBlockEntity entity) {
        if (entity.firingControl != null) {
            entity.firingControl.resetTarget();
        }
        entity.isRunning = false;
    }
}