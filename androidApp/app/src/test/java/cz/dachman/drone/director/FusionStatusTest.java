package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import java.util.Collections;
import org.junit.Test;

public final class FusionStatusTest {
    @Test public void distinguishesImageTagFusionAndHold() {
        long now = 10_000L;
        TargetBox box = new TargetBox(.2f, .2f, .5f, .8f, .9f, now);
        TrackingSnapshot image = new TrackingSnapshot(Collections.singletonList(box), box, null, now);
        WorkerPositionFix fix = new WorkerPositionFix("one", 49d, 14d, 1f, now,
            WorkerPositionFix.Source.UWB_FUSED, 0f, 0f);
        WorkerGeoSnapshot tag = new WorkerGeoSnapshot("one", "", fix, null);
        assertEquals(FusionStatus.IMAGE_OK,
            FusionStatus.evaluate(FlightMode.FOLLOW, image, WorkerGeoSnapshot.empty(), now));
        assertEquals(FusionStatus.TAG_OK,
            FusionStatus.evaluate(FlightMode.FOLLOW, TrackingSnapshot.empty(), tag, now));
        assertEquals(FusionStatus.FUSION_OK,
            FusionStatus.evaluate(FlightMode.FOLLOW, image, tag, now));
        assertEquals(FusionStatus.HOLD,
            FusionStatus.evaluate(FlightMode.FOLLOW, TrackingSnapshot.empty(), WorkerGeoSnapshot.empty(), now));
    }
}
