package view;

import controller.SimulationController;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import model.VehicleWrapper;
import java.util.List;
import java.util.Map;

/**
 * Panel for live statistics
 */
public class FxStatisticsPanel extends VBox {

    private SimulationController controller;
    private Label avgSpeedLabel;
    private Label totalVehiclesLabel;

    //speedchart
    private LineChart<Number, Number> lineChart;
    private XYChart.Series<Number, Number> speedSeries;

    // Travel time distribution chart
    private BarChart<String, Number> travelTimeChart;
    private XYChart.Series<String, Number> distributionSeries;

    public FxStatisticsPanel(SimulationController controller) {
        this.controller = controller;
        setSpacing(10);
        setPadding(new Insets(10));

        avgSpeedLabel = new Label("Avg Speed: 0.00 km/h");
        totalVehiclesLabel = new Label("Vehicles: 0");

        setupSpeedChart();
        setupHistogram();

        getChildren().addAll(avgSpeedLabel, totalVehiclesLabel, lineChart, travelTimeChart);
    }

    private void setupSpeedChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Steps");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("km/h");

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Speed History");
        lineChart.setAnimated(false);
        lineChart.setPrefHeight(200);

        speedSeries = new XYChart.Series<>();
        speedSeries.setName("Avg Speed");
        lineChart.getData().add(speedSeries);
    }

    private void setupHistogram() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Travel Time (Seconds)");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Number of Vehicles");

        travelTimeChart = new BarChart<>(xAxis, yAxis);
        travelTimeChart.setTitle("Travel Time Distribution");
        travelTimeChart.setAnimated(false);
        travelTimeChart.setPrefHeight(250);

        distributionSeries = new XYChart.Series<>();
        distributionSeries.setName("Vehicles");
        travelTimeChart.getData().add(distributionSeries);
    }

    public void update() {
        if (controller == null) return;


        double avgSpeed = controller.getCurrentAvgSpeed();
        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        List<Double> history = controller.getSpeedHistory();

        avgSpeedLabel.setText(String.format("Avg Speed: %.2f km/h", avgSpeed));
        totalVehiclesLabel.setText("Vehicles: " + vehicles.size());

        // 2. speed chart update
        speedSeries.getData().clear();
        for (int i = 0; i < history.size(); i++) {
            speedSeries.getData().add(new XYChart.Data<>(i, history.get(i)));
        }

        // 3. travel time distribution update
        updateHistogramData(vehicles);
    }

    private void updateHistogramData(Map<String, VehicleWrapper> vehicles) {
        int[] bins = new int[5]; // 0-30s, 31-60s, 61-120s, 121-300s, >300s
        String[] labels = {"<30s", "1m", "2m", "5m", ">5m"};

        for (VehicleWrapper car : vehicles.values()) {
            long time = car.getTravelTimeSeconds(); //
            if (time <= 30) bins[0]++;
            else if (time <= 60) bins[1]++;
            else if (time <= 120) bins[2]++;
            else if (time <= 300) bins[3]++;
            else bins[4]++;
        }

        distributionSeries.getData().clear();
        for (int i = 0; i < bins.length; i++) {
            distributionSeries.getData().add(new XYChart.Data<>(labels[i], bins[i]));
        }
    }
}