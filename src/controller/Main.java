package controller;

import javafx.application.Application;
import javafx.stage.Stage;
import view.FxMainFrame;

/**
 * Main entry point for the Traffic Simulation
 * Extends Application to handle start and stop of the GUI application
 */
public class Main extends Application {

    private SimulationController controller;

    /**
     * Initializes the simulation controller and the main GUI window
     * @param primaryStage The main window provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        String sumoBin = "sumo";
        String config = "sumofiles/frankfurt/frankfurt_city.sumocfg";

        controller = new SimulationController(sumoBin, config);

        // Initialize the main view, passing the stage for the window and the controller for logic
        new FxMainFrame(primaryStage, controller);
    }


    // This method is called when the application is shutting down (closing the window)
    @Override
    public void stop() throws Exception {
        if (controller != null) {
            controller.stop();
        }
        // Perform standard JavaFX cleanup
        super.stop();
        System.exit(0);
    }

    // Starts the JavaFX application by calling launch(), which then triggers the start() method
    public static void main(String[] args) {
        launch(args);
    }
}