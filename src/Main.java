import de.tudresden.sumo.cmd.Trafficlight;
import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import wrapper.VehicleWrapper;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    // map to save our vehicle objects so they dont get lost
    private static final Map<String, VehicleWrapper> activeVehicles = new HashMap<>();

    /**
     * Starts the simulation and the connection to sumo and
     * executes the methods for reporting and saving cars
     * @param args command line arguments
     * @throws Exception if the connection to sumo fails
     */
    public static void main(String[] args) throws Exception {
        // arguments for connection
        String sumo = "sumo";
        String config = "sumofiles/frankfurt_city.sumocfg";
        SumoTraciConnection conn = new SumoTraciConnection(sumo, config);

        // starts connection
        conn.runServer();

        for (int i = 0; i < 5000; i++) { // runs simulation for i steps
            conn.do_timestep();
            // get list of all current car ids from sumo
            SumoStringList currentIds = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

            // updates our map to match the sumo list
            updateVehicleList(currentIds, conn);

            // to have not so many reports we only use the method every 100 steps
            if (i % 100 == 0) {
                analyzeTraffic(conn, i);
            }
        }
        // just counts how many trafficlights there are on the map
        int TrafficlightCount = (int) conn.do_job_get(Trafficlight.getIDCount());
        log.info("Trafficlight Count: " + TrafficlightCount);
        conn.close();
        log.info("SumoTraciConnection Closed");
    }

    /**
     * Is a method for updating the Hashmap outside of the main for storing cars
     * @param Ids List of vehicle Ids that are present in the simulation
     * @param conn The active sumo connection for making new instances of the wrapper
     */
    private static void updateVehicleList(SumoStringList Ids, SumoTraciConnection conn) {
        // loop through all ids from sumo
        for (String id : Ids) {
            // check if we already have this car in our map
            if (!activeVehicles.containsKey(id)) {
                // if not create new wrapper and put it in map
                VehicleWrapper newCar = new VehicleWrapper(id, conn);
                activeVehicles.put(id, newCar);
            }
        }
        // remove cars that are not in sumo anymore
        activeVehicles.keySet().retainAll(Ids);
    }

    /**
     * Method for analyzing traffic and reporting the average speed
     * @param conn The connection going on right now for the simulation
     * @param step Makes the report for said step
     * @throws Exception if you cannot get any data
     */
    private static void analyzeTraffic(SumoTraciConnection conn, int step) throws Exception {

        int count = activeVehicles.size();

        double totalSpeed = 0;
        // loop through all our car objects in the map
        for (VehicleWrapper car : activeVehicles.values()) {
            // prints id to console
            //System.out.println(car.getId());

            totalSpeed += car.getSpeed();

        }

        // calculate average
        double avgSpeed = totalSpeed / count;

        log.info("Report for step " + step + ": " + "total cars: " + count + "average speed: " + avgSpeed);
    }
}