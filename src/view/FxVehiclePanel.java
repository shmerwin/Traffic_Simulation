package view;

import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.util.List;

import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;

public class FxVehiclePanel extends VBox {

    private SimulationController controller;
    private TextField idField;
    private ComboBox<String> typeSelector;
    private ComboBox<String> routeSelector;
    private ColorPicker colorPicker;
    private ComboBox<String> filterSelector;
    private Spinner<Double> speedSpinner;

    public FxVehiclePanel(SimulationController controller) {
        this.controller = controller;
        setSpacing(10);
        setPadding(new Insets(10));
        setAlignment(Pos.TOP_CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        grid.add(new Label("Color:"), 0, 3);
        colorPicker = new ColorPicker(Color.YELLOW);
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        grid.add(colorPicker, 1, 3);

        grid.add(new Label("Vehicle ID:"), 0, 0);
        idField = new TextField("new_car");
        grid.add(idField, 1, 0);

        grid.add(new Label("Type:"), 0, 1);
        typeSelector = new ComboBox<>();
        typeSelector.setMaxWidth(Double.MAX_VALUE);
        grid.add(typeSelector, 1, 1);

        grid.add(new Label("Route:"), 0, 2);
        routeSelector = new ComboBox<>();
        routeSelector.setMaxWidth(Double.MAX_VALUE);
        grid.add(routeSelector, 1, 2);

        grid.add(new Label("Speed (km/h):"), 0, 4);
        speedSpinner = new Spinner<>(0.0, 200.0, 50.0, 5.0);
        speedSpinner.setEditable(true);
        speedSpinner.setMaxWidth(Double.MAX_VALUE);
        grid.add(speedSpinner, 1, 4);

        TitledPane pane1 = new TitledPane("Spawn Vehicle", grid);
        pane1.setCollapsible(false);

        Label infoLabel = new Label("1. Press Play\n2. Load Data\n3. Spawn");
        infoLabel.setStyle("-fx-text-alignment: center; -fx-text-fill: gray;");

        Button loadBtn = new Button("Load Data");
        loadBtn.setMaxWidth(Double.MAX_VALUE);
        loadBtn.setOnAction(e -> updateLists());

        Button spawnBtn = new Button("Spawn Single");
        spawnBtn.setMaxWidth(Double.MAX_VALUE);
        spawnBtn.setOnAction(e -> spawnVehicle());

        Button stressBtn = new Button("Spawn 50 Cars");
        stressBtn.setMaxWidth(Double.MAX_VALUE);
        stressBtn.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        stressBtn.setOnAction(e -> {
            if (this.controller != null) {
                String selectedRoute = routeSelector.getValue();
                Color selectedColor = colorPicker.getValue();

                if (selectedRoute == null || selectedRoute.startsWith("No routes")) {
                    this.controller.startStressTest("Random Route", selectedColor);
                } else {
                    this.controller.startStressTest(selectedRoute, selectedColor);
                }
            }
        });

        getChildren().addAll(pane1, infoLabel, loadBtn, spawnBtn, stressBtn);

        grid.add(new Label("Filter:"), 0, 5);

        filterSelector = new ComboBox<>();

        filterSelector.getItems().addAll(
                "All",
                "Red Vehicles",
                "Blue Vehicles",
                "Green Vehicles",
                "Yellow Vehicles",
                "White Vehicles",
                "Black Vehicles",
                "Fast Vehicles (> 40 km/h)",
                "Slow/Stopped (< 5 km/h)",
                "North Side (Top Half)",
                "South Side (Bottom Half)"
        );

        filterSelector.setValue("All");
        filterSelector.setMaxWidth(Double.MAX_VALUE);

        filterSelector.setOnAction(e -> {
            if (controller != null) controller.setActiveFilter(filterSelector.getValue());
        });

        grid.add(filterSelector, 1, 5);
    }

    public void setController(SimulationController controller) {
        this.controller = controller;
    }

    private void updateLists() {
        if (controller == null) return;

        typeSelector.getItems().clear();
        routeSelector.getItems().clear();

        List<String> types = controller.getVehicleTypeList();
        List<String> routes = controller.getRouteList();

        typeSelector.getItems().addAll(types);

        if (routes.isEmpty()) {
            routeSelector.getItems().add("No routes found");
        } else {
            routeSelector.getItems().addAll(routes);
        }

        if (!routes.isEmpty()) routeSelector.getSelectionModel().selectFirst();
        if (!types.isEmpty()) typeSelector.getSelectionModel().selectFirst();
    }

    private void spawnVehicle() {
        if (controller == null) return;

        String id = idField.getText();
        String type = typeSelector.getValue();
        String route = routeSelector.getValue();

        javafx.scene.paint.Color selectedColor = colorPicker.getValue();

        double speedKmh = speedSpinner.getValue();

        if (speedKmh < 30.0) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Geschwindigkeitswarnung");
            alert.setHeaderText("Zu langsam!");
            alert.setContentText("Die Geschwindigkeit muss mindestens 30 km/h betragen.");
            alert.showAndWait();
            return;
        }

        if (speedKmh > 50.0) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Geschwindigkeitswarnung");
            alert.setHeaderText("Zu schnell!");
            alert.setContentText("Die Geschwindigkeit darf maximal 50 km/h betragen.");
            alert.showAndWait();
            return;
        }

        if (route == null || route.startsWith("No routes")) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Invalid Route");
            alert.setHeaderText(null);
            alert.setContentText("Please choose a valid route.");
            alert.showAndWait();
            return;
        }

        if (id != null && !id.isEmpty() && type != null) {
            double speedMs = speedKmh / 3.6;
            controller.spawnVehicle(id, type, route, selectedColor, speedMs);
            idField.setText(id + "_x");
        }
    }
}