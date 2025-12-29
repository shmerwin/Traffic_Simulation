package view;

import controller.SimulationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;

/**
 * Represents the control bar containing the buttons to control the simulation (play, pause, speed)
 * Extends HBox which arranges the control elements in a horizontal row.
 */
public class FxControlPanel extends HBox {

    private Button playButton;
    private Button pauseButton;
    private Slider speedSlider;
    private SimulationController controller;

    public FxControlPanel(SimulationController controller) {
        this.controller = controller;

        setPadding(new Insets(10));
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);

        playButton = new Button("Play");
        pauseButton = new Button("Pause");

        // slider from 1 to 10 for speed multiplier
        speedSlider = new Slider(1, 10, 1);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1);
        speedSlider.setBlockIncrement(1);
        speedSlider.setPrefWidth(150);

        /**
         * Actions which are executed when the buttons are clicked via lambda expressions
         * Parameter 'e' holds the details of the click event (ActionEvent).
         */
        playButton.setOnAction(e -> controller.play());
        pauseButton.setOnAction(e -> controller.pause());

        /**
         * Listener to observe changes to the slider's value
         * Using the new value to update the simulation speed
         */
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!speedSlider.isValueChanging()) {
                controller.setSpeedMultiplier(newVal.intValue());
            }
        });

        // Add all control components (children) to our HBox so they are displayed
        getChildren().addAll(playButton, pauseButton, new Label("Speed:"), speedSlider);
    }
}