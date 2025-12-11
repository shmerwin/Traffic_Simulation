package controller;

import com.formdev.flatlaf.FlatLightLaf;
import view.MainFrame;

public class Main {

    public static void main(String[] args) {

        FlatLightLaf.setup();

        String sumoBin = "sumo";
        String config = "sumofiles/frankfurt/frankfurt_city.sumocfg";

        SimulationController controller = new SimulationController(sumoBin, config);

        //controller.runConsoleSimulation();
        new MainFrame(controller);
    }
}