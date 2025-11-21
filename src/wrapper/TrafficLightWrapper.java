package wrapper;

import de.tudresden.sumo.cmd.Trafficlight;
import it.polito.appeal.traci.SumoTraciConnection;

public class TrafficLightWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    public TrafficLightWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
    }

    public String getId() {
        return id;
    }
    // shows current state of light
    public String getState() throws Exception {
        return (String) conn.do_job_get(Trafficlight.getRedYellowGreenState(id));
    }
    // phase is the order of states
    public void getPhase(int index) throws Exception {
        conn.do_job_set(Trafficlight.getPhase(id));
    }
}