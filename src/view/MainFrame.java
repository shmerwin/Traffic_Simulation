package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;

/**
 * Main window of the application containing the map, controls, and info tabs.
 */
public class MainFrame extends JFrame {

    private MapPanel mapPanel;
    private ControlPanel controlPanel;
    private JTabbedPane infoTabs;

    // the sub-panels for the tabs
    private VehiclePanel vehiclePanel;
    private TrafficLightPanel trafficLightPanel;
    private StatisticsPanel statisticsPanel;

    /**
     * Initializes the main frame and all sub-panels.
     * @param controller The simulation controller reference.
     */
    public MainFrame(SimulationController controller) {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setTitle("SUMO Traffic Simulation");
        setLayout(new BorderLayout());
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // MapPanel (Center)
        mapPanel = new MapPanel();
        mapPanel.setController(controller);
        add(mapPanel, BorderLayout.CENTER);

        // ControlPanel (North)
        controlPanel = new ControlPanel();
        controlPanel.setController(controller);
        add(controlPanel, BorderLayout.NORTH);

        // Info Tabs (East)
        infoTabs = new JTabbedPane();
        infoTabs.setPreferredSize(new Dimension(320, 0));

        // Create and add Vehicle Tab
        vehiclePanel = new VehiclePanel(controller);
        vehiclePanel.setController(controller);
        infoTabs.addTab("Vehicle", vehiclePanel);

        // Create and add Traffic Light Tab
        trafficLightPanel = new TrafficLightPanel();
        trafficLightPanel.setController(controller);
        infoTabs.addTab("Traffic Lights", trafficLightPanel);

        // Create and add Statistics Tab
        statisticsPanel = new StatisticsPanel();
        statisticsPanel.setController(controller);
        infoTabs.addTab("Statistics", statisticsPanel);

        add(infoTabs, BorderLayout.EAST);

        // Connect View to Controller
        controller.setView(this);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Updates all UI components. Called by the controller loop.
     */
    public void refresh() {
        if (mapPanel != null) {
            mapPanel.repaint();
        }
        if (trafficLightPanel != null) {
            trafficLightPanel.update();
        }
        if (statisticsPanel != null) {
            statisticsPanel.update();
        }
        // vehicle panel does not need constant updates unless opened
    }

    public MapPanel getMapPanel() {
        return mapPanel;
    }
}