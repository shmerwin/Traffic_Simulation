package model;

import de.tudresden.sumo.cmd.Vehicle;
import it.polito.appeal.traci.SumoTraciConnection;

/**
 * Wrapper for a vehicle in the sumo connection
 * Uses traas commands for getting our own
 */
public class VehicleWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    /**
     * Instanciates a new VehicleWrapper object
     * @param id The id of the vehicle in sumo
     * @param conn The connection to sumo
     */
    public VehicleWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
    }

    public String getId() {
        return id;
    }

    /**
     * Method for retrieving vehicle speed
     * @return speed of a specific vehicle
     * @throws Exception if data cannot be retrieved
     */
    public double getSpeed() throws Exception {
        return (double) conn.do_job_get(Vehicle.getSpeed(id));
    }

    /**
     * Method for seeing on which road which car is
     * @return the edge of the specific car
     * @throws Exception if data cannot be retrieved
     */
    public String getRoadId() throws Exception {
        return (String) conn.do_job_get(Vehicle.getRoadID(id));
    }
}