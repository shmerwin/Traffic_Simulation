package controller;

import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.objects.*;
import it.polito.appeal.traci.SumoTraciConnection;
import javafx.application.Platform;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;
import view.FxMainFrame;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller that handles the simulation and connection to sumo
 */
public class SimulationController {

    private static final Logger log = Logger.getLogger(SimulationController.class.getName());

    // use ConcurrentHashMap to prevent crashes when gui reads while sim thread writes
    private final Map<String, VehicleWrapper> activeVehicles = new ConcurrentHashMap<>();
    private final Map<String, TrafficLightWrapper> trafficLights = new HashMap<>();

    // contains all lanes so the map looks complete
    private final List<EdgeWrapper> mapEdges = new ArrayList<>();

    // contains only valid car edges for safe spawning
    private final List<String> drivableEdges = new ArrayList<>();

    // lock object to synchronize all traci communication
    private final Object traciLock = new Object();

    // history for the statistics panel
    private final List<Double> speedHistory = new ArrayList<>();
    private double currentAvgSpeed = 0.0;
    private final int maxHistoryPoints = 200;

    private SumoTraciConnection conn;
    private final String sumo;
    private final String config;

    // simulation state
    // Volatile keywords added to ensure thread visibility between GUI and Sim-Thread
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = true;
    private volatile boolean isAutoMode = false;
    private Thread simThread;
    private volatile int simDelay = 100;

    private FxMainFrame view;

    // dynamic map boundaries
    private double mapMinX, mapMinY, mapMaxX, mapMaxY;
    private final Random random = new Random();

    public SimulationController(String sumo, String config) {
        this.sumo = sumo;
        this.config = config;
    }

    /**
     * connects the view to the controller
     * @param view the mainframe
     */
    public void setView(FxMainFrame view) {
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

                // synchronize initial data fetching
                synchronized (traciLock) {
                    // calculate map bounds from sumo
                    Object Bounds = conn.do_job_get(Simulation.getNetBoundary());
                    if (Bounds instanceof SumoBoundingBox) {
                        SumoBoundingBox bbox = (SumoBoundingBox) Bounds;
                        this.mapMinX = bbox.x_min; this.mapMinY = bbox.y_min;
                        this.mapMaxX = bbox.x_max; this.mapMaxY = bbox.y_max;
                    } else if (Bounds instanceof SumoGeometry) {
                        calculateBoundsFromGeometry((SumoGeometry) Bounds);
                    }

                    loadMapData();
                }

                // start the loop in a separate thread so gui doesnt freeze
                simThread = new Thread(this::simulationLoop);
                simThread.start();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * loads map data and separates visuals from logic
     */
    private void loadMapData() {
        try {
            log.info("Loading map data");
            SumoStringList laneIds = (SumoStringList) conn.do_job_get(Lane.getIDList());
            Set<String> safeEdgeSet = new HashSet<>();

            for (String laneId : laneIds) {
                // visuals: add all lanes (except internal)
                mapEdges.add(new EdgeWrapper(laneId, conn));

                // logic: filter for cars only
                if (!laneId.startsWith(":")) {
                    try {
                        SumoStringList allowed = (SumoStringList) conn.do_job_get(Lane.getAllowed(laneId));
                        // if empty (all allowed) or contains "passenger"
                        if (allowed.isEmpty() || allowed.contains("passenger")) {
                            String edgeId = laneId.substring(0, laneId.lastIndexOf('_'));
                            safeEdgeSet.add(edgeId);
                        }
                    } catch (Exception e) {
                        // ignore permission errors
                    }
                }
            }

            drivableEdges.addAll(safeEdgeSet);
            Collections.sort(drivableEdges);

            // load traffic lights
            SumoStringList tlsIds = (SumoStringList) conn.do_job_get(Trafficlight.getIDList());
            for (String id : tlsIds) {
                trafficLights.put(id, new TrafficLightWrapper(id, conn));
            }

            log.info("Map Loaded: " + mapEdges.size() + " Visual Lanes | " + drivableEdges.size() + " Drivable Edges.");

        } catch (Exception e) {
            log.severe("Error loading map: " + e.getMessage());
        }
    }

    /**
     * main loop that runs the simulation in the background
     */
    private void simulationLoop() {
        int step = 0;
        while (isRunning) {
            try {
                if (!isPaused) {
                    synchronized (traciLock) {
                        conn.do_timestep();
                        step++;

                        // get current data from sumo
                        refreshData(step);
                        calculateStatistics();

                        if (isAutoMode && step % 10 == 0) {
                            handleTrafficLightsAuto();
                        }

                        // print report every 100 steps (console log)
                        if (step % 100 == 0) {
                            analyzeTraffic(step);
                        }
                    }

                    // update the gui if it exists
                    if (view != null) {
                        Platform.runLater(() -> view.refresh());
                    }
                }
                Thread.sleep(simDelay);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error simulation: " + e.getMessage());
                stop();
            }
        }
    }

    private void handleTrafficLightsAuto() {
        long currentTime = System.currentTimeMillis();
        for (TrafficLightWrapper tls : trafficLights.values()) {
            if (currentTime - tls.getLastSwitchTime() < 5000) {
                continue;
            }
            int waiting = tls.getWaitingVehicleCount();
            if (waiting > 2) {
                tls.nextPhase();
            }
        }
    }

    public void setAutoMode(boolean active) {
        this.isAutoMode = active;
        log.info("Traffic Light Auto Mode: " + active);
    }

    private void refreshData(int step) throws Exception {
        // update vehicles
        SumoStringList vIds = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

        // add new cars
        for (String id : vIds) {
            if (!activeVehicles.containsKey(id)) {
                activeVehicles.put(id, new VehicleWrapper(id, conn));
            }
        }
        // remove old cars
        activeVehicles.keySet().retainAll(vIds);

        // update existing cars
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
        // optional analysis logic here
    }

    /**
     * adds a new vehicle to sumo safely
     * spawns a thread to avoid blocking the GUI
     */
    public void spawnVehicle(String id, String type, String selection, javafx.scene.paint.Color color) {
        if (conn == null) return;
        new Thread(() -> spawnVehicleInternal(id, type, selection, color)).start();
    }

    /**
     * Internal method containing the spawn logic.
     * Synchronized to ensure thread safety during batch operations.
     */
    private void spawnVehicleInternal(String id, String type, String selection, javafx.scene.paint.Color color) {
        synchronized (traciLock) {
            try {
                String fromEdge = null;

                // picks a safe edge
                if (selection == null || selection.equals("Random Route") || selection.startsWith("!")) {
                    if (!drivableEdges.isEmpty()) {
                        fromEdge = drivableEdges.get(random.nextInt(drivableEdges.size()));
                    }
                } else if (drivableEdges.contains(selection)) {
                    fromEdge = selection;
                }

                if (fromEdge == null) {
                    log.warning("Cannot spawn: No drivable edge found for selection: " + selection);
                    return;
                }

                String routeId = generateRouteFrom(id, fromEdge);

                if (routeId != null) {
                    // random offset to prevent invisible queue for cars
                    double randomPos = 5.0 + (Math.random() * 35.0);
                    double startSpeed = 3.0;

                    // 0 is the depart time
                    conn.do_job_set(Vehicle.add(id, type, routeId, 0, randomPos, startSpeed, (byte) 0));

                    VehicleWrapper newCar = new VehicleWrapper(id, conn);
                    newCar.setColor(color);

                    activeVehicles.put(id, newCar);

                    // reduce logging noise during stress tests
                    if (!id.startsWith("stress_")) {
                        log.info("Spawned " + id + " on " + fromEdge);
                    }
                } else {
                    log.warning("No path found from " + fromEdge);
                }

            } catch (Exception e) {
                log.severe("Error spawning vehicle " + id + ": " + e.getMessage());
            }
        }
    }

    /**
     * Starts a stress test by spawning 50 vehicles.
     * Uses a single thread for the batch to minimize overhead.
     */
    public void startStressTest(String selectedEdge, javafx.scene.paint.Color color) {
        if (!isRunning) return;

        new Thread(() -> {
            log.info("Starting Stress Test (50 Cars) on edge: " + selectedEdge);
            long batchId = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                String id = "stress_" + batchId + "_" + i;

                spawnVehicleInternal(id, "DEFAULT_VEHTYPE", selectedEdge, color);

                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
            log.info("Stress Test Injection Loop Finished.");
        }).start();
    }

    /**
     * helper to generate dynamic route
     */
    private String generateRouteFrom(String vehicleId, String fromEdge) throws Exception {
        if (drivableEdges.size() < 2) return null;

        int attempts = 0;
        int maxAttempts = 100; // Increased attempts to find valid routes in complex networks

        while (attempts < maxAttempts) {
            String toEdge = drivableEdges.get(random.nextInt(drivableEdges.size()));
            if (fromEdge.equals(toEdge)) { attempts++; continue; }

            try {
                Object result = conn.do_job_get(Simulation.findRoute(fromEdge, toEdge, "DEFAULT_VEHTYPE", -1.0, 0));
                if (result instanceof SumoStage) {
                    SumoStage stage = (SumoStage) result;
                    if (stage.edges != null && !stage.edges.isEmpty()) {
                        String newRouteId = "route_" + vehicleId + "_" + System.nanoTime();
                        conn.do_job_set(Route.add(newRouteId, stage.edges));
                        return newRouteId;
                    }
                }
            } catch (Exception e) {}
            attempts++;
        }
        return null;
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
            log.log(Level.SEVERE, "Error closing connection.", e);
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
        List<String> list = new ArrayList<>();
        list.add("Random Route");
        list.addAll(drivableEdges);
        return list;
    }

    public List<String> getVehicleTypeList() {
        try {
            synchronized (traciLock) {
                return new ArrayList<>((SumoStringList) conn.do_job_get(Vehicletype.getIDList()));
            }
        } catch (Exception e) { return new ArrayList<>(); }
    }

    /**
     * method for density of edges
     */
    public double getVehicleDensity(EdgeWrapper edge) {
        try {
            int count = edge.getVehicle();

            if (edge.getLength() > 0) {
                return (double) count / edge.getLength();
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }



    /**
     * method to return every edge with hotspot
     */
    public List<String> getCongestionHotspots() {
        List<String> hotspots = new ArrayList<>();
        for (EdgeWrapper edge : mapEdges) {
            try {

                double meanSpeed = (double) conn.do_job_get(Lane.getLastStepMeanSpeed(edge.getId()));
                //if cars are on this edge and the meanSpeed is below 2m/s
                if (edge.getVehicle() > 0 && meanSpeed < 2.0) {
                    hotspots.add(edge.getId());
                }
            } catch (Exception e) {
            }
        }
        return hotspots;
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

    public List<EdgeWrapper> getMapEdges() { return mapEdges; }
    public Map<String, VehicleWrapper> getActiveVehicles() { return activeVehicles; }
    public Map<String, TrafficLightWrapper> getTrafficLights() { return trafficLights; }

}