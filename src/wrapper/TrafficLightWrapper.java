package wrapper;

import org.eclipse.sumo.libtraci.TrafficLight;

public class TrafficLightWrapper {

    private final String id;

    public TrafficLightWrapper(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getState() {
        return TrafficLight.getRedYellowGreenState(id);
    }

    public String getPhase() {
        return TrafficLight.getPhaseName(id);
    }

    public void setState(String state) {
        TrafficLight.setRedYellowGreenState(id, state);
    }

    public void setPhase(String phase) {
        TrafficLight.setPhaseName(id, phase);
    }



}
