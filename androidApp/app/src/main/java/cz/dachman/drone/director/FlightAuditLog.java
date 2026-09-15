package cz.dachman.drone.director;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Append-only JSONL record of AI decisions for post-flight review. */
public final class FlightAuditLog {
    private final File file;
    private long lastCommandAt;
    public FlightAuditLog(File directory) { file = new File(directory, "flight-ai-audit.jsonl"); }
    public synchronized void append(String event, String detail, FusionStatus fusion, FlightCommand command) {
        long now = System.currentTimeMillis();
        if ("AI_COMMAND".equals(event) && now - lastCommandAt < 500L) return;
        if ("AI_COMMAND".equals(event)) lastCommandAt = now;
        String line = "{\"time\":" + now + ",\"event\":\"" + escape(event)
            + "\",\"detail\":\"" + escape(detail) + "\",\"fusion\":\"" + fusion.name()
            + "\",\"pitch\":" + command.pitch + ",\"roll\":" + command.roll
            + ",\"yaw\":" + command.yaw + ",\"vertical\":" + command.vertical + "}\n";
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
    public File file() { return file; }
    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ");
    }
}
