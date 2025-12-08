package controller;

public class Main {

    public static void main(String[] args) {

        String sumoBin = "sumo";
        String config = "sumofiles/frankfurt_city.sumocfg";

        SimulationController controller = new SimulationController(sumoBin, config);

        controller.runConsoleSimulation();

    }
}