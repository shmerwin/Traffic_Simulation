package view;

import controller.SimulationController;

import javax.swing.*;
import java.awt.*;

public class ControlPanel extends JPanel {

    private JButton playButton;
    private JButton pauseButton;
    private JSlider speedSlider;
    private SimulationController controller;

    public ControlPanel() {

        setLayout(new FlowLayout(FlowLayout.LEFT));

        playButton = new JButton("Play");
        pauseButton = new JButton("Pause");

        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 10, 1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setPreferredSize(new Dimension(150, 40));


        playButton.addActionListener(e -> {
            if (controller != null) controller.play(); });

        pauseButton.addActionListener(e -> {
            if (controller != null) controller.pause(); });

        speedSlider.addChangeListener(e -> {
            if (!speedSlider.getValueIsAdjusting() && controller != null) {
                controller.setSpeedMultiplier(speedSlider.getValue());
            }
        });

        add(playButton);
        add(pauseButton);
        add(Box.createHorizontalStrut(20));
        add(new JLabel("Speed:"));
        add(speedSlider);

    }


}
