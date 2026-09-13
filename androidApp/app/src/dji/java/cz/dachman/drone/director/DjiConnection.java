package cz.dachman.drone.director;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

/** Registration and connection diagnostics only; never arms Virtual Stick. */
final class DjiConnection {
    private final Activity activity;
    private boolean registering;
    DjiConnection(Activity activity) { this.activity = activity; }
    String description() { return "DJI MSDK 4.18 – registrace zatím neproběhla."; }
    void connect(Consumer<String> status) {
        if (registering) { status.accept("Registrace již probíhá."); return; }
        String key;
        try {
            key = activity.getPackageManager().getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA)
                .metaData.getString("com.dji.sdk.API_KEY", "");
        } catch (Exception error) { status.accept("Nelze načíst konfiguraci aplikace."); return; }
        if (key.trim().isEmpty() || key.contains("__") || key.contains("$(") || key.toLowerCase().contains("placeholder")) {
            status.accept("Chybí DJI_ANDROID_APP_KEY v sestavené aplikaci."); return;
        }
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT <= 32) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 31) { permissions.add(Manifest.permission.BLUETOOTH_CONNECT); permissions.add(Manifest.permission.BLUETOOTH_SCAN); }
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (!missing.isEmpty()) {
            activity.requestPermissions(missing.toArray(new String[0]), 10);
            status.accept("Potvrďte oprávnění DJI a potom znovu stiskněte Připojit DJI."); return;
        }
        registering = true; status.accept("Registrace aplikace u DJI…");
        DJISDKManager.getInstance().registerApp(activity.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
            @Override public void onRegister(DJIError error) {
                registering = false;
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    status.accept("DJI registrace úspěšná; připojuji zařízení.");
                    DJISDKManager.getInstance().startConnectionToProduct();
                } else status.accept("DJI registrace selhala: " + (error == null ? "neznámá chyba" : error.getDescription()));
            }
            @Override public void onProductDisconnect() { status.accept("DJI odpojeno."); }
            @Override public void onProductConnect(BaseProduct product) { status.accept("DJI připojeno; živé řízení zůstává vypnuté."); }
            @Override public void onProductChanged(BaseProduct product) { status.accept("DJI produkt změněn; ověřte připojení."); }
            @Override public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {}
            @Override public void onInitProcess(DJISDKInitEvent event, int progress) {}
            @Override public void onDatabaseDownloadProgress(long current, long total) {}
        });
    }
}
