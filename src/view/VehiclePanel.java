package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * panel to create and spawn new vehicles into the simulation
 * allows selecting type and route
 */
public class VehiclePanel extends JPanel {

    private SimulationController controller;

    private JTextField idField;
    private JComboBox<String> typeSelector;
    private JComboBox<String> routeSelector;
    private JButton spawnButton;
    private JButton refreshListsButton;

    public VehiclePanel() {
        setLayout(new BorderLayout());

        // create form area
        JPanel formPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        formPanel.setBorder(BorderFactory.createTitledBorder("Spawn Vehicle"));

        formPanel.add(new JLabel("Vehicle ID:"));
        idField = new JTextField("new_car");
        formPanel.add(idField);

        formPanel.add(new JLabel("Type:"));
        typeSelector = new JComboBox<>();
        formPanel.add(typeSelector);

        formPanel.add(new JLabel("Route:"));
        routeSelector = new JComboBox<>();
        formPanel.add(routeSelector);

        add(formPanel, BorderLayout.NORTH);

        // create buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 5));

        refreshListsButton = new JButton("Load Data");
        refreshListsButton.addActionListener(e -> updateLists());

        spawnButton = new JButton("Spawn");
        spawnButton.addActionListener(e -> spawnVehicle());

        buttonPanel.add(refreshListsButton);
        buttonPanel.add(spawnButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // instructions label
        JLabel infoLabel = new JLabel("<html>1. Press Play<br>2. Load Data<br>3. Spawn</html>");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(infoLabel, BorderLayout.CENTER);
    }

    /**
     * sets the controller reference
     * @param controller the simulation controller
     */
    public void setController(SimulationController controller) {
        this.controller = controller;
    }

    /**
     * fetches available vehicle types and routes from sumo
     */
    public void updateLists() {
        if (controller == null) {
            return;
        }

        typeSelector.removeAllItems();
        routeSelector.removeAllItems();

        List<String> types = controller.getVehicleTypeList();
        List<String> routes = controller.getRouteList();

        for (String t : types) {
            typeSelector.addItem(t);
        }

        if (routes.isEmpty()) {
            routeSelector.addItem("No routes found");
        } else {
            for (String r : routes) {
                routeSelector.addItem(r);
            }
        }
    }

    /**
     * reads inputs and triggers the spawn command in controller
     */
    private void spawnVehicle() {
        if (controller == null) {
            return;
        }

        String id = idField.getText();
        String type = (String) typeSelector.getSelectedItem();
        String route = (String) routeSelector.getSelectedItem();

        // validation
        if (route == null || route.startsWith("No routes")) {
            JOptionPane.showMessageDialog(this, "Please choose a valid route.");
            return;
        }

        if (id != null && !id.isEmpty() && type != null) {
            controller.spawnVehicle(id, type, route);
            // auto increment id for convenience
            idField.setText(id + "_x");
        }
    }
}
