package org.valkyrienskies.core.api.ships;

import java.util.Collection;

public interface ShipWorld {
    ShipWorld getLoadedShips();
    Ship getById(long id);
    Collection<Ship> getAllShips();
}
