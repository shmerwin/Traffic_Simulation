package model;

import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoPosition2D;
import it.polito.appeal.traci.SumoTraciConnection;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
/**
 * Wrapper class for instanciating Edges/Streets
 */
public class EdgeWrapper {

    private final String id;
    private final SumoTraciConnection conn;
    private List<SumoPosition2D> shapePoints;
    private double width;
    private double length;

    /**
     * Makes an instance of an edge
     * @param id the id of the edge
     * @param conn the connection
     */
    public EdgeWrapper(String id, SumoTraciConnection conn) {
    this.id = id;
    this.conn = conn;
    fetchGeometry();
    }

    /**
     * method for counting the number of vehicles on a street
     * @return returns the number of vehicles on a certain street
     * @throws Exception if data cannot be retrieved
     */
    public int getVehicle() throws Exception{
        return (int) conn.do_job_get(Edge.getLastStepVehicleNumber(id));
    }

    /**
     * Method for getting the shape and everything of the edges to draw them
     */
    private void fetchGeometry() {
        try {
            // gets the width of the edge to draw it later
            this.width = (double) conn.do_job_get(Lane.getWidth(id));
            // important for hotspots
            this.length = (double) conn.do_job_get(Lane.getLength(id));
            SumoGeometry geometry = (SumoGeometry) conn.do_job_get(Lane.getShape(id));

            if (geometry != null && geometry.coords != null) {
                this.shapePoints = geometry.coords;
            } else {
                // if there arent any coords we will make a new list for them
                this.shapePoints = new ArrayList<>();
            }
        } catch (Exception e) {
            this.shapePoints = new ArrayList<>();
        }
    }
    public String getId() { return id; }
    public double getLength() { return length; }

    /**
     * for the drawing of the edges we have this method to get their points
     * @return returns a list with 2d coordinates
     */
    public List<SumoPosition2D> getShapePoints() { return shapePoints; }
    public double getWidth() { return width; }
}