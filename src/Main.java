import org.eclipse.sumo.libtraci.*;

public class Main {
    public static void main(String[] args) {

        try {
            Simulation.preloadLibraries();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Could not load libtraci native library.");
            e.printStackTrace();
            return;
        }

        //heyy david


        try {
            StringVector argsVector = new StringVector();
            argsVector.add("sumo");
            argsVector.add("-c");
            argsVector.add("sumofiles/quickstart.sumocfg");

            Simulation.start(argsVector);
            System.out.println("SUMO successfully started");

            for (int i = 0; i < 1000; i++) {
                Simulation.step();
                StringVector vehicles = Vehicle.getIDList();
                System.out.println("Step " + i + " vehicles: " + vehicles);
            }

            System.out.println("found traffic lights: " + TrafficLight.getIDList().size());
            Simulation.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
