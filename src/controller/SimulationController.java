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
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = true;
    private volatile boolean isAutoMode = false;
    private Thread simThread;
    private volatile int simDelay = 100;
    private volatile String activeFilter = "All";


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
                // Restore standard connection logic without manual port handling
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
     * loads map data, separates visuals from logic, and validates edges.
     */
    private void loadMapData() {
        try {
            log.info("Loading map data...");
            SumoStringList laneIds = (SumoStringList) conn.do_job_get(Lane.getIDList());
            Set<String> safeEdgeSet = new HashSet<>();

            for (String laneId : laneIds) {
                // visuals: add all lanes (except internal)
                mapEdges.add(new EdgeWrapper(laneId, conn));

                // logic: filter for cars only
                if (!laneId.startsWith(":")) {
                    try {
                        // Check explicit permissions
                        SumoStringList allowed = (SumoStringList) conn.do_job_get(Lane.getAllowed(laneId));
                        SumoStringList disallowed = (SumoStringList) conn.do_job_get(Lane.getDisallowed(laneId));

                        // A lane is valid if it allows 'passenger' OR allows everything, AND does not explicitly forbid 'passenger'
                        boolean isAllowed = allowed.isEmpty() || allowed.contains("passenger");
                        boolean isNotForbidden = disallowed == null || !disallowed.contains("passenger");

                        if (isAllowed && isNotForbidden) {
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

            // Validation: Remove broken edges that cause crashes
            validateNetwork();

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
     * Validates all loaded edges by asking SUMO if a route can be computed.
     * Removes edges that cause TraCI errors (sometimes there appears an error
     * with invalid starting edge)
     */
    private void validateNetwork() {
        log.info("Validating " + drivableEdges.size() + " potential edges (this may take a moment)...");
        Iterator<String> it = drivableEdges.iterator();
        int removed = 0;

        while (it.hasNext()) {
            String edge = it.next();
            try {
                // Try to find a route from the edge to itself.
                // If the edge is invalid for passenger cars SUMO throws an error
                conn.do_job_get(Simulation.findRoute(edge, edge, "DEFAULT_VEHTYPE", 0.0, 0));
            } catch (Exception e) {
                // If finding a route fails this edge is dangerous to spawn on
                it.remove();
                removed++;
            }
        }
        log.info("Network validation complete. Removed " + removed + " invalid edges. Remaining: " + drivableEdges.size());
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
                        getVehicleSpeed();

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

        // UPDATE TRAFFIC LIGHTS: Sync every step
        for (TrafficLightWrapper tls : trafficLights.values()) {
            tls.updateData();
        }
    }

    /**
     * method to calculate averagespeed
     */
    private void getVehicleSpeed() {
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
                // Catch errors to prevent thread death
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
        int maxAttempts = 20; // Reduced to prevent long freezes

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
            } catch (Exception e) {
                // route not found try next
            }
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
     * method to display TravelTimeChart
     */
    public int[] getTravelTimeBins() {
        int[] bins = new int[5]; // <=30, <=60, <=120, <=300, >300

        for (VehicleWrapper car : getActiveVehicles().values()) {
            long time = car.getTravelTimeSeconds();
            if (time <= 30) bins[0]++;
            else if (time <= 60) bins[1]++;
            else if (time <= 120) bins[2]++;
            else if (time <= 300) bins[3]++;
            else bins[4]++;
        }

        return bins;
    }


    /**
     * method to display EdgeDensity chart
     */
    public int[] getEdgeDensityBins() {
        int[] bins = new int[6];

        //counts vehicles per edge
        Map<String, Integer> edgeCounts = new HashMap<>();
        for (VehicleWrapper v : getActiveVehicles().values()) {
            try {
                String edgeId = v.getRoadId();
                if (edgeId == null || edgeId.isBlank()) continue;
                if (edgeId.startsWith(":")) continue;
                edgeCounts.merge(edgeId, 1, Integer::sum);
            } catch (Exception ignored) {}
        }

        Set<String> seenEdges = new HashSet<>();
        for (EdgeWrapper lane : getMapEdges()) {
            if (lane == null) continue;

            String laneId = lane.getId();
            if (laneId == null || laneId.isBlank()) continue;
            if (laneId.startsWith(":")) continue;

            int idx = laneId.lastIndexOf('_');
            if (idx <= 0) continue;

            String edgeId = laneId.substring(0, idx);
            if (!seenEdges.add(edgeId)) continue;

            int c = edgeCounts.getOrDefault(edgeId, 0);

            if (c == 0) bins[0]++;
            else if (c == 1) bins[1]++;
            else if (c == 2) bins[2]++;
            else if (c <= 5) bins[3]++;
            else if (c <= 10) bins[4]++;
            else bins[5]++;
        }

        return bins;
    }



    public void setActiveFilter(String filter) {
        this.activeFilter = (filter == null) ? "All" : filter;
        if (view != null) Platform.runLater(view::refresh);
    }

    public String getActiveFilter() {
        return activeFilter;
    }

    public void setTrafficLightPhaseDuration(String tlsId, double seconds) {
        if (conn == null) return;
        synchronized (traciLock) {
            TrafficLightWrapper tls = trafficLights.get(tlsId);
            if (tls != null) tls.setPhaseDuration(seconds);
        }
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