package controller;

import javafx.application.Application;
import javafx.stage.Stage;
import view.FxMainFrame;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javafx.application.Platform;

/**
 * Main entry point for the Traffic Simulation
 * Extends Application to handle start and stop of the GUI application
 */
public class Main extends Application {
    /**
     * Starts the JavaFX application by calling launch(),
     * which then triggers the start() method
     * @param args
     */
    public static void main(String[] args) {
        setupFileLogging();
        launch(args);
    }

    // Global logger instance for this class
    private static final Logger log = Logger.getLogger(Main.class.getName());
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
        log.info("Simulation stopped");
        Platform.exit();
    }
    /**
     * Configures the global logger to write logs to a file named 'simulation.log'.
     * The file is created in the application's working directory.
     */
    private static void setupFileLogging() {
        try {
            // Create a file handler that writes to "simulation.log"
            // Second parameter 'false' means: Overwrite the file on every start.
            FileHandler fileHandler = new FileHandler("simulation.log", false);

            // Use SimpleFormatter to produce readable text logs (instead of XML)
            fileHandler.setFormatter(new SimpleFormatter());

            // Add the handler to the root logger so it captures logs from all classes
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);

            // Set the default logging level (INFO captures normal flow + errors)
            rootLogger.setLevel(Level.INFO);

            log.info("File logging initialized. Logs are written to 'simulation.log'.");

        } catch (IOException e) {
            // Fallback: Print error to console if file logging fails
            log.log(Level.SEVERE, "Failed to setup file logging: " + e.getMessage());
        }
    }
}