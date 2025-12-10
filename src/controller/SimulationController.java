package controller;

import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.objects.SumoBoundingBox;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoPosition2D;
import de.tudresden.sumo.objects.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;
import view.MainFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Controller that handles the simulation and connection to sumo
 */
public class SimulationController {

    private static final Logger log = Logger.getLogger(SimulationController.class.getName());

    private final Map<String, VehicleWrapper> activeVehicles = new HashMap<>();
    private final Map<String, TrafficLightWrapper> trafficLights = new HashMap<>();
    private final List<EdgeWrapper> mapEdges = new ArrayList<>();

    // history for the statistics panel later
    private final List<Double> speedHistory = new ArrayList<>();
    private double currentAvgSpeed = 0.0;
    private final int maxHistoryPoints = 200;

    private SumoTraciConnection conn;
    private final String sumo;
    private final String config;

    // simulation state
    private boolean isRunning = false;
    private boolean isPaused = true;
    private Thread simThread;
    private int simDelay = 100;

    private MainFrame view;

    // dynamic map boundaries
    private double mapMinX, mapMinY, mapMaxX, mapMaxY;

    public SimulationController(String sumo, String config) {
        this.sumo = sumo;
        this.config = config;
    }

    /**
     * connects the view to the controller
     * @param view the mainframe
     */
    public void setView(MainFrame view) {
        this.view = view;
    }

    /**
     * Connects to SUMO and starts the simulation thread
     */
    public void startConnection() {
        try {
            if (conn == null) {
                conn = new SumoTraciConnection(sumo, config);
                conn.runServer();
                isRunning = true;

                // calculate map bounds from sumo
                Object Bounds = conn.do_job_get(Simulation.getNetBoundary());
                if (Bounds instanceof SumoBoundingBox) {
                    SumoBoundingBox bbox = (SumoBoundingBox) Bounds;
                    this.mapMinX = bbox.x_min; this.mapMinY = bbox.y_min;
                    this.mapMaxX = bbox.x_max; this.mapMaxY = bbox.y_max;
                } else if (Bounds instanceof SumoGeometry) {
                    calculateBoundsFromGeometry((SumoGeometry) Bounds);
                }

                loadStaticMapData();

                // start the loop in a separate thread so gui doesnt freeze
                simThread = new Thread(this::simulationLoop);
                simThread.start();
            }
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

    /**
     * main loop that runs the simulation in the background
     */
    private void simulationLoop() {
        int step = 0;
        while (isRunning) {
            try {
                if (!isPaused) {
                    conn.do_timestep();
                    step++;

                    // Get current data from SUMO
                    refreshData(step);
                    calculateStatistics();

                    // Print report every 100 steps (console log)
                    if (step % 100 == 0) {
                        analyzeTraffic(step);
                    }

                    // update the gui if it exists
                    if (view != null) {
                        javax.swing.SwingUtilities.invokeLater(() -> view.refresh());
                    }
                }
                Thread.sleep(simDelay);
            } catch (Exception e) {
                e.printStackTrace();
                stop();
            }
        }
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

    /**
     * calculates simple stats for the history graph
     */
    private void calculateStatistics() {
        if (activeVehicles.isEmpty()) {
            currentAvgSpeed = 0.0;
        } else {
            double totalSpeed = 0;
            for (VehicleWrapper car : activeVehicles.values()) {
                totalSpeed += car.getSpeed();
            }
            currentAvgSpeed = (totalSpeed / activeVehicles.size()) * 3.6;
        }
        speedHistory.add(currentAvgSpeed);
        if (speedHistory.size() > maxHistoryPoints) {
            speedHistory.remove(0);
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

            // Find most used road
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

    /**
     * adds a new vehicle to sumo
     */
    public void spawnVehicle(String id, String type, String route) {
        if (conn == null) return;
        try {
            double randomPos = 5.0 + (Math.random() * 35.0);
            double startSpeed = 3.0;
            // 0.0 is the depart time (now)
            conn.do_job_set(Vehicle.add(id, type, route, 0, randomPos, startSpeed, (byte) 0));
            log.info("Spawned new vehicle: " + id);
        } catch (Exception e) {
            log.severe("Error spawning vehicle: " + e.getMessage());
        }
    }

    // helper to calculate bounds if sumo returns geometry
    private void calculateBoundsFromGeometry(SumoGeometry geom) {
        if (geom.coords == null || geom.coords.isEmpty()) return;
        double minX = Double.MAX_VALUE; double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE; double maxY = Double.MIN_VALUE;
        for (SumoPosition2D pos : geom.coords) {
            if (pos.x < minX) minX = pos.x; if (pos.y < minY) minY = pos.y;
            if (pos.x > maxX) maxX = pos.x; if (pos.y > maxY) maxY = pos.y;
        }
        this.mapMinX = minX; this.mapMinY = minY; this.mapMaxX = maxX; this.mapMaxY = maxY;
    }

    public void stop() {
        isRunning = false;
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // control methods for the gui
    public void play() {
        if (conn == null) startConnection();
        isPaused = false;
    }

    public void pause() {
        isPaused = true;
    }

    public void setSpeedMultiplier(int value) {
        if (value > 0) this.simDelay = 500 / value;
    }

    // getters for lists
    public List<String> getRouteList() {
        try {
            SumoStringList list = (SumoStringList) conn.do_job_get(Route.getIDList());
            return new ArrayList<>(list);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public List<String> getVehicleTypeList() {
        try {
            SumoStringList list = (SumoStringList) conn.do_job_get(Vehicletype.getIDList());
            return new ArrayList<>(list);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // getters
    public double getMapWidth() { return mapMaxX - mapMinX; }
    public double getMapHeight() { return mapMaxY - mapMinY; }
    public double getMapMaxX() { return mapMaxX; }
    public double getMapMinX() { return mapMinX; }
    public double getMapMaxY() { return mapMaxY; }
    public double getMapMinY() { return mapMinY; }

    public double getCurrentAvgSpeed() { return currentAvgSpeed; }
    public List<Double> getSpeedHistory() { return new ArrayList<>(speedHistory); }

    public java.util.List<EdgeWrapper> getMapEdges() { return mapEdges; }
    public java.util.Map<String, VehicleWrapper> getActiveVehicles() { return activeVehicles; }
    public java.util.Map<String, TrafficLightWrapper> getTrafficLights() {return trafficLights; }

}