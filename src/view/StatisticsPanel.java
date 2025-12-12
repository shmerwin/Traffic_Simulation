package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * panel that shows live statistics and a speed graph
 */
public class StatisticsPanel extends JPanel {

    private SimulationController controller;

    private JLabel avgSpeedLabel;
    private JLabel totalVehiclesLabel;
    private JLabel totalTrafficLightsLabel;
    private GraphPanel graphPanel;

    public StatisticsPanel() {
        // use border layout with gaps
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // create the top info area
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Live Data"));

        // init labels with default text
        avgSpeedLabel = new JLabel("Avg Speed: 0.00 km/h");
        totalVehiclesLabel = new JLabel("Vehicles: 0");
        totalTrafficLightsLabel = new JLabel("Traffic Lights: 0");

        infoPanel.add(avgSpeedLabel);
        infoPanel.add(totalVehiclesLabel);
        infoPanel.add(totalTrafficLightsLabel);
        add(infoPanel, BorderLayout.NORTH);

        // create the graph area in the center
        graphPanel = new GraphPanel();
        graphPanel.setBorder(BorderFactory.createTitledBorder("Speed History"));
        add(graphPanel, BorderLayout.CENTER);
    }

    /**
     * sets the controller reference
     * @param controller the simulation controller
     */
    public void setController(SimulationController controller) {
        this.controller = controller;
    }

    /**
     * updates the labels and the graph with new data
     * called by mainframe refresh
     */
    public void update() {
        if (controller == null) {
            return;
        }

        // get data from controller
        double avgSpeed = controller.getCurrentAvgSpeed();
        int carCount = controller.getActiveVehicles().size();
        int tlsCount = controller.getTrafficLights().size();
        List<Double> history = controller.getSpeedHistory();

        // update text labels
        avgSpeedLabel.setText(String.format("Avg Speed: %.2f km/h", avgSpeed));
        totalVehiclesLabel.setText("Vehicles: " + carCount);
        totalTrafficLightsLabel.setText("Traffic Lights: " + tlsCount);

        // update graph
        graphPanel.setData(history);
        graphPanel.repaint();
    }

    /**
     * inner helper class to draw the line graph
     */
    private static class GraphPanel extends JPanel {

        private List<Double> data;

        public GraphPanel() {
            // default empty list to avoid null pointer errors
            this.data = new ArrayList<>();
        }

        public void setData(List<Double> data) {
            // create a copy to avoid concurrency issues
            this.data = new ArrayList<>(data);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // if no data, show message
            if (data.isEmpty()) {
                g.drawString("Waiting for data...", 20, 30);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padding = 20;

            // draw background
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);

            // draw axis lines
            g2.setColor(Color.GRAY);
            g2.drawLine(padding, h - padding, w - padding, h - padding); // x axis
            g2.drawLine(padding, h - padding, padding, padding);         // y axis

            // find max value for scaling
            double maxVal = 80.0; // default max speed
            for (Double speed : data) {
                if (speed > maxVal) {
                    maxVal = speed;
                }
            }

            // calculations for scaling
            int drawingWidth = w - (2 * padding);
            int drawingHeight = h - (2 * padding);
            int numPoints = data.size();

            // avoid division by zero
            if (numPoints < 2) {
                return;
            }

            double xScale = (double) drawingWidth / (numPoints - 1);
            double yScale = (double) drawingHeight / maxVal;

            // draw the line graph
            g2.setColor(new Color(0, 120, 215)); // blue color
            g2.setStroke(new BasicStroke(2f));

            for (int i = 0; i < data.size() - 1; i++) {
                double val1 = data.get(i);
                double val2 = data.get(i + 1);

                int x1 = padding + (int) (i * xScale);
                int y1 = h - padding - (int) (val1 * yScale);

                int x2 = padding + (int) ((i + 1) * xScale);
                int y2 = h - padding - (int) (val2 * yScale);

                g2.drawLine(x1, y1, x2, y2);
            }

            // draw max value text
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("Max: " + (int) maxVal + " km/h", padding + 5, padding);
        }
    }
}
