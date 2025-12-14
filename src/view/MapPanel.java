package view;

import controller.SimulationController;
import de.tudresden.sumo.objects.SumoPosition2D;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Map;

/**
 * Panel responsible for rendering the simulation map.
 * Enhanced: Vehicles now have borders and minimum size constraints for better visibility.
 */
public class MapPanel extends JPanel {

    private SimulationController controller;

    // colors
    private static final Color COLOR_BACKGROUND = new Color(30, 30, 30);
    private static final Color COLOR_ROAD = new Color(100, 100, 100); // Slightly darker for contrast
    private static final Color COLOR_ROAD_OUTLINE = new Color(60, 60, 60);

    // navigation state
    private double zoom = 1.0;
    private double camX = 0;
    private double camY = 0;
    private boolean firstLoad = true;
    private Point lastMousePt;

    public MapPanel() {
        setBackground(COLOR_BACKGROUND);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = 1.1;
                if (e.getWheelRotation() > 0) {
                    zoom /= factor;
                } else {
                    zoom *= factor;
                }
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePt = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePt != null) {
                    double dx = e.getX() - lastMousePt.x;
                    double dy = e.getY() - lastMousePt.y;
                    camX -= dx / getScale();
                    camY += dy / getScale();
                    lastMousePt = e.getPoint();
                    repaint();
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    public void setController(SimulationController controller) {
        this.controller = controller;
    }

    private double getScale() {
        return 5.0 * zoom;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (controller == null) {
            g.setColor(Color.WHITE);
            g.drawString("Waiting for Controller...", 20, 20);
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double mapWidth = controller.getMapWidth();
        double mapHeight = controller.getMapHeight();

        if (mapWidth == 0 || mapHeight == 0) {
            g.setColor(Color.WHITE);
            g.drawString("Press Play to load Map", 20, 20);
            return;
        }

        if (firstLoad) {
            camX = controller.getMapMinX() + (mapWidth / 2.0);
            camY = controller.getMapMinY() + (mapHeight / 2.0);
            double scaleX = getWidth() / mapWidth;
            double scaleY = getHeight() / mapHeight;
            zoom = (scaleX < scaleY ? scaleX : scaleY) * 0.9 / 5.0;
            firstLoad = false;
        }

        AffineTransform oldTransform = g2d.getTransform();
        AffineTransform tx = new AffineTransform();
        tx.translate(getWidth() / 2.0, getHeight() / 2.0);
        double s = getScale();
        tx.scale(s, -s);
        tx.translate(-camX, -camY);
        g2d.setTransform(tx);

        // Draw Roads
        List<EdgeWrapper> edges = controller.getMapEdges();
        if (edges != null) {
            for (EdgeWrapper edge : edges) {
                drawEdge(g2d, edge, s);
            }
        }

        // Draw Vehicles with Enhanced Visibility
        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        for (VehicleWrapper car : vehicles.values()) {
            drawVehicle(g2d, car, s);
        }

        // Draw Traffic Lights
        Map<String, TrafficLightWrapper> lights = controller.getTrafficLights();
        if (lights != null) {
            for (TrafficLightWrapper tls : lights.values()) {
                for (TrafficLightWrapper.SignalPoint signal : tls.getSignalPoints()) {
                    drawSignal(g2d, signal, s);
                }
            }
        }

        g2d.setTransform(oldTransform);

        // HUD
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2d.drawString("Cars: " + vehicles.size() + " | Streets: " + (edges != null ? edges.size() : 0), 20, 30);
        g2d.drawString("Zoom: " + String.format("%.2f", zoom), 20, 50);
    }

    private void drawEdge(Graphics2D g2, EdgeWrapper edge, double scale) {
        List<SumoPosition2D> points = edge.getShapePoints();
        if (points.isEmpty()) return;

        Path2D path = new Path2D.Double();
        path.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);

        float width = (float) edge.getWidth();

        // Optional: Draw road outline for better contrast
        g2.setStroke(new BasicStroke(width + (float)(1.0/scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COLOR_ROAD_OUTLINE);
        g2.draw(path);

        // Draw main road
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COLOR_ROAD);
        g2.draw(path);
    }

    private void drawVehicle(Graphics2D g2, VehicleWrapper car, double scale) {
        AffineTransform old = g2.getTransform();
        g2.translate(car.getX(), car.getY());

        double w = car.getWidth();
        double l = car.getLength();

        // VISIBILITY FIX: Minimum size constraint
        // If the car is smaller than 4 pixels on screen, scale it up visually
        double minPixels = 4.0;
        double currentSizePixels = l * scale;

        double drawScale = 1.0;
        if (currentSizePixels < minPixels) {
            drawScale = minPixels / currentSizePixels;
        }

        // Rotate
        g2.rotate(Math.toRadians(-car.getAngle() + 90));

        // Draw Car Body
        g2.setColor(car.getColor());

        Shape carShape;
        if (drawScale > 1.5) {
            // If heavily zoomed out, draw as a simple circle (dot) for clarity
            double size = Math.max(w, l) * drawScale;
            carShape = new Ellipse2D.Double(-size/2, -size/2, size, size);
        } else {
            // Otherwise draw the detailed rectangle
            carShape = new Rectangle2D.Double(-l/2 * drawScale, -w/2 * drawScale, l * drawScale, w * drawScale);
        }

        g2.fill(carShape);

        // VISIBILITY FIX: Contrast Border
        // Draw a 1-pixel constant width border around the car
        // We use (1.0 / scale) to ensure the line is always 1 pixel on SCREEN, not in world
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke((float)(1.0 / scale)));
        g2.draw(carShape);

        g2.setTransform(old);
    }

    private void drawSignal(Graphics2D g2, TrafficLightWrapper.SignalPoint signal, double currentScale) {
        double baseSizeMeters = 1.5;
        double minPixels = 2.0;

        double sizeInMeters = baseSizeMeters;
        if (baseSizeMeters * currentScale < minPixels) {
            sizeInMeters = minPixels / currentScale;
        }

        g2.setColor(signal.color);
        g2.fill(new Ellipse2D.Double(signal.x - sizeInMeters/2, signal.y - sizeInMeters/2, sizeInMeters, sizeInMeters));
    }
}