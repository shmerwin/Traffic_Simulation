package controller;

import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Controller that handles the simulation and connection to sumo.
 * Simplified for console-only testing.
 */
public class SimulationController {

    private static final Logger log = Logger.getLogger(SimulationController.class.getName());

    private final Map<String, VehicleWrapper> activeVehicles = new HashMap<>();
    private final Map<String, TrafficLightWrapper> trafficLights = new HashMap<>();
    private final List<EdgeWrapper> mapEdges = new ArrayList<>();

    private SumoTraciConnection conn;
    private final String sumo;
    private final String config;

    public SimulationController(String sumo, String config) {
        this.sumo = sumo;
        this.config = config;
    }

    /**
     * Connects to SUMO and runs the simulation loop directly.
     */
    public void runConsoleSimulation() {
        try {

            conn = new SumoTraciConnection(sumo, config);
            conn.runServer();
            loadStaticMapData();

            for (int step = 1; step <= 1000; step++) {

                conn.do_timestep();
                // Get current data from SUMO
                refreshData(step);

                // Print report every 100 steps
                if (step % 100 == 0) {
                    analyzeTraffic(step);
                }
            }
            stop();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadStaticMapData() throws Exception {
        // Load Edges
        SumoStringList laneIds = (SumoStringList) conn.do_job_get(Lane.getIDList());
        for (String laneId : laneIds) {
            mapEdges.add(new EdgeWrapper(laneId, conn));
        }
        // Load Traffic Lights
        SumoStringList tlsIds = (SumoStringList) conn.do_job_get(Trafficlight.getIDList());
        for (String id : tlsIds) {
            trafficLights.put(id, new TrafficLightWrapper(id, conn));
        }
        log.info("Data loaded: " + mapEdges.size() + " Edges, " + trafficLights.size() + " Traffic Lights.");
    }

    private void refreshData(int step) throws Exception {
        // Update Vehicles
        SumoStringList vIds = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

        // Add new cars
        for (String id : vIds) {
            if (!activeVehicles.containsKey(id)) {
                activeVehicles.put(id, new VehicleWrapper(id, conn));
            }
        }
        // Remove old cars
        activeVehicles.keySet().retainAll(vIds);

        // Update existing cars
        for (VehicleWrapper car : activeVehicles.values()) {
            car.updateData();
        }

        // update tls every 10 steps for performance
        if (step % 10 == 0) {
            for (TrafficLightWrapper tls : trafficLights.values()) {
                tls.updateData();
            }
        }
    }

    private void analyzeTraffic(int step) {
        try {
            int count = activeVehicles.size();
            if (count == 0) {
                log.info("Step " + step + ": No vehicles.");
                return;
            }

            double totalSpeed = 0;
            Map<String, Integer> roadCounts = new HashMap<>();

            for (VehicleWrapper car : activeVehicles.values()) {
                totalSpeed += car.getSpeed();

                // Direct call to SUMO for Road ID
                String roadId = (String) conn.do_job_get(Vehicle.getRoadID(car.getId()));
                roadCounts.merge(roadId, 1, Integer::sum);
            }

            double avgSpeed = (totalSpeed / count) * 3.6; // m/s to km/h

            // Find top road
            String topRoad = "";
            int maxRoad = 0;
            for (Map.Entry<String, Integer> entry : roadCounts.entrySet()) {
                if (entry.getValue() > maxRoad) {
                    maxRoad = entry.getValue();
                    topRoad = entry.getKey();
                }
            }

            log.info(String.format("Step %d | Cars: %d | Speed: %.2f km/h | Top Road: %s (%d)",
                    step, count, avgSpeed, topRoad, maxRoad));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}