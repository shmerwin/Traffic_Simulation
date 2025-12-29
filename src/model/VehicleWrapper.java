package model;

import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoColor;
import de.tudresden.sumo.objects.SumoPosition2D;
import it.polito.appeal.traci.SumoTraciConnection;

import javafx.scene.paint.Color;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper for a vehicle in the sumo connection
 * Uses traas commands for getting our own
 */
public class VehicleWrapper {

    private static final Logger log = Logger.getLogger(VehicleWrapper.class.getName());

    private final String id;
    private final SumoTraciConnection conn;
    private double x, y, angle, speed;
    private double length, width;
    private Color color;


    /**
     * Instanciates a new VehicleWrapper object
     * @param id The id of the vehicle in sumo
     * @param conn The connection to sumo
     */
    public VehicleWrapper(String id, SumoTraciConnection conn) {
        this.id = id;
        this.conn = conn;
        fetchStaticData();
    }

    /**
     * Methode to retrieve and assign color,length and width of the vehicle from sumo
     * in case of communication failure default values are set.
     */
    private void fetchStaticData() {
        try {
            SumoColor sc = (SumoColor) conn.do_job_get(Vehicle.getColor(id));
            int r = RGBLimiter(sc.r); int g = RGBLimiter(sc.g); int b = RGBLimiter(sc.b);
            this.color = Color.rgb(r, g, b);
            this.length = (double) conn.do_job_get(Vehicle.getLength(id));
            this.width = (double) conn.do_job_get(Vehicle.getWidth(id));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to fetch static data for vehicle " + id, e);
            this.color = Color.YELLOW;
            this.length = 5.0; this.width = 2.0;
        }
    }

    /**
     * Method to limit RGB values to 255
     * @param val value for RGB
     * @return an RGB value that does not exceed 255
     */
    private int RGBLimiter(int val) { return Math.max(0, Math.min(255, val)); }

    /**
     * Method to retrieve the position and speed of the vehicle
     */
    public void updateData() {
        try {
            SumoPosition2D pos = (SumoPosition2D) conn.do_job_get(Vehicle.getPosition(id));
            this.x = pos.x; this.y = pos.y;
            this.angle = (double) conn.do_job_get(Vehicle.getAngle(id));
            this.speed = (double) conn.do_job_get(Vehicle.getSpeed(id));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to update data for vehicle " + id, e);
        }
    }

    public String getId() { return id;}
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public double getSpeed() { return speed; }
    public double getLength() { return length; }
    public double getWidth() { return width; }
    public Color getColor() { return color; }

    /**
     * Method for seeing on which road which car is
     * @return the edge of the specific car
     * @throws Exception if data cannot be retrieved
     */
    public String getRoadId() throws Exception {
        // Warning: This method uses the shared connection directly and might require external synchronization
        // if called outside the main simulation loop.
        return (String) conn.do_job_get(Vehicle.getRoadID(id));
    }

}