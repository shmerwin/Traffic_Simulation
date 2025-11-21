package wrapper;

import de.tudresden.sumo.cmd.Vehicle;
import it.polito.appeal.traci.SumoTraciConnection;

public class VehicleWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    public VehicleWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
    }

    public String getId() {
        return id;
    }

    public double getSpeed() throws Exception {
        return (double) conn.do_job_get(Vehicle.getSpeed(id));
    }

    public String getRoadId() throws Exception {
        return (String) conn.do_job_get(Vehicle.getRoadID(id));
    }
}