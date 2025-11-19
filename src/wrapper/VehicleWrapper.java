package wrapper;

import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.Vehicle;

public class VehicleWrapper {

    private final String id;

    public VehicleWrapper(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public double getSpeed() {
        return Vehicle.getSpeed(id);
    }

    public String getRoadId() {
        return Vehicle.getRoadID(id);
    }

    public TraCIPosition getPosition() {
        return Vehicle.getPosition(id);
    }

    @Override
    public String toString() {
        return "Vehicle[" + id + "]";
    }
}
