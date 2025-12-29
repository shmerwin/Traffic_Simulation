package view;

import controller.SimulationController;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import java.util.List;

/**
 * panel that shows live statistics and a speed graph
 */
public class FxStatisticsPanel extends VBox {

    private SimulationController controller;
    private Label avgSpeedLabel;
    private Label totalVehiclesLabel;
    private Label totalTrafficLightsLabel;
    private LineChart<Number, Number> lineChart;
    private XYChart.Series<Number, Number> series;

    public FxStatisticsPanel(SimulationController controller) {
        this.controller = controller;
        setSpacing(10);
        setPadding(new Insets(10));

        // Labels with default text
        avgSpeedLabel = new Label("Avg Speed: 0.00 km/h");
        totalVehiclesLabel = new Label("Vehicles: 0");
        totalTrafficLightsLabel = new Label("Traffic Lights: 0");

        // Chart
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Steps");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Speed (km/h)");

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Speed History");
        lineChart.setAnimated(false);

        series = new XYChart.Series<>();
        series.setName("Avg Speed");
        lineChart.getData().add(series);

        getChildren().addAll(avgSpeedLabel, totalVehiclesLabel, totalTrafficLightsLabel, lineChart);
    }

    /**
     * updates the labels and the graph with new data
     * called by mainframe refresh
     */
    public void update() {
        if (controller == null) return;

        // get data from controller
        double avgSpeed = controller.getCurrentAvgSpeed();
        int carCount = controller.getActiveVehicles().size();
        int tlsCount = controller.getTrafficLights().size();
        List<Double> history = controller.getSpeedHistory();

        // update text labels
        avgSpeedLabel.setText(String.format("Avg Speed: %.2f km/h", avgSpeed));
        totalVehiclesLabel.setText("Vehicles: " + carCount);
        totalTrafficLightsLabel.setText("Traffic Lights: " + tlsCount);

        // update chart
        series.getData().clear();
        for (int i = 0; i < history.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, history.get(i)));
        }
    }
}