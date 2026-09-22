package cz.dachman.drone.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackingSnapshot {
    public final List<TargetBox> candidates;
    public final TargetBox primary;
    public final TargetBox secondary;
    public final long processedAtMillis;

    public TrackingSnapshot(List<TargetBox> candidates, TargetBox primary, TargetBox secondary, long processedAtMillis) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.primary = primary;
        this.secondary = secondary;
        this.processedAtMillis = processedAtMillis;
    }

    public static TrackingSnapshot empty() {
        return new TrackingSnapshot(Collections.emptyList(), null, null, 0L);
    }

    public boolean hasRequiredTargets(FlightMode mode) {
        return (!mode.requiresPrimary || primary != null) && (!mode.requiresSecondary || secondary != null);
    }

    public TargetBox targetFor(FlightMode mode) {
        return mode.requiresSecondary ? TargetBox.union(primary, secondary) : primary;
    }

    public boolean hasRequiredTargets(FlightPlan plan) {
        return !plan.mode.requiresPrimary || targetFor(plan) != null;
    }

    public TargetBox targetFor(FlightPlan plan) {
        if (plan.targets == TargetSelection.WORKER_TWO) return secondary;
        if (plan.targets == TargetSelection.BOTH) {
            return primary == null || secondary == null ? null : TargetBox.union(primary, secondary);
        }
        return primary;
    }
}
