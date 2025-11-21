import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;

public class Main {

    public static void main(String[] args) throws Exception {
        // arguments for connection
        String sumo = "sumo-gui";
        String config = "sumofiles/fberg.sumocfg";

        SumoTraciConnection conn = new SumoTraciConnection(sumo, config);
        // starts connection
        conn.runServer();
        // runs simulation for i steps
        for (int i = 0; i < 5000; i++) {
            conn.do_timestep();
        // to have not so many reports we only use the method every 100 steps
            if (i % 100 == 0) {
                analyzeTraffic(conn, i);
            }
        }

        conn.close();
        System.out.println("Simulation beendet.");
    }

    private static void analyzeTraffic(SumoTraciConnection conn, int step) throws Exception {
        // uses static list of vehicles instead of instances
        SumoStringList vehicleList = (SumoStringList) conn.do_job_get(Vehicle.getIDList());
        int count = vehicleList.size();

        double totalSpeed = 0;

        for (String carId : vehicleList) {
            double speed = (double) conn.do_job_get(Vehicle.getSpeed(carId));
            totalSpeed += speed;

        }

        double avgSpeed = totalSpeed / count;

        System.out.println("Report for Step " + step + "\n" +
                "Active cars " + count + "\n" +
                "Avergae Speed: " + avgSpeed + "m/s\n\n" );

    }
}