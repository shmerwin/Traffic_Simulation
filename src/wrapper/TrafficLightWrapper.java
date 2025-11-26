package wrapper;

import de.tudresden.sumo.cmd.Trafficlight;
import it.polito.appeal.traci.SumoTraciConnection;

/**
 * Wrapper for instances of traffic lights
 */
public class TrafficLightWrapper {

    private final String id;
    private final SumoTraciConnection conn;

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
}