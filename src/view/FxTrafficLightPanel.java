package view;

import controller.SimulationController;
import model.TrafficLightWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import java.util.Map;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;


/**
 * panel to inspect and control traffic lights manually
 * allows switching phases for specific junctions
 */
public class FxTrafficLightPanel extends VBox {

    private SimulationController controller;
    private ComboBox<String> tlsSelector;
    private Label statusLabel;
    private Label phaseLabel;
    private Button nextPhaseButton;
    private TextField phaseDurationField;
    private Button setDurationButton;


    public FxTrafficLightPanel(SimulationController controller) {
        this.controller = controller;
        setSpacing(10);
        setPadding(new Insets(10));

        // top panel for selection
        VBox topBox = new VBox(5);
        tlsSelector = new ComboBox<>();
        tlsSelector.setMaxWidth(Double.MAX_VALUE);
        tlsSelector.setOnAction(e -> updateInfo());
        topBox.getChildren().add(tlsSelector);
        TitledPane pane1 = new TitledPane("Select Junction", topBox);
        pane1.setCollapsible(false);

        // center panel for information
        VBox centerBox = new VBox(5);
        statusLabel = new Label("State: -");
        phaseLabel = new Label("Phase: -");
        centerBox.getChildren().addAll(statusLabel, phaseLabel);
        TitledPane pane2 = new TitledPane("Status", centerBox);
        pane2.setCollapsible(false);

        // bottom panel for actions
        VBox bottomBox = new VBox(5);
        nextPhaseButton = new Button("Next Phase");
        nextPhaseButton.setMaxWidth(Double.MAX_VALUE);
        nextPhaseButton.setDisable(true);
        nextPhaseButton.setOnAction(e -> switchPhase());
        bottomBox.getChildren().add(nextPhaseButton);
        TitledPane pane3 = new TitledPane("Control", bottomBox);
        pane3.setCollapsible(false);

        getChildren().addAll(pane1, pane2, pane3);

        phaseDurationField = new TextField("10");
        phaseDurationField.setPromptText("seconds");

        setDurationButton = new Button("Set Phase Duration (s)");
        setDurationButton.setMaxWidth(Double.MAX_VALUE);
        setDurationButton.setOnAction(e -> setPhaseDuration());

        bottomBox.getChildren().addAll(phaseDurationField, setDurationButton);

    }

    /**
     * updates the panel content
     * refreshes the combobox list and the status labels
     */
    public void update() {
        if (controller == null) {
            return;
        }

        Map<String, TrafficLightWrapper> lights = controller.getTrafficLights();

        // check if we need to update the list (simple check by size)
        if (lights.size() != tlsSelector.getItems().size()) {
            String selected = tlsSelector.getValue();
            tlsSelector.getItems().clear();
            tlsSelector.getItems().addAll(lights.keySet());

            // restore selection if possible
            if (selected != null && lights.containsKey(selected)) {
                tlsSelector.setValue(selected);
            }
        }
        updateInfo();
    }

    private void updateInfo() {
        if (controller == null) {
            return;
        }

        String selectedId = tlsSelector.getValue();

        // case: nothing selected
        if (selectedId == null) {
            statusLabel.setText("State: -");
            phaseLabel.setText("Phase: -");
            nextPhaseButton.setDisable(true);
            return;
        }

        // case: something selected
        TrafficLightWrapper tls = controller.getTrafficLights().get(selectedId);
        if (tls != null) {
            statusLabel.setText("State: " + tls.getCurrentState());

            if (tls.getNumPhases() > 0) {
                phaseLabel.setText("Phase Index: " + tls.getCurrentPhase());
            } else {
                phaseLabel.setText("Phase: " + tls.getCurrentPhase());
            }

            nextPhaseButton.setDisable(false);
        }
    }

    /**
     * helper to switch the traffic light phase
     */
    private void switchPhase() {
        if (controller == null) {
            return;
        }

        String selectedId = tlsSelector.getValue();
        if (selectedId != null) {
            TrafficLightWrapper tls = controller.getTrafficLights().get(selectedId);
            if (tls != null) {
                tls.nextPhase();
                updateInfo();
            }
        }
    }

    /**
     * Read the duration from the text field and apply it to the currently selected traffic light
     */
    private void setPhaseDuration() {
        if (controller == null) return;

        String selectedId = tlsSelector.getValue();
        if (selectedId == null) return;

        try {
            double seconds = Double.parseDouble(phaseDurationField.getText().trim());
            if (seconds <= 0) throw new NumberFormatException();

            // Recommended: call through controller (thread-safe)
            controller.setTrafficLightPhaseDuration(selectedId, seconds);
            updateInfo();

        } catch (NumberFormatException ex) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText("Invalid duration");
            a.setContentText("Please enter a positive number (seconds).");
            a.showAndWait();
        }
    }

}