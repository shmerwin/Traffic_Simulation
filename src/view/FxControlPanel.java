package view;

import controller.SimulationController;
import controller.SimulationException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.io.IOException;

/**
 * Represents the control bar containing the buttons to control the simulation (play, pause, speed)
 * Extends HBox which arranges the control elements in a horizontal row.
 */
public class FxControlPanel extends HBox {

    private Button playButton;
    private Button pauseButton;
    private Button exportCsvButton;
    private Button exportPdfButton;
    private Slider speedSlider;
    private SimulationController controller;

    public FxControlPanel(SimulationController controller) {
        this.controller = controller;

        setPadding(new Insets(10));
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);

        playButton = new Button("Play");
        pauseButton = new Button("Pause");
        exportCsvButton = new Button("Export CSV");
        exportPdfButton = new Button("Export PDF");

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
        playButton.setOnAction(e -> {
            try {
                controller.play();
            } catch (SimulationException ex) {
                throw new RuntimeException(ex);
            }
        });
        pauseButton.setOnAction(e -> controller.pause());

        exportCsvButton.setOnAction(e -> exportCsv());
        exportPdfButton.setOnAction(e -> exportPdf());

        /**
         * Listener to observe changes to the slider's value
         * Using the new value to update the simulation speed immediately.
         * Removed 'isValueChanging' check to ensure updates are always sent.
         */
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (controller != null) {
                controller.setSpeedMultiplier(newVal.intValue());
            }
        });

        // Add all control components (children) to our HBox so they are displayed
        getChildren().addAll(playButton, pauseButton, exportCsvButton, exportPdfButton, new Label("Speed:"), speedSlider);
    }

    private void exportCsv() {
        if (controller == null) {
            showAlert(Alert.AlertType.WARNING, "Export not available", "Controller not connected.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        chooser.setInitialFileName("simulation_report.csv");

        File file = chooser.showSaveDialog(getWindowSafe());
        if (file == null) return;

        exportCsvButton.setDisable(true);

        new Thread(() -> {
            try {
                controller.exportCsvReport(file);
                Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION,
                        "Export successful",
                        "CSV report saved to:\n" + file.getAbsolutePath()));
            } catch (IOException ex) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR,
                        "Export failed",
                        "Could not write report:\n" + ex.getMessage()));
            } finally {
                Platform.runLater(() -> exportCsvButton.setDisable(false));
            }
        }, "csv-export").start();
    }

    private void exportPdf() {
        if (controller == null) {
            showAlert(Alert.AlertType.WARNING, "Export not available", "Controller not connected.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
        chooser.setInitialFileName("simulation_report.pdf");

        File file = chooser.showSaveDialog(getWindowSafe());
        if (file == null) return;

        exportPdfButton.setDisable(true);

        new Thread(() -> {
            try {
                controller.exportPdfReport(file);
                Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION,
                        "Export successful",
                        "PDF report saved to:\n" + file.getAbsolutePath()));
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR,
                        "Export failed",
                        "Could not write report:\n" + ex.getMessage()));
            } finally {
                Platform.runLater(() -> exportPdfButton.setDisable(false));
            }
        }, "pdf-export").start();
    }

    private Window getWindowSafe() {
        return (getScene() != null) ? getScene().getWindow() : null;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Window owner = getWindowSafe();
        if (owner != null) alert.initOwner(owner);
        alert.show();
    }
}
