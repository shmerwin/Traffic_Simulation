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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;




/**
 * The core controller of the application.
 * Manages the TraCI connection to SUMO, runs the simulation loop in a separate thread,
 * synchronizes data between the engine and the GUI, and handles user interactions
 * (like spawning vehicles or exporting reports).
 */
public class SimulationController {

    private static final Logger log = Logger.getLogger(SimulationController.class.getName());

    // use ConcurrentHashMap to prevent crashes when gui reads while sim thread writes
    private final Map<String, VehicleWrapper> activeVehicles = new ConcurrentHashMap<>();
    private final Map<String, TrafficLightWrapper> trafficLights = new HashMap<>();

    // Lists for map visualization and logic
    private final List<EdgeWrapper> mapEdges = new ArrayList<>();
    // Cache for valid spawning edges to avoid "No route found" errors
    private final List<String> drivableEdges = new ArrayList<>();

    // lock object to synchronize all traci communication
    private final Object traciLock = new Object();

    // history for the statistics panel
    private final List<Double> speedHistory = new ArrayList<>();
    private double currentAvgSpeed = 0.0;
    private final int maxHistoryPoints = 200;
    private long lastGuiUpdate = 0;

    // Simulation configuration
    private SumoTraciConnection conn;
    private final String sumo;
    private final String config;

    // simulation state (volatile for visibility across threads)
    private volatile boolean isRunning = false;
    public volatile boolean isPaused = true;
    private volatile boolean isAutoMode = true;
    private volatile int simDelay = 100; // delay in ms between steps
    private volatile String activeFilter = "All";


    // reporting metrics
    private volatile long currentStep = 0; // for reports
    private volatile long simulationStartWallTimeMs = 0;

    // View & Navigation
    private FxMainFrame view;
    // dynamic map boundaries
    private double mapMinX, mapMinY, mapMaxX, mapMaxY;
    private final Random random = new Random();

    /**
     * Creates the controller instance
     * @param sumo Path to the SUMO executable (sumo-gui or sumo)
     * @param config Path to the .sumocfg configuration file
     * */
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
     * Establishes the connection to the SUMO server and initializes the simulation
     * Starts the background thread that drives the simulation loop
     */
    public void startConnection() throws SimulationException {
        try {
            if (conn == null) {
                // Initialize the TraCI connection wrapper
                conn = new SumoTraciConnection(sumo, config);
                conn.runServer();
                isRunning = true;

                // Reset timers for the new session
                simulationStartWallTimeMs = System.currentTimeMillis();
                currentStep = 0;

                // Synchronized block to ensure data integrity during initial load
                synchronized (traciLock) {
                    // Retrieve map boundaries for coordinate transformation (SUMO to JavaFX)
                    Object bounds = conn.do_job_get(Simulation.getNetBoundary());
                    if (bounds instanceof SumoBoundingBox) {
                        SumoBoundingBox bbox = (SumoBoundingBox) bounds;
                        this.mapMinX = bbox.x_min; this.mapMinY = bbox.y_min;
                        this.mapMaxX = bbox.x_max; this.mapMaxY = bbox.y_max;
                    } else if (bounds instanceof SumoGeometry) {
                        calculateBoundsFromGeometry((SumoGeometry) bounds);
                    }

                    // Load the road network and traffic lights
                    loadMapData();
                }

                // Start the simulation loop in a background thread
                Thread simThread = new Thread(this::simulationLoop, "Sim-Thread");
                simThread.start();
                log.info("Simulation started successfully.");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Fatal error starting SUMO connection: " + e.getMessage(), e);
            throw new SimulationException("Could not start SUMO. Check path or config file.", e);
        }
    }

    /**
     * Loads static map data (lanes, edges, traffic lights) from SUMO
     * Filters edges to identify those valid for passenger cars to prevent spawning errors
     */
    private void loadMapData() {
        try {
            log.info("Loading map structure and permissions...");
            SumoStringList laneIds = (SumoStringList) conn.do_job_get(Lane.getIDList());
            Set<String> safeEdgeSet = new HashSet<>();

            for (String laneId : laneIds) {
                // Add all lanes so the map looks complete
                mapEdges.add(new EdgeWrapper(laneId, conn));

                // Filter for drivable edges (exclude internal lanes starting with ":")
                if (!laneId.startsWith(":")) {
                    try {
                        // Get allowed and disallowed vehicle classes
                        SumoStringList allowed = (SumoStringList) conn.do_job_get(Lane.getAllowed(laneId));
                        SumoStringList disallowed = (SumoStringList) conn.do_job_get(Lane.getDisallowed(laneId));

                        // A lane is valid if 'passenger' is allowed OR list is empty (all allowed)
                        // AND 'passenger' is not explicitly in the disallowed list
                        boolean isAllowed = allowed.isEmpty() || allowed.contains("passenger");
                        boolean isNotForbidden = disallowed == null || !disallowed.contains("passenger");

                        if (isAllowed && isNotForbidden) {
                            // Convert lane ID (e.g., "edge1_0") to edge ID ("edge1")
                            String edgeId = laneId.substring(0, laneId.lastIndexOf('_'));
                            safeEdgeSet.add(edgeId);
                        }
                    } catch (Exception e) {
                        // Ignore permission read errors for specific lanes
                    }
                }
            }

            // Update the cache of drivable edges
            drivableEdges.addAll(safeEdgeSet);
            Collections.sort(drivableEdges);

            // Validate edges to prevent runtime crashes during spawning
            validateNetwork();

            // Load Traffic Lights
            SumoStringList tlsIds = (SumoStringList) conn.do_job_get(Trafficlight.getIDList());
            for (String id : tlsIds) {
                trafficLights.put(id, new TrafficLightWrapper(id, conn));
            }

            log.info("Map Init Complete: " + mapEdges.size() + " visual lanes | " + drivableEdges.size() + " spawnable edges.");

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error loading map data: " + e.getMessage(), e);
        }
    }

    /**
     * Validates all potentially drivable edges by attempting to calculate a route.
     * Removes edges where SUMO cannot find a path (isolated roads), preventing future crashes.
     */
    private void validateNetwork() {
        log.info("Validating network topology (this may take a moment)...");
        Iterator<String> it = drivableEdges.iterator();
        int removed = 0;

        while (it.hasNext()) {
            String edge = it.next();
            try {
                // Testing to see if edge can be routed to itself
                // If SUMO throws an error here, the edge is unusable for spawning
                conn.do_job_get(Simulation.findRoute(edge, edge, "DEFAULT_VEHTYPE", 0.0, 0));
            } catch (Exception e) {
                it.remove();
                removed++;
            }
        }
        log.info("Network Validation Finished: Removed " + removed + " broken/isolated edges.");
    }
    /**
     * The main simulation loop running in a separate background thread.
     * Advances the SUMO simulation step-by-step, synchronizes data with the internal model,
     * calculates statistics, and triggers the GUI refresh.
     */
    private void simulationLoop() {
        while (isRunning) {
            try {
                if (!isPaused) {
                    // Critical Section: Exclusive access to TraCI
                    synchronized (traciLock) {
                        // Advance Simulation
                        conn.do_timestep();
                        currentStep++;

                        // Fetch Logic & Data
                        refreshData();
                        calculateStats();

                        // Execute Traffic Logic (e.g. adaptive lights)
                        if (isAutoMode) {
                            handleTrafficLightsAuto();
                        }

                        // Console Logging
                        if (currentStep % 100 == 0) {
                            log.info("Step " + currentStep + ": Active Vehicles: " + activeVehicles.size());
                        }
                    }

                    // Notify GUI (Thread-Safe)
                    long now = System.currentTimeMillis();
                    if (view != null && (now - lastGuiUpdate > 33)) {
                        Platform.runLater(() -> view.refresh());
                        lastGuiUpdate = now;
                    }
                }

                // Control Simulation Speed
                Thread.sleep(simDelay);

            } catch (Exception e) {
                log.log(Level.SEVERE, "Error in simulation loop: " + e.getMessage(), e);
                // Emergency Stop to prevent log spamming
                stop();
            }
        }
    }

    /**
     * Synchronizes the state of vehicles and traffic lights from SUMO to Java
     */
    private void refreshData() throws Exception {
        // Get list of all currently active vehicle IDs
        SumoStringList vIds = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

        // Add new vehicles
        for (String id : vIds) {
            if (!activeVehicles.containsKey(id)) {
                activeVehicles.put(id, new VehicleWrapper(id, conn));
            }
        }

        // Remove vehicles that have left the simulation
        // retainAll keeps only keys that are present in vIds
        activeVehicles.keySet().retainAll(vIds);

        // Update data for all active vehicles (Position, Speed, etc.)
        for (VehicleWrapper car : activeVehicles.values()) {
            car.updateData();
        }

        // Update status for all loaded traffic lights (Red/Green state)
        for (TrafficLightWrapper tls : trafficLights.values()) {
            tls.updateData();
        }
    }

    /**
     * Calculates real-time statistics for the dashboard.
     * Updates the running average speed history based on the active filter
     */
    private void calculateStats() {
        double totalSpeed = 0;
        int count = 0;

        // Iterate over all vehicles but only count those matching the filter
        for (VehicleWrapper car : activeVehicles.values()) {
            if (matchesFilter(car)) {
                totalSpeed += car.getSpeed();
                count++;
            }
        }
        if (count == 0) {
            currentAvgSpeed = 0.0;
        } else {
            // Convert m/s to km/h
            currentAvgSpeed = (totalSpeed / count) * 3.6;
        }

        speedHistory.add(currentAvgSpeed);
        // Keep history size fixed
        if (speedHistory.size() > maxHistoryPoints) {
            speedHistory.remove(0);
        }
    }

    private void handleTrafficLightsAuto() {
        long currentTime = System.currentTimeMillis();

        for (TrafficLightWrapper tls : trafficLights.values()) {
            long timeInPhase = currentTime - tls.getLastSwitchTime();

            if (timeInPhase < 5000) continue;

            if (timeInPhase > 60000) {
                tls.nextPhase();
                continue;
            }


            int[] stats = tls.getPhaseAnalysis();
            int waitingOnRed = stats[0];
            int flowingOnGreen = stats[1];

            boolean shouldSwitch = false;

            if (flowingOnGreen > 0) {

                if (timeInPhase > 30000 && waitingOnRed > 0) {
                    shouldSwitch = true;
                }
            } else {

                if (waitingOnRed > 0) {
                    shouldSwitch = true;
                }
            }

            if (shouldSwitch) {
                tls.nextPhase();
            }
        }
    }

    /**
     * adds a new vehicle to sumo safely
     * spawns a thread to avoid blocking the GUI
     */
    public void spawnVehicle(String id, String type, String selection, javafx.scene.paint.Color color, double speed) {
        if (conn == null) return;
        new Thread(() -> spawnVehicleInternal(id, type, selection, color, speed)).start();
    }

    /**
     * Internal method containing the spawn logic.
     * Synchronized to ensure thread safety during batch operations.
     */
    private void spawnVehicleInternal(String id, String type, String selection, javafx.scene.paint.Color color, double startSpeed) {
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

                // safety clamp
                double safeSpeed = startSpeed;
                try {
                    String laneId = fromEdge + "_0";
                    double laneMaxSpeed = (double) conn.do_job_get(Lane.getMaxSpeed(laneId));
                    double typeMaxSpeed = (double) conn.do_job_get(Vehicletype.getMaxSpeed(type));


                    double limit = Math.max(laneMaxSpeed, typeMaxSpeed) * 1.5;

                    if (startSpeed > limit) {
                        safeSpeed = limit;
                        log.info("Speed " + startSpeed + " m/s too high. Clamped to safe limit: " + safeSpeed + " m/s");
                    }
                } catch (Exception e) {
                    if (startSpeed > 14.0) safeSpeed = 14.0;
                }

                String routeId = generateRouteFrom(id, fromEdge);

                if (routeId != null) {
                    double randomPos = 5.0 + (Math.random() * 35.0);


                    conn.do_job_set(Vehicle.add(id, type, routeId, 0, randomPos, safeSpeed, (byte) 0));

                    VehicleWrapper newCar = new VehicleWrapper(id, conn);
                    newCar.setColor(color);

                    activeVehicles.put(id, newCar);

                    if (!id.startsWith("stress_")) {
                        log.info("Spawned " + id + " on " + fromEdge + " with speed " + safeSpeed + " m/s");
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

                spawnVehicleInternal(id, "DEFAULT_VEHTYPE", selectedEdge, color, 5.0);
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }

            Platform.runLater(() -> {
                if (view != null) { view.refresh();}
            });

            log.info("Stress Test Injection Loop Finished.");
        }).start();
    }

    /**
     * helper to generate dynamic route
     */
    private String generateRouteFrom(String vehicleId, String fromEdge) throws Exception {
        if (drivableEdges.size() < 2) return null;

        int attempts = 0;
        int maxAttempts = 5;

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

    /**
     * Central filter logic used by both GUI and Exports.
     */
    public boolean matchesFilter(VehicleWrapper car) {
        //
        if (activeFilter == null || activeFilter.equals("All")) return true;

        javafx.scene.paint.Color c = car.getColor();
        if (c == null) return false;

        double r = c.getRed();
        double g = c.getGreen();
        double b = c.getBlue();


        switch (activeFilter) {
            case "Red Vehicles" -> {
                return r > 0.5 && r > g && r > b;
            }
            case "Blue Vehicles" -> {
                return b > 0.5 && b > r && b > g;
            }
            case "Green Vehicles" -> {
                return g > 0.4 && g > r && g > b;
            }
            case "Yellow Vehicles" -> {
                return r > 0.5 && g > 0.5 && b < 0.6;
            }
            case "White Vehicles" -> {
                return r > 0.7 && g > 0.7 && b > 0.7;
            }
            case "Black Vehicles" -> {
                return r < 0.3 && g < 0.3 && b < 0.3;
            }
        }

        // Speed Filter
        if (activeFilter.startsWith("Fast Vehicles")) {
            return car.getSpeed() * 3.6 >= 40.0;
        }
        if (activeFilter.startsWith("Slow/Stopped")) {
            return car.getSpeed() * 3.6 < 5.0;
        }

        // Position Filter
        double midY = this.mapMinY + (getMapHeight() / 2.0);

        if (activeFilter.startsWith("North Side")) {
            return car.getY() > midY;
        }
        if (activeFilter.startsWith("South Side")) {
            return car.getY() <= midY;
        }

        return true;
    }

    /**
     * Export a CSV report (one file) containing Summary (key,value) and Speed history (index,avg_speed_kmh)
     */
    public void exportCsvReport(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file is null");

        final String exportTimeUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        final long step;
        final double avgSpeedKmh;
        final int vehicleCount;
        final String filter;
        final List<Double> hist;

        // Take a quick snapshot under lock, then write to disk without holding TraCI lock.
        synchronized (traciLock) {
            step = currentStep;
            avgSpeedKmh = currentAvgSpeed;
            int count = 0;
            for (VehicleWrapper car : activeVehicles.values()) {
                if (matchesFilter(car)) {
                    count++;
                }
            }
            vehicleCount = count;
            filter = activeFilter;
            hist = new ArrayList<>(speedHistory);
        }

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // Summary section
            w.write("key,value\n");
            w.write("export_time_utc," + csvEscape(exportTimeUtc) + "\n");
            w.write("sumo_binary," + csvEscape(sumo) + "\n");
            w.write("config_file," + csvEscape(config) + "\n");
            w.write("current_step," + step + "\n");
            w.write("active_filter," + csvEscape(filter) + "\n");
            w.write("active_vehicle_count," + vehicleCount + "\n");
            w.write(String.format(Locale.US, "current_avg_speed_kmh,%.3f\n", avgSpeedKmh));

            // Blank line between sections (Excel-friendly)
            w.write("\n");

            // Speed history section
            w.write("index,avg_speed_kmh\n");
            for (int i = 0; i < hist.size(); i++) {
                w.write(i + "," + String.format(Locale.US, "%.3f", hist.get(i)) + "\n");
            }
        }
    }


    public void exportPdfReport(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file is null");

        final long step;
        final double avgSpeedKmh;
        final int vehicleCount;
        final int tlsCount;
        final String filter;
        final long runtimeSeconds;

        // Snapshot under lock so we don't conflict with TraCI thread
        synchronized (traciLock) {
            step = currentStep;
            avgSpeedKmh = currentAvgSpeed;
            int count = 0;
            for (VehicleWrapper car : activeVehicles.values()) {
                if (matchesFilter(car)) {
                    count++;
                }
            }
            vehicleCount = count;
            tlsCount = trafficLights.size();
            filter = activeFilter;

            long start = simulationStartWallTimeMs;
            long now = System.currentTimeMillis();
            runtimeSeconds = (start > 0) ? Math.max(0, (now - start) / 1000) : 0;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Config: " + config);
        lines.add("Step: " + step);
        lines.add("Runtime (s): " + runtimeSeconds);
        lines.add("Active filter: " + filter);
        lines.add("Active vehicles: " + vehicleCount);
        lines.add("Traffic lights: " + tlsCount);
        lines.add(String.format(Locale.US, "Current avg speed (km/h): %.2f", avgSpeedKmh));

        // PDFBox writing
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float x = 50;
                float y = 780;

                // Title
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(x, y);
                cs.showText("Traffic Simulation Report");
                cs.endText();

                y -= 28;

                // Body
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(x, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            }

            doc.save(file);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String out = s.replace("\"", "\"\"");
        return needQuotes ? ("\"" + out + "\"") : out;
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
    public void play() throws SimulationException {
        if (conn == null) startConnection();
        isPaused = false;
    }

    public void pause() {
        isPaused = true;
    }

    // various getters and setters start here

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

            if(!matchesFilter(car)) continue;

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
     * Calculates density. Fixed to use drivable edges only.
     */
    public int[] getEdgeDensityBins() {
        int[] bins = new int[6];
        Map<String, Integer> counts = new HashMap<>();

        for (VehicleWrapper v : activeVehicles.values()) {
            if(!matchesFilter(v)) continue;
            try {
                String roadId = v.getRoadId();
                if (roadId != null && !roadId.startsWith(":")) {
                    counts.merge(roadId, 1, Integer::sum);
                }
            } catch (Exception e) {}
        }

        for (String edgeId : drivableEdges) {
            int c = counts.getOrDefault(edgeId, 0);
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
    public boolean isPaused() {
        return isPaused;
    }
    public double getCurrentAvgSpeed() { return currentAvgSpeed; }
    public List<Double> getSpeedHistory() { return new ArrayList<>(speedHistory); }
    public List<EdgeWrapper> getMapEdges() { return mapEdges; }
    public Map<String, VehicleWrapper> getActiveVehicles() { return activeVehicles; }
    public Map<String, TrafficLightWrapper> getTrafficLights() { return trafficLights; }
}