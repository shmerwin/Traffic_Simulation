package model;

import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoStringList;
import de.tudresden.sumo.objects.SumoTLSProgram;
import it.polito.appeal.traci.SumoTraciConnection;

import javafx.scene.paint.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper for instances of traffic lights
 */
public class TrafficLightWrapper {

    private static final Logger log = Logger.getLogger(TrafficLightWrapper.class.getName());

    private final String id;
    private final SumoTraciConnection conn;
    private String currentState = "";
    private int currentPhase = 0;
    private int numPhases = 0;

    private final List<String> controlledLanes = new ArrayList<>();
    private long lastSwitchTime = 0;

    /**
     * Represents a single tl regardless of it being a junction or not
     */
    public static class SignalPoint {
        public double x, y;
        public Color color;
        public SignalPoint(double x, double y) { this.x = x; this.y = y; this.color = Color.RED;}
    }
    // List of every light being controlled by the tls
    private final List<SignalPoint> signalPoints = new ArrayList<>();

    /**
     * Instanciates a new trafficlight object
     * @param id The id of the traffic light
     * @param conn The connection to sumo
     */
    public TrafficLightWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
        loadLanes();
        Geometry();
        Logic();
    }

    /**
     * Loads the position/geometry of all signals
     * with the end points of the lanes
     */
    private void Geometry() {
        try {
            SumoStringList lanes = (SumoStringList) conn.do_job_get(Trafficlight.getControlledLanes(id));
            for (String laneId : lanes) {
                SumoGeometry shape = (SumoGeometry) conn.do_job_get(Lane.getShape(laneId));
                if (shape.coords != null && !shape.coords.isEmpty()) {
                    var lastPoint = shape.coords.get(shape.coords.size() - 1);
                    signalPoints.add(new SignalPoint(lastPoint.x, lastPoint.y));
                }
            }
        } catch (Exception e) {
        }
    }
    /**
     * Loads the traffic light program logic using Reflection
     * Tries both Getter-Method and Direct-Field access to support different TraaS versions
     */
    @SuppressWarnings("unchecked")
    private void Logic() {
        try {
            Object controllerObj = conn.do_job_get(Trafficlight.getCompleteRedYellowGreenDefinition(id));
            String currentProgId = (String) conn.do_job_get(Trafficlight.getProgram(id));

            if (controllerObj == null) return;

            Map<String, SumoTLSProgram> programs = null;
            Class<?> classx = controllerObj.getClass();

            // Try Getter-Method getPrograms
            try {
                Method getProgramsMethod = classx.getMethod("getPrograms");
                programs = (Map<String, SumoTLSProgram>) getProgramsMethod.invoke(controllerObj);
            } catch (NoSuchMethodException e) {
                // method not found
            }

            // Try direct field access "programs"
            if (programs == null) {
                try {
                    java.lang.reflect.Field programsField = classx.getField("programs");
                    programs = (Map<String, SumoTLSProgram>) programsField.get(controllerObj);
                } catch (NoSuchFieldException e) {
                    log.warning("TLS " + id + ": Logic detection failed (unknown TraaS structure).");
                }
            }

            // check if programs were found and assign phase count
            if (programs != null) {
                if (programs.containsKey(currentProgId)) {
                    this.numPhases = programs.get(currentProgId).phases.size();
                    log.info("TLS " + id + " loaded with " + numPhases + " phases.");
                } else if (!programs.isEmpty()) {
                    // fallback to first available program if ID mismatch
                    this.numPhases = programs.values().iterator().next().phases.size();
                }
            }

        } catch (Exception e) {
            log.log(Level.WARNING, "Error analyzing TLS logic for " + id, e);
            this.numPhases = 0;
        }
    }

    /**
     * Updates the current state and phase from SUMO and applies the corresponding
     * color to all signal points
     */
    public void updateData() {
        try {
            this.currentState = (String) conn.do_job_get(Trafficlight.getRedYellowGreenState(id));
            this.currentPhase = (int) conn.do_job_get(Trafficlight.getPhase(id));
            for (int i = 0; i < signalPoints.size() && i < currentState.length(); i++) {
                char stateChar = currentState.charAt(i);
                signalPoints.get(i).color = parseStateToColor(stateChar);
            }
        } catch (Exception e) {}
    }
    /**
     * Switches the traffic light to the next phase.
     * Uses modulo arithmetic to safely cycle through phases.
     * Falls back to phase 0 if the total phase count is unknown.
     */
    public void nextPhase() {
        if (numPhases <= 0) {
            Logic();
        }
        if (numPhases <= 0) {
            return;
        }
        try {
            int nextIndex = (currentPhase + 1) % numPhases;
            conn.do_job_set(Trafficlight.setPhase(id, nextIndex));
            updateData();
            this.lastSwitchTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error switching phase for TLS " + id, e);
        }
    }

    /**
     * Sets the duration of the current traffic light phase.
     * @param seconds is the new duration of the phase.
     */
    public void setPhaseDuration(double seconds) {
        try {
            conn.do_job_set(Trafficlight.setPhaseDuration(id, seconds));


            updateData();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error setting phase duration for traffic light " + id, e);
        }
    }

    private Color parseStateToColor(char state) {
        switch (state) {
            case 'r': case 'R': return Color.RED;
            case 'y': case 'Y': return Color.YELLOW;
            case 'g': case 'G': return Color.GREEN;
            case 'u': case 'U': return Color.ORANGE;
            default: return Color.DARKGRAY;
        }
    }

    private void loadLanes() {
        try {
            SumoStringList lanes = (SumoStringList) conn.do_job_get(Trafficlight.getControlledLanes(id));
            controlledLanes.addAll(lanes);
        } catch (Exception e) {
            log.warning("Could not load lanes for TLS " + id);
        }
    }

    /**
     * Counts how many vehicles are waiting in a lane with a tls
     * @return the number of waiting vehicles
     */
    public int getWaitingVehicleCount(){
        int count = 0;
        try {
            for (String laneId : controlledLanes) {
                int waiting = (int) conn.do_job_get(Lane.getLastStepHaltingNumber(laneId));
                count += waiting;
            }
        } catch (Exception e) {
        }
        return count;
    }

    /**
     * Analyzes traffic to distinguish between stuck cars on red vs. moving cars on green.
     * If a green lane is mostly jammed, we treat it as empty
     * to force a switch and stop feeding the jam.
     * @return int array: [0] = waiting on RED, [1] = flow on GREEN
     */
    public int[] getPhaseAnalysis() {
        int waitingOnRed = 0;
        int movingOnGreen = 0;

        try {
            int limit = Math.min(controlledLanes.size(), currentState.length());

            for (int i = 0; i < limit; i++) {
                String laneId = controlledLanes.get(i);
                char state = currentState.charAt(i);

                boolean isRed = (state == 'r' || state == 'R');
                boolean isGreen = (state == 'g' || state == 'G');

                if (isRed) {

                    waitingOnRed += (int) conn.do_job_get(Lane.getLastStepHaltingNumber(laneId));
                } else if (isGreen) {
                    int total = (int) conn.do_job_get(Lane.getLastStepVehicleNumber(laneId));
                    int halting = (int) conn.do_job_get(Lane.getLastStepHaltingNumber(laneId));

                    if (total > 0 && halting > (total * 0.5)) {

                    } else {

                        movingOnGreen += Math.max(0, total - halting);
                    }
                }
            }
        } catch (Exception e) {
            return new int[]{0, 0};
        }
        return new int[]{waitingOnRed, movingOnGreen};
    }


    public String getId() { return id; }
    public String getCurrentState() { return currentState; }
    public int getCurrentPhase() { return currentPhase; }
    public int getNumPhases() { return numPhases; }
    public List<SignalPoint> getSignalPoints() { return signalPoints; }
    public long getLastSwitchTime() { return lastSwitchTime; }
    }

