import de.tudresden.sumo.cmd.Trafficlight;
import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;

import wrapper.VehicleWrapper;

import java.util.HashMap;
import java.util.Map;


public class Main {
    // map to save our vehicle objects so they dont get lost
    private static final Map<String, VehicleWrapper> activeVehicles = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // arguments for connection
        String sumo = "sumo";
        String config = "sumofiles/test123.sumocfg";
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
        System.out.printf("Trafficlight Count: %d\n", TrafficlightCount);

        conn.close();
        System.out.println("Simulation finished");
    }

    // updates Vehicle list in every iteration to retain oop structure
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

    // defining method for checking the traffic on all cars
    private static void analyzeTraffic(SumoTraciConnection conn, int step) throws Exception {

        int count = activeVehicles.size();

        double totalSpeed = 0;
        // loop through all our car objects in the map
        for (VehicleWrapper car : activeVehicles.values()) {
            // prints id to console
            System.out.println(car.getId());

            totalSpeed += car.getSpeed();

        }

        // calculate average
        double avgSpeed = totalSpeed / count;

        System.out.println("Report for Step " + step + "\n" +
                "Active cars " + count + "\n" +
                "Avergae Speed: " + avgSpeed + "m/s\n" +"\n\n" );

    }
}