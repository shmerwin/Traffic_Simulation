package view;

import controller.SimulationController;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Affine;
import model.EdgeWrapper;
import model.TrafficLightWrapper;
import model.VehicleWrapper;
import de.tudresden.sumo.objects.SumoPosition2D;

import java.util.List;
import java.util.Map;

/**
 * The main drawing canvas responsible for rendering the simulation map.
 * Enhanced: Vehicles now have borders and dynamic detail levels for better visibility.
 */
public class FxMapCanvas extends Canvas {

    private SimulationController controller;

    // colors
    private static final Color COLOR_BACKGROUND = Color.rgb(30, 30, 30);
    private static final Color COLOR_ROAD = Color.rgb(100, 100, 100);
    private static final Color COLOR_ROAD_OUTLINE = Color.rgb(60, 60, 60);
    private static final Color COLOR_KERB = Color.rgb(120, 120, 120);

    // navigation state
    private double zoom = 1.0;
    private double camX = 0;
    private double camY = 0;
    private boolean firstLoad = true;
    private double lastMouseX, lastMouseY;

    public FxMapCanvas(SimulationController controller) {
        this.controller = controller;

        widthProperty().addListener(evt -> draw());
        heightProperty().addListener(evt -> draw());

        setOnScroll(e -> handleScroll(e));

        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;
            camX -= dx / getScale();
            camY += dy / getScale();
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            draw();
        });

        setOnMouseClicked(e -> handleMouseClick(e));
    }

    private void handleMouseClick(MouseEvent e) {
        if (controller == null) return;

        double w = getWidth();
        double h = getHeight();
        double s = getScale();

        double clickSimX = (e.getX() - w / 2.0) / s + camX;
        double clickSimY = camY - (e.getY() - h / 2.0) / s;

        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        if (vehicles == null) return;

        for (VehicleWrapper car : vehicles.values()) {
            double dist = Math.hypot(car.getX() - clickSimX, car.getY() - clickSimY);
            double hitRadius = Math.max(2.0, car.getLength() / 2.0);

            if (dist < hitRadius) {
                showVehicleInfo(car);
                return;
            }
        }
    }

    private void showVehicleInfo(VehicleWrapper car) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Vehicle Info");
        alert.setHeaderText("Vehicle Details");

        String typeStr = (car.getType() != null) ? car.getType() : "Unknown";

        String infoText = "ID: " + car.getId() + "\n" +
                "Type: " + typeStr + "\n" +
                "Road: " + car.getRoadId() + "\n" +
                "Speed: " + String.format("%.2f km/h", car.getSpeed() * 3.6) + "\n" +
                "Color: " + car.getColor().toString();

        alert.setContentText(infoText);
        alert.showAndWait();
    }

    @Override
    public boolean isResizable() { return true; }

    @Override
    public double minWidth(double height) { return 100.0; }

    @Override
    public double minHeight(double width) { return 100.0; }

    @Override
    public double prefWidth(double height) { return 800.0; }

    @Override
    public double prefHeight(double width) { return 600.0; }

    private void handleScroll(ScrollEvent e) {
        double factor = 1.1;
        if (e.getDeltaY() < 0) zoom /= factor;
        else zoom *= factor;
        draw();
    }

    private double getScale() { return 5.0 * zoom; }

    public void draw() {
        double w = getWidth();
        double h = getHeight();

        GraphicsContext gc = getGraphicsContext2D();

        gc.setFill(COLOR_BACKGROUND);
        gc.fillRect(0, 0, w, h);

        if (controller == null) return;
        double mapWidth = controller.getMapWidth();
        double mapHeight = controller.getMapHeight();

        if (mapWidth == 0 || mapHeight == 0) {
            gc.setStroke(Color.WHITE);
            gc.strokeText("Press Play to load Map", 20, 20);
            return;
        }

        if (firstLoad) {
            camX = controller.getMapMinX() + (mapWidth / 2.0);
            camY = controller.getMapMinY() + (mapHeight / 2.0);
            double scaleX = w / mapWidth;
            double scaleY = h / mapHeight;
            zoom = (Math.min(scaleX, scaleY)) * 0.9 / 5.0;
            firstLoad = false;
        }

        gc.save();

        Affine t = new Affine();
        t.appendTranslation(w / 2.0, h / 2.0);
        double s = getScale();
        t.appendScale(s, -s);
        t.appendTranslation(-camX, -camY);
        gc.setTransform(t);

        List<EdgeWrapper> edges = controller.getMapEdges();
        if (edges != null) {
            for (EdgeWrapper edge : edges){
                drawEdge(gc, edge, s);
            }
        }

        Map<String, VehicleWrapper> vehicles = controller.getActiveVehicles();
        int shownCars = 0;

        if (vehicles != null) {

            for (VehicleWrapper car : vehicles.values()) {
                if (controller.matchesFilter(car)) {
                    drawVehicle(gc, car, s);
                    shownCars++;
                }
            }
        }

        Map<String, TrafficLightWrapper> lights = controller.getTrafficLights();
        if (lights != null) {
            for (TrafficLightWrapper tls : lights.values()) {
                for (TrafficLightWrapper.SignalPoint signal : tls.getSignalPoints()) {
                    drawSignal(gc, signal, s);
                }
            }
        }

        gc.restore();

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        int carCount = shownCars;
        int streetCount = (edges != null) ? edges.size() : 0;
        gc.fillText("Cars: " + carCount + " | Streets: " + streetCount, 20, 30);
        gc.fillText("Zoom: " + String.format("%.2f", zoom), 20, 50);

        gc.fillText("Filter: " + controller.getActiveFilter(), 20, 70);
    }

    private void drawEdge(GraphicsContext gc, EdgeWrapper edge, double scale) {
        List<SumoPosition2D> points = edge.getShapePoints();
        if (points.isEmpty()) return;

        gc.beginPath();
        gc.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) gc.lineTo(points.get(i).x, points.get(i).y);

        double streetWidth = edge.getWidth();

        gc.setStroke(COLOR_KERB);
        gc.setLineWidth(streetWidth + 0.8);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.stroke();

        gc.setStroke(COLOR_ROAD);
        gc.setLineWidth(streetWidth);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.stroke();
    }

    private void drawVehicle(GraphicsContext gc, VehicleWrapper car, double scale) {
        gc.save();
        gc.translate(car.getX(), car.getY());
        gc.rotate(-car.getAngle() + 90);

        double w = car.getWidth();
        double l = car.getLength();

        double minPixels = 4.0;
        double currentSizePixels = l * scale;
        double drawScale = 1.0;

        if (currentSizePixels < minPixels) {
            drawScale = minPixels / currentSizePixels;
        }

        double halfL = (l / 2.0) * drawScale;
        double halfW = (w / 2.0) * drawScale;

        if (drawScale > 2.0) {
            gc.setFill(car.getColor());
            gc.fillRoundRect(-halfL, -halfW, l * drawScale, w * drawScale, w*0.5, w*0.5);
            gc.restore();
            return;
        }

        gc.setFill(Color.BLACK);
        double wheelL = l * 0.2 * drawScale;
        double wheelW = w * 0.3 * drawScale;
        double wheelXOffset = l * 0.25 * drawScale;
        double wheelYOffset = w * 0.4 * drawScale;

        gc.fillRect(wheelXOffset - wheelL/2, -wheelYOffset - wheelW/2, wheelL, wheelW);
        gc.fillRect(wheelXOffset - wheelL/2, wheelYOffset - wheelW/2, wheelL, wheelW);
        gc.fillRect(-wheelXOffset - wheelL/2, -wheelYOffset - wheelW/2, wheelL, wheelW);
        gc.fillRect(-wheelXOffset - wheelL/2, wheelYOffset - wheelW/2, wheelL, wheelW);

        gc.setFill(car.getColor());
        gc.fillRoundRect(-halfL, -halfW, l * drawScale, w * drawScale, w*0.4*drawScale, w*0.4*drawScale);

        gc.setFill(Color.rgb(30, 30, 35));
        double cabinL = l * 0.6 * drawScale;
        double cabinW = w * 0.8 * drawScale;
        double cabinX = -(l * 0.05) * drawScale;
        gc.fillRoundRect(cabinX - cabinL/2, -cabinW/2, cabinL, cabinW, w*0.2, w*0.2);

        gc.setFill(car.getColor());
        double roofL = l * 0.35 * drawScale;
        double roofW = w * 0.7 * drawScale;
        gc.fillRoundRect(cabinX - roofL/2, -roofW/2, roofL, roofW, w*0.1, w*0.1);

        gc.setFill(Color.LIGHTYELLOW);
        double lightSize = w * 0.2 * drawScale;
        gc.fillOval(halfL - lightSize, -halfW + (w*0.1*drawScale), lightSize, lightSize);
        gc.fillOval(halfL - lightSize, halfW - (w*0.1*drawScale) - lightSize, lightSize, lightSize);

        gc.setFill(Color.RED);
        gc.fillOval(-halfL, -halfW + (w*0.1*drawScale), lightSize, lightSize);
        gc.fillOval(-halfL, halfW - (w*0.1*drawScale) - lightSize, lightSize, lightSize);

        gc.setFill(car.getColor());
        double mirrorL = l * 0.05 * drawScale;
        double mirrorW = w * 0.2 * drawScale;
        double mirrorX = l * 0.15 * drawScale;
        gc.fillOval(mirrorX, -halfW - mirrorW/2, mirrorL, mirrorW);
        gc.fillOval(mirrorX, halfW - mirrorW/2, mirrorL, mirrorW);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(0.5 / scale);
        gc.strokeRoundRect(-halfL, -halfW, l * drawScale, w * drawScale, w*0.4*drawScale, w*0.4*drawScale);

        gc.restore();
    }

    private void drawSignal(GraphicsContext gc, TrafficLightWrapper.SignalPoint signal, double currentScale) {
        double baseSizeMeters = 2.5;
        double minPixels = 3.0;
        double sizeInMeters = baseSizeMeters;
        if (baseSizeMeters * currentScale < minPixels) sizeInMeters = minPixels / currentScale;

        gc.setFill(signal.color);
        gc.fillOval(signal.x - sizeInMeters/2, signal.y - sizeInMeters/2, sizeInMeters, sizeInMeters);
    }

}