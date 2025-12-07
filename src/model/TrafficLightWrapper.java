package model;

import de.tudresden.sumo.cmd.Junction;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoPosition2D;
import de.tudresden.sumo.objects.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;

/**
 * Wrapper for instances of traffic lights
 */
public class TrafficLightWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    // Cache für Position
    private SumoPosition2D cachedPosition = null;
    private boolean positionSearchFailed = false;


    /**
     * Instanciates a new trafficlight object
     * @param id The id of the traffic light
     * @param conn The connection to sumo
     */
    public TrafficLightWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
    }

    public String getId() {
        return id;
    }

    /**
     * shows current state of light
     * @return returns the state of the trafficlight
     * @throws Exception if state cannot be retrieved
     */
    public String getState() throws Exception {
        return (String) conn.do_job_get(Trafficlight.getRedYellowGreenState(id));
    }

    /**
     * phase is the order of states
     * @return the phase
     * @throws Exception if data cannot be retrieved
     */
    public int getPhase() throws Exception {
        return (int) conn.do_job_get(Trafficlight.getPhase(id));
    }

   /**
    * set the phase of the traffic light
    */

    public void setPhase(int phase) throws Exception {
        conn.do_job_set(Trafficlight.setPhase(id, phase));
    }

    /**
     * methode to determine the position of the trafficLight in Sumo
     */
    public SumoPosition2D getPosition() {
        if (cachedPosition != null) return cachedPosition;
        if (positionSearchFailed) return null;

        try {

            // strategy 1: Junction position ( but not effective for clusters)
            if (!id.startsWith("cluster") && !id.startsWith("joinedS")) {
                try {
                    cachedPosition = (SumoPosition2D) conn.do_job_get(Junction.getPosition(id));
                    return cachedPosition;
                } catch (Exception e) { /* Fallback */ }
            }

            // strategy 2: Fallback via controlled lanes and lane shape:
            SumoStringList lanes = (SumoStringList) conn.do_job_get(Trafficlight.getControlledLanes(id));
            if (lanes != null && !lanes.isEmpty()) {
                String firstLaneId = lanes.get(0);
                SumoGeometry geo = (SumoGeometry) conn.do_job_get(Lane.getShape(firstLaneId));
                if (geo != null && !geo.coords.isEmpty()) {
                    cachedPosition = geo.coords.get(geo.coords.size() - 1);
                    return cachedPosition;
                }
            }
        } catch (Exception e) {

        }
        // if both fail,
        positionSearchFailed = true;
        return null;
    }
}