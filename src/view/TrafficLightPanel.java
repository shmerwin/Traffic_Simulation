package view;

import controller.SimulationController;
import model.TrafficLightWrapper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * panel to inspect and control traffic lights manually
 * allows switching phases for specific junctions
 */
public class TrafficLightPanel extends JPanel {

    private SimulationController controller;

    private JComboBox<String> tlsSelector;
    private JLabel statusLabel;
    private JLabel phaseLabel;
    private JButton nextPhaseButton;

    public TrafficLightPanel() {
        setLayout(new BorderLayout());

        // top panel for selection
        JPanel topPanel = new JPanel(new GridLayout(0, 1));
        topPanel.setBorder(BorderFactory.createTitledBorder("Select Junction"));

        tlsSelector = new JComboBox<>();
        tlsSelector.addActionListener(e -> updateInfo());
        topPanel.add(tlsSelector);

        add(topPanel, BorderLayout.NORTH);

        // center panel for information
        JPanel centerPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Status"));

        statusLabel = new JLabel("State: -");
        phaseLabel = new JLabel("Phase: -");

        centerPanel.add(statusLabel);
        centerPanel.add(phaseLabel);
        add(centerPanel, BorderLayout.CENTER);

        // bottom panel for actions
        JPanel bottomPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Control"));

        nextPhaseButton = new JButton("Next Phase");
        nextPhaseButton.setEnabled(false);

        // button action
        nextPhaseButton.addActionListener(e -> {
            switchPhase();
        });

        bottomPanel.add(nextPhaseButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * sets the controller reference
     * @param controller the simulation controller
     */
    public void setController(SimulationController controller) {
        this.controller = controller;
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
        if (lights.size() != tlsSelector.getItemCount()) {
            String selected = (String) tlsSelector.getSelectedItem();
            tlsSelector.removeAllItems();

            for (String id : lights.keySet()) {
                tlsSelector.addItem(id);
            }

            // restore selection if possible
            if (selected != null && lights.containsKey(selected)) {
                tlsSelector.setSelectedItem(selected);
            }
        }

        updateInfo();
    }

    /**
     * helper to switch the traffic light phase
     */
    private void switchPhase() {
        if (controller == null) {
            return;
        }

        String selectedId = (String) tlsSelector.getSelectedItem();
        if (selectedId != null) {
            TrafficLightWrapper tls = controller.getTrafficLights().get(selectedId);
            if (tls != null) {
                tls.nextPhase();
                updateInfo();
            }
        }
    }

    /**
     * updates the text labels based on the selected traffic light
     */
    private void updateInfo() {
        if (controller == null) {
            return;
        }

        String selectedId = (String) tlsSelector.getSelectedItem();

        // case: nothing selected
        if (selectedId == null) {
            statusLabel.setText("State: -");
            phaseLabel.setText("Phase: -");
            nextPhaseButton.setEnabled(false);
            return;
        }

        // case: something selected
        TrafficLightWrapper tls = controller.getTrafficLights().get(selectedId);

        if (tls != null) {
            statusLabel.setText("State: " + tls.getCurrentState());
            nextPhaseButton.setEnabled(true);

            // show phase index or raw phase depending on logic
            if (tls.getNumPhases() > 0) {
                phaseLabel.setText("Phase Index: " + tls.getCurrentPhase());
            } else {
                phaseLabel.setText("Phase: " + tls.getCurrentPhase());
            }
        }
    }
}
