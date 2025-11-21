import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoStringList;
import wrapper.VehicleWrapper;

public class Main {

    public static void main(String[] args) throws Exception {

        String sumoBin = "sumo";
        String config = "sumofiles/quickstart.sumocfg";

        SumoTraciConnection conn = new SumoTraciConnection(sumoBin, config);
        conn.runServer();

        System.out.println("SUMO started");

        for (int i = 0; i < 10; i++) {
            conn.do_timestep();

            SumoStringList vehicleList = (SumoStringList) conn.do_job_get(Vehicle.getIDList());

            if (!vehicleList.isEmpty()) {
                String carId = vehicleList.get(0);
                VehicleWrapper myCar = new VehicleWrapper(carId, conn);

                System.out.println("Step " + i + ": Speed=" + myCar.getSpeed());
            }
        }

        conn.close();
        System.out.println("Simulation beendet.");
    }
}