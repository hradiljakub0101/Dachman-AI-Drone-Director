package cz.dachman.drone.director;

/** Explicit pilot choice, independent of the slot currently being edited in the image. */
public enum TargetSelection {
    WORKER_ONE("Worker 1"), WORKER_TWO("Worker 2"), BOTH("Worker 1 + 2");

    public final String label;
    TargetSelection(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
