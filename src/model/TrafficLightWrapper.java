package model;

import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoStringList;
import de.tudresden.sumo.objects.SumoTLSProgram;
import it.polito.appeal.traci.SumoTraciConnection;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for instances of traffic lights
 */
public class TrafficLightWrapper {

    private final String id;
    private final SumoTraciConnection conn;
    private String currentState = "";
    private int currentPhase = 0;
    private int numPhases = 0;

    /**
     * Represents a single tl regardless of it being a junction or not
     */
    public static class SignalPoint {
        public double x, y;
        public Color color;
        public SignalPoint(double x, double y) { this.x = x; this.y = y; this.color = Color.RED; }
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
     * Loads the traffic light program logic
     * to see how many phases there are
     */
    @SuppressWarnings("unchecked")
    private void Logic() {
            try {
                Object Controller = conn.do_job_get(Trafficlight.getCompleteRedYellowGreenDefinition(id));
                String currentProgId = (String) conn.do_job_get(Trafficlight.getProgram(id));
                Method getProgramsMethod = Controller.getClass().getMethod("getPrograms");
                Map<String, SumoTLSProgram> programs = (Map<String, SumoTLSProgram>) getProgramsMethod.invoke(Controller);
                if (programs != null && programs.containsKey(currentProgId)) {
                    this.numPhases = programs.get(currentProgId).phases.size();
                }
            } catch (Exception e) {
                // if number of phases cant be loaded
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
     * Uses modulo division if number of phases is known
     * safe try-catch fallback to loop to phase 0 on failure
     */
    public void nextPhase() {
        if (numPhases > 0) {
            int next = (currentPhase + 1) % numPhases;
            try {
                conn.do_job_set(Trafficlight.setPhase(id, next));
                updateData();
            } catch (Exception e) {}
        } else {
            try {
                conn.do_job_set(Trafficlight.setPhase(id, currentPhase + 1));
                updateData();
            } catch (Exception e) {
                try {
                    conn.do_job_set(Trafficlight.setPhase(id, 0));
                    updateData();
                } catch (Exception ex) {}
            }
        }
    }

    private Color parseStateToColor(char state) {
        switch (state) {
            case 'r': case 'R': return Color.RED;
            case 'y': case 'Y': return Color.YELLOW;
            case 'g': case 'G': return Color.GREEN;
            case 'u': case 'U': return Color.ORANGE;
            default: return Color.DARK_GRAY;
        }
    }
    public String getId() { return id; }
    public String getCurrentState() { return currentState; }
    public int getCurrentPhase() { return currentPhase; }
    public int getNumPhases() { return numPhases; }
    public List<SignalPoint> getSignalPoints() { return signalPoints; }
    }

