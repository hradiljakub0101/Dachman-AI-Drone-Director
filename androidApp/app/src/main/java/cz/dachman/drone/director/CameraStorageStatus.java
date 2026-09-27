package cz.dachman.drone.director;

/**
 * Platform-neutral summary of the aircraft camera's microSD state.
 *
 * DJI Mini 2 has no selectable internal media storage. New photos and videos are therefore
 * written to the microSD card in the aircraft. Recording is allowed only when the SDK reports
 * that card as inserted, initialized, formatted, writable and not full.
 */
public final class CameraStorageStatus {
    public static final int UNKNOWN_REMAINING_SECONDS = -1;

    public final boolean ready;
    public final int remainingRecordingSeconds;
    public final int remainingSpaceMB;
    public final String shortLabel;
    public final String detail;

    private CameraStorageStatus(boolean ready, int remainingRecordingSeconds,
            String shortLabel, String detail, int remainingSpaceMB) {
        this.ready = ready;
        this.remainingRecordingSeconds = remainingRecordingSeconds;
        this.shortLabel = shortLabel;
        this.detail = detail;
        this.remainingSpaceMB = remainingSpaceMB;
    }

    public static CameraStorageStatus disconnected() {
        return blocked("SD —", "Kamera ani microSD karta nejsou připojené.");
    }

    public static CameraStorageStatus checking() {
        return blocked("SD …", "Čekám na ověření microSD karty v dronu.");
    }

    public static CameraStorageStatus evaluate(boolean inserted, boolean initializing,
            boolean readOnly, boolean formatted, boolean formatting, boolean full,
            boolean verified, boolean hasError, int remainingRecordingSeconds) {
        return evaluate(inserted, initializing, readOnly, formatted, formatting, full,
            verified, hasError, remainingRecordingSeconds, -1);
    }

    public static CameraStorageStatus evaluate(boolean inserted, boolean initializing,
            boolean readOnly, boolean formatted, boolean formatting, boolean full,
            boolean verified, boolean hasError, int remainingRecordingSeconds, int remainingSpaceMB) {
        if (!inserted) return blocked("SD CHYBÍ", "V dronu není vložená microSD karta.");
        if (initializing) return blocked("SD START", "MicroSD karta se inicializuje.");
        if (formatting) return blocked("SD FORMÁT", "Probíhá formátování microSD karty.");
        if (hasError) return blocked("SD CHYBA", "DJI kamera hlásí chybu microSD karty.");
        if (readOnly) return blocked("SD ZÁMEK", "MicroSD karta je pouze pro čtení.");
        if (!formatted) return blocked("SD FORMÁT", "MicroSD karta není naformátovaná.");
        if (full || remainingRecordingSeconds == 0) {
            return blocked("SD PLNÁ", "Na microSD kartě není místo pro další video.");
        }

        int seconds = Math.max(UNKNOWN_REMAINING_SECONDS, remainingRecordingSeconds);
        String detail = verified
            ? "MicroSD karta v dronu je připravená pro záznam."
            : "MicroSD karta je zapisovatelná; DJI neoznámilo ověření její pravosti.";
        detail += capacityDetail(remainingSpaceMB);
        return new CameraStorageStatus(true, seconds, verified ? "SD OK" : "SD OK?", detail,
            remainingSpaceMB);
    }

    private static String capacityDetail(int remainingSpaceMB) {
        if (remainingSpaceMB < 0) return "\\nVolné místo: DJI SDK ho neposkytlo.";
        return String.format(java.util.Locale.getDefault(),
            "\\nVolné místo: %.1f GB", remainingSpaceMB / 1024.0d);
    }

    private static CameraStorageStatus blocked(String label, String detail) {
        return new CameraStorageStatus(false, UNKNOWN_REMAINING_SECONDS, label, detail, -1);
    }
}
