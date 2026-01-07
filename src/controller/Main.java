package controller;

import javafx.application.Application;
import javafx.stage.Stage;
import view.FxMainFrame;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * Main entry point for the Traffic Simulation
 * Extends Application to handle start and stop of the GUI application
 */
public class Main extends Application {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    private SimulationController controller;

    /**
     * Initializes the simulation controller and the main GUI window
     * @param primaryStage The main window provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        String sumoBin = "sumo";
        String config = "sumofiles/langen/langen.sumocfg";

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
        log.info("Simulation stopped");
        Platform.exit();

    }

    // Starts the JavaFX application by calling launch(), which then triggers the start() method
    public static void main(String[] args) {
        launch(args);
    }
}