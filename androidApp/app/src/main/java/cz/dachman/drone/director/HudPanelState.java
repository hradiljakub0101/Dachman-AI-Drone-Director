package cz.dachman.drone.director;

/** Mutually exclusive slide-out HUD panels, kept independent from flight authority. */
public final class HudPanelState {
    private boolean controlsOpen;
    private boolean mapOpen;

    public boolean controlsOpen() { return controlsOpen; }
    public boolean mapOpen() { return mapOpen; }

    public void toggleControls() {
        boolean next = !controlsOpen;
        controlsOpen = next;
        if (next) mapOpen = false;
    }

    public void toggleMap() {
        boolean next = !mapOpen;
        mapOpen = next;
        if (next) controlsOpen = false;
    }

    public void openControls() {
        controlsOpen = true;
        mapOpen = false;
    }

    public void openMap() {
        mapOpen = true;
        controlsOpen = false;
    }

    public void closeAll() {
        controlsOpen = false;
        mapOpen = false;
    }
}
