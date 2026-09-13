package cz.dachman.drone.director;
import android.app.Activity;
import java.util.function.Consumer;
final class DjiConnection {
    DjiConnection(Activity activity) {}
    String description() { return "Demo APK bez DJI SDK a bez připojení k dronu."; }
    void connect(Consumer<String> status) { status.accept("Toto je demo. Pro diagnostiku použijte variantu dji s vlastním DJI klíčem."); }
}
