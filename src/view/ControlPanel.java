package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;

/**
 * Panel containing the buttons to control the simulation (play, pause, speed)
 */
public class ControlPanel extends JPanel {

    private JButton playButton;
    private JButton pauseButton;
    private JSlider speedSlider;

    // reference to our controller
    private SimulationController controller;

    public ControlPanel() {
        // use flow layout aligned to the left
        setLayout(new FlowLayout(FlowLayout.LEFT));

        playButton = new JButton("Play");
        pauseButton = new JButton("Pause");

        // slider from 1 to 10 for speed multiplier
        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 10, 1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setPreferredSize(new Dimension(150, 40));

        // add listeners that only run if controller is set
        playButton.addActionListener(e -> {
            if (controller != null) controller.play();
        });

        pauseButton.addActionListener(e -> {
            if (controller != null) controller.pause();
        });

        speedSlider.addChangeListener(e -> {
            // only update if user stopped sliding
            if (!speedSlider.getValueIsAdjusting() && controller != null) {
                controller.setSpeedMultiplier(speedSlider.getValue());
            }
        });

        // add components to the panel
        add(playButton);
        add(pauseButton);
        add(Box.createHorizontalStrut(20)); // spacer
        add(new JLabel("Speed:"));
        add(speedSlider);
    }

    /**
     * sets the controller so buttons can work
     * @param controller the simulation controller
     */
    public void setController(SimulationController controller) {
        this.controller = controller;
    }
}
