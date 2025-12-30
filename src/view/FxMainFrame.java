package view;

import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Manages the main application window layout
 * Assembles the map canvas, control panels and info tabs
 */
public class FxMainFrame {

    private FxMapCanvas mapCanvas;
    private FxControlPanel controlPanel;

    // the sub-panels for the tabs
    private FxVehiclePanel vehiclePanel;
    private FxTrafficLightPanel trafficLightPanel;
    private FxStatisticsPanel statisticsPanel;
    private TabPane infoTabs;

    /**
     * Configures the primary stage and initializes all sub-panels.
     * @param stage The primary stage (window) passed from Main.java
     * @param controller The simulation controller reference
     */
    public FxMainFrame(Stage stage, SimulationController controller) {
        stage.setTitle("SUMO Traffic Simulation");
        BorderPane root = new BorderPane();

        // MapCanvas (Center)
        mapCanvas = new FxMapCanvas(controller);
        // Canvas wrapped in a StackPane to automatically resize and fill the available space in the center
        StackPane mapHolder = new StackPane(mapCanvas);
        mapHolder.setPadding(new Insets(5));
        mapHolder.setStyle("-fx-background-color: #1e1e1e;");
        // for the canvas to resize dynamically to match the window size
        mapCanvas.widthProperty().bind(mapHolder.widthProperty());
        mapCanvas.heightProperty().bind(mapHolder.heightProperty());
        root.setCenter(mapHolder);
        stage.setFullScreen(true);

        // ControlPanel (Top)
        controlPanel = new FxControlPanel(controller);
        root.setTop(controlPanel);

        // Info Tabs (Right)
        infoTabs = new TabPane();
        infoTabs.setPrefWidth(320);

        // Create and add Vehicle Tab
        vehiclePanel = new FxVehiclePanel(controller);
        Tab vehicleTab = new Tab("Vehicle", vehiclePanel);
        vehicleTab.setClosable(false);

        // Create and add Traffic Light Tab
        trafficLightPanel = new FxTrafficLightPanel(controller);
        Tab lightsTab = new Tab("Traffic Lights", trafficLightPanel);
        lightsTab.setClosable(false);

        // Create and add Statistics Tab
        statisticsPanel = new FxStatisticsPanel(controller);
        Tab statsTab = new Tab("Statistics", statisticsPanel);
        statsTab.setClosable(false);

        infoTabs.getTabs().addAll(vehicleTab, lightsTab, statsTab);
        root.setRight(infoTabs);

        // Connect View to Controller
        controller.setView(this);

        // Create the scene containing the layout (root) with a default size and display the window
        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.show();

        mapCanvas.draw();
    }
    /**
     * Updates all UI components. Called by the controller loop.
     */
    public void refresh() {
        if (mapCanvas != null){
            mapCanvas.draw();
        }
        if (trafficLightPanel != null){
            trafficLightPanel.update();
        }
        if (statisticsPanel != null){
            statisticsPanel.update();
        }
        // vehicle panel does not need constant updates unless opened
    }
}