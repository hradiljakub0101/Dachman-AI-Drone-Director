package cz.dachman.drone.director;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Saves the DJI transcoded live stream as a raw H.264 elementary stream in Downloads.
 * This is a phone-side safety copy, not the camera original and not an MP4 container.
 */
final class PhoneH264Backup {
    private static final int MAX_QUEUED_PACKETS = 512;
    private final Context context;
    private final ArrayBlockingQueue<byte[]> packets = new ArrayBlockingQueue<>(MAX_QUEUED_PACKETS);
    private volatile boolean accepting;
    private volatile String lastError;
    private volatile String displayName;
    private volatile Uri contentUri;
    private Thread writer;

    PhoneH264Backup(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized boolean start() {
        if (accepting || writer != null) {
            lastError = "Předchozí záložní soubor se ještě uzavírá.";
            return false;
        }
        lastError = null;
        packets.clear();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        displayName = "Dachman_Phone_Backup_" + stamp + ".h264";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, "video/h264");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/Dachman Drone Director");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            lastError = "Android nevytvořil soubor v Downloads.";
            return false;
        }
        contentUri = uri;
        final OutputStream output;
        try {
            output = new BufferedOutputStream(resolver.openOutputStream(uri, "w"), 256 * 1024);
            if (output == null) throw new IOException("Výstupní proud souboru je prázdný.");
        } catch (Exception error) {
            lastError = "Soubor telefonu nelze otevřít: " + safeMessage(error);
            resolver.delete(uri, null, null);
            contentUri = null;
            return false;
        }
        accepting = true;
        writer = new Thread(() -> writeLoop(output, uri), "dachman-phone-h264");
        writer.setPriority(Thread.NORM_PRIORITY - 1);
        writer.start();
        return true;
    }

    void offer(byte[] source, int size) {
        if (!accepting || source == null || size <= 0) return;
        byte[] copy = new byte[Math.min(size, source.length)];
        System.arraycopy(source, 0, copy, 0, copy.length);
        if (!packets.offer(copy)) {
            lastError = "Záložní zápis nestíhá přenos; soubor může být neúplný.";
            accepting = false;
        }
    }

    synchronized void stop() {
        accepting = false;
    }

    boolean isRecording() {
        return accepting;
    }

    String displayName() {
        return displayName == null ? "—" : displayName;
    }

    String lastError() {
        return lastError;
    }

    private void writeLoop(OutputStream output, Uri uri) {
        try (OutputStream stream = output) {
            while (accepting || !packets.isEmpty()) {
                byte[] packet = packets.poll(200, TimeUnit.MILLISECONDS);
                if (packet != null) stream.write(packet);
            }
            stream.flush();
        } catch (Exception error) {
            lastError = "Zápis H.264 do telefonu selhal: " + safeMessage(error);
            accepting = false;
        } finally {
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Downloads.IS_PENDING, 0);
            try {
                context.getContentResolver().update(uri, completed, null, null);
            } catch (RuntimeException ignored) { }
            synchronized (this) {
                writer = null;
            }
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
