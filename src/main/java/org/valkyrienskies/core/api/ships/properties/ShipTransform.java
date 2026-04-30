package org.valkyrienskies.core.api.ships.properties;

import org.joml.Quaterniondc;
import org.joml.Matrix4dc;

public interface ShipTransform {
    Quaterniondc getRotation();
    Quaterniondc getShipToWorldRotation();
    Matrix4dc getWorldToShip();
}
