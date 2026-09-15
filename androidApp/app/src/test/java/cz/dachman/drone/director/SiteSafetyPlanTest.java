package cz.dachman.drone.director;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class SiteSafetyPlanTest {
    private static final java.util.List<SiteSafetyPlan.Point> ROOF = Arrays.asList(
        new SiteSafetyPlan.Point(49.0000, 14.0000), new SiteSafetyPlan.Point(49.0000, 14.0010),
        new SiteSafetyPlan.Point(49.0010, 14.0010), new SiteSafetyPlan.Point(49.0010, 14.0000));

    @Test public void acceptsInsideRoofAndRejectsOutside() {
        SiteSafetyPlan plan = new SiteSafetyPlan(ROOF, Collections.emptyList(), 49d, 14d, 10f, 0f, 0f);
        assertTrue(plan.aircraftAllowed(49.0005, 14.0005));
        assertFalse(plan.aircraftAllowed(49.0020, 14.0005));
    }

    @Test public void forbiddenPolygonOverridesRoof() {
        java.util.List<SiteSafetyPlan.Point> forbidden = Arrays.asList(
            new SiteSafetyPlan.Point(49.0004, 14.0004), new SiteSafetyPlan.Point(49.0004, 14.0006),
            new SiteSafetyPlan.Point(49.0006, 14.0006), new SiteSafetyPlan.Point(49.0006, 14.0004));
        SiteSafetyPlan plan = new SiteSafetyPlan(ROOF, forbidden, 49d, 14d, 10f, 0f, 0f);
        assertFalse(plan.aircraftAllowed(49.0005, 14.0005));
    }

    @Test public void roofHeightChangesAlongDownslope() {
        SiteSafetyPlan plan = new SiteSafetyPlan(ROOF, Collections.emptyList(), 49d, 14d, 10f, 45f, 0f);
        assertTrue(plan.roofHeightAt(49.0001, 14d) < 10f);
    }
}
