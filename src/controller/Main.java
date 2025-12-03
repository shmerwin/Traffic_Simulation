package controller;

import controller.SimulationController;
import com.formdev.flatlaf.FlatLightLaf;

public class Main {

    /**
     * Starts the application
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // FlatLightLaf.setup();

        // arguments for connection
        String sumo = "sumo";
        String config = "sumofiles/frankfurt_city.sumocfg";

        SimulationController controller = new SimulationController(sumo, config);

        controller.runLoop();
    }
}