package cz.dachman.drone.director;

import org.junit.Test;
import static org.junit.Assert.*;

public class HudPanelStateTest {
    @Test public void cameraStartsWithBothDrawersClosed() {
        HudPanelState state = new HudPanelState();
        assertFalse(state.controlsOpen());
        assertFalse(state.mapOpen());
    }

    @Test public void openingControlsClosesMapPanel() {
        HudPanelState state = new HudPanelState();
        state.openMap();
        state.openControls();
        assertTrue(state.controlsOpen());
        assertFalse(state.mapOpen());
    }

    @Test public void openingMapClosesControlsPanel() {
        HudPanelState state = new HudPanelState();
        state.openControls();
        state.openMap();
        assertFalse(state.controlsOpen());
        assertTrue(state.mapOpen());
    }

    @Test public void togglesAndFlightCloseRestoreUnobstructedCamera() {
        HudPanelState state = new HudPanelState();
        state.toggleControls();
        assertTrue(state.controlsOpen());
        state.toggleControls();
        assertFalse(state.controlsOpen());
        state.toggleMap();
        state.closeAll();
        assertFalse(state.controlsOpen());
        assertFalse(state.mapOpen());
    }
}
