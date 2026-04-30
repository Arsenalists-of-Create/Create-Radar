package org.valkyrienskies.core.api.ships;

import org.joml.Matrix4dc;
import org.joml.Vector3dc;

public interface ShipTransform {
    Matrix4dc getWorldToShip();
    Matrix4dc getShipToWorld();
    Vector3dc getShipToWorldRotation();
    Vector3dc getPositionInWorld();
}