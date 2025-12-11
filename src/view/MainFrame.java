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

        // Info Tabs (East comes later)
        infoTabs = new JTabbedPane();
        infoTabs.setPreferredSize(new Dimension(320, 0));

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
    }

    public MapPanel getMapPanel() {
        return mapPanel;
    }
}
