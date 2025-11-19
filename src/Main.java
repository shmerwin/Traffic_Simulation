import org.eclipse.sumo.libtraci.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {


        try {
            // global path for sumo installation
            String sumoHome = System.getenv("SUMO_HOME");

            if (sumoHome == null || sumoHome.isEmpty()) {
                System.err.println("SUMO_HOME environment variable is not set");
                System.err.println("Please set the environment variable SUMO_HOME in windows settings");

                sumoHome = "C:\\Program Files (x86)\\Eclipse\\Sumo";
            }


            String libPath = sumoHome + File.separator + "bin" + File.separator + "libtracijni.dll";

            File libFile = new File(libPath);
            if (!libFile.exists()) {

                libPath = sumoHome + File.separator + "bin" + File.separator + "libtracijni.so";
            }

            System.out.println("Loading Sumo Library from: " + libPath);


            System.load(libPath);

        } catch (UnsatisfiedLinkError e) {
            System.err.println("Couldn't load dll");
            e.printStackTrace();
            return;
        }


        try {
            Simulation.preloadLibraries();

            StringVector argsVector = new StringVector();
            argsVector.add("sumo");
            argsVector.add("-c");

            argsVector.add("sumofiles/quickstart.sumocfg");

            Simulation.start(argsVector);
            System.out.println("SUMO successfully started");


            for (int i = 0; i < 1000; i++) { //1000 statt 5
                Simulation.step();
                StringVector vehicles = Vehicle.getIDList();
                System.out.println("Step " + i + " vehicles: " + vehicles);
            }

            // prints out traffic lights
            System.out.println("found traffic lights: " + TrafficLight.getIDList().size());

            Simulation.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}