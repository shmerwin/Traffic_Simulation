package wrapper;


import de.tudresden.sumo.cmd.Edge;
import it.polito.appeal.traci.SumoTraciConnection;

public class EdgeWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    public EdgeWrapper(String id, SumoTraciConnection conn) {
    this.id = id;
    this.conn = conn;
    }

    public String getId() {
        return id;
    }
    // returns the number of vehicles on a certain street
    public int getVehicle() throws Exception{
        return (int) conn.do_job_get(Edge.getLastStepVehicleNumber(id));


    }
}