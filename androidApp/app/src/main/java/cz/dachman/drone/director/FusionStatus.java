package cz.dachman.drone.director;

public enum FusionStatus {
    IMAGE_OK("OBRAZ OK"), HOLD("HOLD");
    public final String label;
    FusionStatus(String label) { this.label = label; }

}
