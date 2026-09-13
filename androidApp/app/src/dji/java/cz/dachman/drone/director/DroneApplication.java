package cz.dachman.drone.director;
import android.app.Application;
import android.content.Context;
public final class DroneApplication extends Application {
    @Override protected void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        com.cySdkyc.clx.Helper.install(this);
    }
}
