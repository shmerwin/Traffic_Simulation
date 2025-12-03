package controller;

import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;
import model.VehicleWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Controller that handles the simulation and connection to sumo
 * Refactored from Main.java for MVC pattern
 */
public class SimulationController {

    private static final Logger log = Logger.getLogger(SimulationController.class.getName());

    // map to save our vehicle objects so they dont get lost
    private final Map<String, VehicleWrapper> activeVehicles = new HashMap<>();

    private SumoTraciConnection conn;
    private final String sumo;
    private final String config;
    private boolean isRunning = false;

    /**
     * Instantiates the controller with connection arguments
     * @param sumo path to sumo executable
     * @param config path to config file
     */
    public SimulationController(String sumo, String config) {
        this.sumo = sumo;
        this.config = config;
    }

    /**
     * Starts the simulation and the connection to sumo
     */
    public void startConnection() {
        try {
            conn = new SumoTraciConnection(sumo, config);
            // starts connection
            conn.runServer();
            isRunning = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Executes the methods for reporting and saving cars in a loop
     */
    public void runLoop() {
        if (conn == null) startConnection();

        try {
            // runs simulation for i steps
            for (int i = 0; i < 1001; i++) {
                if (!isRunning) break;

                conn.do_timestep();

                // updates our map to match the sumo list
                refreshData();

                // to have not so many reports we only use the method every 100 steps
                if (i % 100 == 0) {
                    analyzeTraffic(i);
                }
            }
            stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Is a method for updating the Hashmap for storing cars
     * @throws Exception if data cannot be retrieved
     */
    private void refreshData() throws Exception {
        // get list of all current car ids from sumo
        SumoStringList currentIds = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

        // loop through all ids from sumo
        for (String id : currentIds) {
            // check if we already have this car in our map
            if (!activeVehicles.containsKey(id)) {
                // if not create new wrapper and put it in map
                VehicleWrapper newCar = new VehicleWrapper(id, conn);
                activeVehicles.put(id, newCar);
            }
        }
        // remove cars that are not in sumo anymore
        activeVehicles.keySet().retainAll(currentIds);
    }

    /**
     * Method for analyzing traffic and reporting the average speed
     * @param step Makes the report for said step
     */
    private void analyzeTraffic(int step) {
        try {
            int count = activeVehicles.size();
            double totalSpeed = 0;

            // loop through all our car objects in the map
            for (VehicleWrapper car : activeVehicles.values()) {
                // prints id to console
                //System.out.println(car.getId());

                totalSpeed += car.getSpeed();
            }
            // calculate average
            double avgSpeed = totalSpeed / count * 3.6;

            log.info("Report for step " + step + ":" + " total cars: " + count + ", average speed: " + avgSpeed + "km/h");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (conn != null && !conn.isClosed()) {
                // just counts how many trafficlights there are on the map
                int TrafficlightCount = (int) conn.do_job_get(Trafficlight.getIDCount());
                log.info("Trafficlight Count: " + TrafficlightCount);

                conn.close();
                log.info("SumoTraciConnection Closed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Getter for GUI
    public Map<String, VehicleWrapper> getActiveVehicles() {
        return activeVehicles;
    }
}