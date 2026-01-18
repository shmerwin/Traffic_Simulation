package view;

import controller.SimulationController;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    // VehicleEdgeDensity chart
    private BarChart<String, Number> edgeDensityChart;
    private XYChart.Series<String, Number> edgeDensitySeries;


    public FxStatisticsPanel(SimulationController controller) {
        this.controller = controller;
        setSpacing(10);
        setPadding(new Insets(10));

        avgSpeedLabel = new Label("Avg Speed: 0.00 km/h");
        totalVehiclesLabel = new Label("Vehicles: 0");

        setupSpeedChart();
        setupHistogram();


        // Top section: labels
        HBox topSection = new HBox(20);
        topSection.getChildren().addAll(avgSpeedLabel, totalVehiclesLabel);

        // Middle section: charts (grows to fill space)
        VBox chartsSection = new VBox(10);
        chartsSection.getChildren().addAll(lineChart, travelTimeChart, edgeDensityChart);
        VBox.setVgrow(chartsSection, Priority.ALWAYS);


        // Bottom section: empty space on left, buttons on right
        HBox bottomSection = new HBox();
        bottomSection.setSpacing(10);
        bottomSection.setPadding(new Insets(10, 0, 0, 0));

        VBox spacer = new VBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomSection.getChildren().addAll(spacer);

        // Assemble everything
        getChildren().addAll(topSection, chartsSection, bottomSection);
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
        xAxis.setLabel("Travel Time");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Number of Vehicles");

        travelTimeChart = new BarChart<>(xAxis, yAxis);
        travelTimeChart.setTitle("Travel Time Distribution");
        travelTimeChart.setAnimated(false);
        travelTimeChart.setPrefHeight(250);

        distributionSeries = new XYChart.Series<>();
        distributionSeries.setName("Vehicles");
        travelTimeChart.getData().add(distributionSeries);

        CategoryAxis edgeXAxis = new CategoryAxis();
        edgeXAxis.setLabel("Vehicles per Edge");

        NumberAxis edgeYAxis = new NumberAxis();
        edgeYAxis.setLabel("Number of Edges");

        edgeDensityChart = new BarChart<>(edgeXAxis, edgeYAxis);
        edgeDensityChart.setTitle("Vehicle Density per Edge");
        edgeDensityChart.setAnimated(false);
        edgeDensityChart.setPrefHeight(250);

        edgeDensitySeries = new XYChart.Series<>();
        edgeDensitySeries.setName("Edges");
        edgeDensityChart.getData().add(edgeDensitySeries);

    }

    public void update() {
        if (controller == null) return;


        double avgSpeed = controller.getCurrentAvgSpeed();
        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        List<Double> history = controller.getSpeedHistory();

        avgSpeedLabel.setText(String.format("Avg Speed: %.2f km/h", avgSpeed));
        totalVehiclesLabel.setText("Vehicles: " + vehicles.size());

        // speed chart
        speedSeries.getData().clear();
        for (int i = 0; i < history.size(); i++) {
            speedSeries.getData().add(new XYChart.Data<>(i, history.get(i)));
        }

        // TravelTime chart
        int[] bins = controller.getTravelTimeBins();
        String[] labels = {"<30s", "1m", "2m", "5m", ">5m"};


        distributionSeries.getData().clear();
        for (int i = 0; i < bins.length; i++) {
            distributionSeries.getData().add(new XYChart.Data<>(labels[i], bins[i]));
        }

        // VehicleEdgeDensity chart
        int[] EdgeBins = controller.getEdgeDensityBins();
        String[] EdgeLabels = {"0", "1", "2", "3-5", "6-10", "11+"};

        edgeDensitySeries.getData().clear();
        for (int i = 0; i < EdgeBins.length; i++) {
            edgeDensitySeries.getData().add(new XYChart.Data<>(EdgeLabels[i], EdgeBins[i]));
        }

    }
}