package view;

import controller.SimulationController;
import de.tudresden.sumo.objects.SumoPosition2D;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;

public class MapPanel extends JPanel {

    private SimulationController controller;

    public MapPanel() {
        setBackground(new Color(30, 30, 30));
    }

    public void setController(SimulationController controller) {
        this.controller = controller;
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
            g.drawString("Press Play to load Map...", 20, 20);
            return;
        }

        double panelWidth = getWidth();
        double panelHeight = getHeight();
        double scaleX = panelWidth / mapWidth;
        double scaleY = panelHeight / mapHeight;
        double scale = Math.min(scaleX, scaleY) * 0.95;
        double offsetX = (panelWidth - (mapWidth * scale)) / 2;
        double offsetY = (panelHeight - (mapHeight * scale)) / 2;


        List<EdgeWrapper> edges = controller.getMapEdges();
        if (edges != null) {
            g2d.setColor(Color.LIGHT_GRAY);
            for (EdgeWrapper edge : edges) {
                drawEdge(g2d, edge, scale, offsetX, offsetY);
            }
        }

        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        for (VehicleWrapper car : vehicles.values()) {
            drawVehicle(g2d, car, scale, offsetX, offsetY);
        }

        Map<String, TrafficLightWrapper> lights = controller.getTrafficLights();
        if (lights != null) {
            for (TrafficLightWrapper tls : lights.values()) {
                for (TrafficLightWrapper.SignalPoint signal : tls.getSignalPoints()) {
                    drawSignal(g2d, signal, scale, offsetX, offsetY);
                }
            }
        }

        g2d.setColor(Color.WHITE);
        g2d.drawString("Autos: " + vehicles.size() + " | Straßen: " + (edges != null ? edges.size() : 0), 10, 20);
    }

    private void drawEdge(Graphics2D g2, EdgeWrapper edge, double scale, double offX, double offY) {
        List<SumoPosition2D> points = edge.getShapePoints();
        if (points.isEmpty()) return;

        Path2D path = new Path2D.Double();
        boolean first = true;
        for (SumoPosition2D p : points) {
            double screenX = offX + ((p.x - controller.getMapMinX()) * scale);
            double screenY = offY + ((controller.getMapMaxY() - p.y) * scale);
            if (first) { path.moveTo(screenX, screenY); first = false; } else { path.lineTo(screenX, screenY); }
        }
        float strokeWidth = (float) Math.max(1.0, edge.getWidth() * scale);
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.draw(path);
    }

    private void drawVehicle(Graphics2D g2, VehicleWrapper car, double scale, double offX, double offY) {
        double rawX = car.getX() - controller.getMapMinX();
        double rawY = controller.getMapMaxY() - car.getY();
        double screenX = offX + (rawX * scale);
        double screenY = offY + (rawY * scale);

        double minSize = 6.0;
        double carWidthPx = Math.max(car.getWidth() * scale, minSize);
        double carLengthPx = Math.max(car.getLength() * scale, minSize * 2);

        AffineTransform old = g2.getTransform();
        g2.translate(screenX, screenY);
        g2.rotate(Math.toRadians(car.getAngle()));
        g2.setColor(car.getColor());
        g2.fill(new Rectangle.Double(-carWidthPx / 2, -carLengthPx / 2, carWidthPx, carLengthPx));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new Rectangle.Double(-carWidthPx / 2, -carLengthPx / 2, carWidthPx, carLengthPx));
        g2.setTransform(old);
    }

    private void drawSignal(Graphics2D g2, TrafficLightWrapper.SignalPoint signal, double scale, double offX, double offY) {
        double rawX = signal.x - controller.getMapMinX();
        double rawY = controller.getMapMaxY() - signal.y;
        double screenX = offX + (rawX * scale);
        double screenY = offY + (rawY * scale);
        double size = 8.0;

        g2.setColor(signal.color);
        g2.fill(new java.awt.geom.Ellipse2D.Double(screenX - size/2, screenY - size/2, size, size));
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new java.awt.geom.Ellipse2D.Double(screenX - size/2, screenY - size/2, size, size));

    }
}