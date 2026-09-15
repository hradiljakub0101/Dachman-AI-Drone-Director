package cz.dachman.drone.director;

public enum FusionStatus {
    IMAGE_OK("OBRAZ OK"), TAG_OK("TAG OK"), FUSION_OK("FÚZE OK"), HOLD("HOLD");
    public final String label;
    FusionStatus(String label) { this.label = label; }

    public static FusionStatus evaluate(FlightMode mode, TrackingSnapshot tracking,
            WorkerGeoSnapshot geo, long now) {
        boolean image = tracking != null && mode != null && tracking.hasRequiredTargets(mode);
        boolean bound = geo != null && geo.hasAnyBinding();
        boolean tag = bound && geo.requiredReliable(mode, now);
        if (image && tag) return FUSION_OK;
        if (image && !bound) return IMAGE_OK;
        if (!image && tag) return TAG_OK;
        return HOLD;
    }
}
