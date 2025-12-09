package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private MapPanel mapPanel;

    public MainFrame(SimulationController controller) {

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setTitle("SUMO Traffic Simulation");
        setLayout(new BorderLayout());
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        mapPanel = new MapPanel();
        mapPanel.setController(controller);
        add(mapPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
    }

}