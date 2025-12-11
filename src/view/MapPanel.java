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
 * panel responsible for rendering the simulation map
 * draws edges, vehicles and traffic lights
 * includes zoom and pan functionality
 */
public class MapPanel extends JPanel {

    private SimulationController controller;

    // colors
    private static final Color COLOR_BACKGROUND = new Color(30, 30, 30);
    private static final Color COLOR_ROAD = Color.LIGHT_GRAY;

    // navigation state
    private double zoom = 1.0;
    private double camX = 0;
    private double camY = 0;
    private boolean firstLoad = true;
    private Point lastMousePt;

    public MapPanel() {
        // set dark background color
        setBackground(COLOR_BACKGROUND);

        // mouse handling for zoom and pan
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

                    // adjust camera position
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

    /**
     * sets the controller reference needed for data access
     * @param controller the simulation controller
     */
    public void setController(SimulationController controller) {
        this.controller = controller;
    }

    private double getScale() {
        return 5.0 * zoom;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // check if controller is ready
        if (controller == null) {
            g.setColor(Color.WHITE);
            g.drawString("Waiting for Controller...", 20, 20);
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        // enable antialiasing for smoother drawing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double mapWidth = controller.getMapWidth();
        double mapHeight = controller.getMapHeight();

        // check if map is loaded
        if (mapWidth == 0 || mapHeight == 0) {
            g.setColor(Color.WHITE);
            g.drawString("Press Play to load Map...", 20, 20);
            return;
        }

        // auto center on first load
        if (firstLoad) {
            camX = controller.getMapMinX() + (mapWidth / 2.0);
            camY = controller.getMapMinY() + (mapHeight / 2.0);

            double scaleX = getWidth() / mapWidth;
            double scaleY = getHeight() / mapHeight;

            // simple if else for min scale
            if (scaleX < scaleY) {
                zoom = scaleX * 0.9 / 5.0;
            } else {
                zoom = scaleY * 0.9 / 5.0;
            }

            firstLoad = false;
        }

        // save old transform to restore later
        AffineTransform oldTransform = g2d.getTransform();
        AffineTransform tx = new AffineTransform();

        // center on screen
        tx.translate(getWidth() / 2.0, getHeight() / 2.0);

        // apply scale and flip y axis
        double s = getScale();
        tx.scale(s, -s);

        // move camera to position
        tx.translate(-camX, -camY);

        g2d.setTransform(tx);

        // draw map edges (roads)
        List<EdgeWrapper> edges = controller.getMapEdges();
        if (edges != null) {
            g2d.setColor(COLOR_ROAD);
            for (EdgeWrapper edge : edges) {
                drawEdge(g2d, edge);
            }
        }

        // draw vehicles
        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        for (VehicleWrapper car : vehicles.values()) {
            drawVehicle(g2d, car);
        }

        // draw traffic lights
        Map<String, TrafficLightWrapper> lights = controller.getTrafficLights();
        if (lights != null) {
            for (TrafficLightWrapper tls : lights.values()) {
                for (TrafficLightWrapper.SignalPoint signal : tls.getSignalPoints()) {
                    drawSignal(g2d, signal, s);
                }
            }
        }

        // restore transform for hud
        g2d.setTransform(oldTransform);

        // debug info
        g2d.setColor(Color.WHITE);

        int edgeCount = 0;
        if (edges != null) {
            edgeCount = edges.size();
        }

        g2d.drawString("Cars: " + vehicles.size() + " | Streets: " + edgeCount, 20, 30);
        g2d.drawString("Zoom: " + String.format("%.2f", zoom), 20, 50);
    }

    /**
     * helper to draw a single road edge
     */
    private void drawEdge(Graphics2D g2, EdgeWrapper edge) {
        List<SumoPosition2D> points = edge.getShapePoints();
        if (points.isEmpty()) {
            return;
        }

        Path2D path = new Path2D.Double();
        boolean first = true;
        for (SumoPosition2D p : points) {
            if (first) {
                path.moveTo(p.x, p.y);
                first = false;
            } else {
                path.lineTo(p.x, p.y);
            }
        }

        float width = (float) edge.getWidth();
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    /**
     * helper to draw a vehicle as a rectangle
     */
    private void drawVehicle(Graphics2D g2, VehicleWrapper car) {
        AffineTransform old = g2.getTransform();
        g2.translate(car.getX(), car.getY());

        // rotate based on sumo angle
        g2.rotate(Math.toRadians(-car.getAngle() + 90));

        double w = car.getWidth();
        double l = car.getLength();

        g2.setColor(car.getColor());
        g2.fill(new Rectangle2D.Double(-l/2, -w/2, l, w));

        g2.setTransform(old);
    }

    /**
     * helper to draw a traffic light signal point
     */
    private void drawSignal(Graphics2D g2, TrafficLightWrapper.SignalPoint signal, double currentScale) {
        // keep constant pixel size regardless of zoom
        double pixelSize = 8.0;
        double sizeInMeters = pixelSize / currentScale;

        g2.setColor(signal.color);
        g2.fill(new Ellipse2D.Double(signal.x - sizeInMeters/2, signal.y - sizeInMeters/2, sizeInMeters, sizeInMeters));
    }
}
