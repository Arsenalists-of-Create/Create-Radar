package org.valkyrienskies.core.api.ships;

import org.joml.Matrix4dc;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import net.minecraft.world.phys.AABB;

public interface Ship {
    long getId();
    String getSlug();
    ShipTransform getTransform();
    Matrix4dc getWorldToShip();
    Matrix4dc getShipToWorld();
    Vector3dc getVelocity();
    AABB getShipAABB();
    AABB getWorldAABB();
}