package wrapper;


import de.tudresden.sumo.cmd.Edge;
import it.polito.appeal.traci.SumoTraciConnection;

/**
 * Wrapper class for instanciating Edges/Streets
 */
public class EdgeWrapper {

    private final String id;
    private final SumoTraciConnection conn;

    /**
     * Makes an instance of an edge
     * @param id the id of the edge
     * @param conn the connection
     */
    public EdgeWrapper(String id, SumoTraciConnection conn) {
    this.id = id;
    this.conn = conn;
    }

    public String getId() {
        return id;
    }

    /**
     * method for counting the number of vehicles on a street
     * @return returns the number of vehicles on a certain street
     * @throws Exception if data cannot be retrieved
     */
    public int getVehicle() throws Exception{
        return (int) conn.do_job_get(Edge.getLastStepVehicleNumber(id));
    }
}