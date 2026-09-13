package cz.dachman.drone.director;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.widget.*;

/** Trajectory-review prototype. No flight-control commands are dispatched. */
public final class MainActivity extends Activity {
    private final ApprovalGate gate = new ApprovalGate();
    private CancellationSignal authentication;
    private TextView status, connection;
    private Button approve;
    private DjiConnection dji;
    private boolean workerOne = true, workerTwo = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dji = new DjiConnection(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int margin = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(margin, margin, margin, margin);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Dachman Drone Director"); title.setTextSize(25); layout.addView(title);
        TextView mode = new TextView(this); mode.setText("SIMULACE MANÉVRŮ – živé řízení dronu není zapojeno."); layout.addView(mode);
        connection = new TextView(this); connection.setText(dji.description()); layout.addView(connection);
        button(layout, "Připojit DJI (diagnostika)", () -> dji.connect(text -> runOnUiThread(() -> connection.setText(text))));
        CheckBox first = new CheckBox(this); first.setText("Worker 1 – PRIMARY (ukázkový cíl)"); first.setChecked(true); layout.addView(first);
        CheckBox second = new CheckBox(this); second.setText("Worker 2 – SECONDARY (ukázkový cíl)"); layout.addView(second);
        first.setOnCheckedChangeListener((b, checked) -> { workerOne = checked; cancel("Výběr změněn – HOLD"); });
        second.setOnCheckedChangeListener((b, checked) -> { workerTwo = checked; cancel("Výběr změněn – HOLD"); });
        status = new TextView(this); status.setText("HOLD"); status.setTextSize(18); layout.addView(status);
        button(layout, "FOLLOW / DUO FOLLOW", () -> request(workerOne && workerTwo ? "DUO FOLLOW" : "FOLLOW"));
        button(layout, "OBLET ZPRAVA", () -> request("OBLET ZPRAVA"));
        button(layout, "OBLET ZLEVA", () -> request("OBLET ZLEVA"));
        button(layout, "ODJEZD", () -> request("ODJEZD"));
        button(layout, "REŽIM LANA", () -> request("REŽIM LANA – omezená trajektorie"));
        approve = button(layout, "OVĚŘIT A SCHVÁLIT", this::authenticate); approve.setEnabled(false);
        button(layout, "HOLD", () -> cancel("HOLD"));
        button(layout, "ABORT", () -> cancel("ABORT – záměr zrušen"));
    }
    private Button button(LinearLayout layout, String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(v -> action.run()); layout.addView(b); return b;
    }
    private void request(String maneuver) {
        cancel("HOLD");
        if (!workerOne && !workerTwo) { status.setText("Vyberte alespoň jednoho pracovníka."); return; }
        gate.request(maneuver); status.setText("NÁHLED TRAJEKTORIE: " + maneuver); approve.setEnabled(true);
    }
    private void authenticate() {
        if (gate.pending() == null || authentication != null) return;
        int methods = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        if (getSystemService(BiometricManager.class).canAuthenticate(methods) != BiometricManager.BIOMETRIC_SUCCESS) {
            status.setText("Schválení zablokováno. Nastavte kód zařízení nebo biometrii."); return;
        }
        final long challenge = gate.revision();
        final String maneuver = gate.pending();
        authentication = new CancellationSignal(); approve.setEnabled(false);
        new BiometricPrompt.Builder(this).setTitle("Ověřit držitele zařízení")
            .setDescription("Schválit simulaci: " + maneuver).setAllowedAuthenticators(methods).build()
            .authenticate(authentication, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    if (challenge != gate.revision()) return;
                    authentication = null;
                    if (gate.approve(challenge, true)) status.setText("OVĚŘENO – SIMULACE: " + maneuver + ". Dronu nebyl odeslán povel.");
                    approve.setEnabled(false);
                }
                @Override public void onAuthenticationError(int code, CharSequence error) {
                    if (challenge != gate.revision()) return;
                    authentication = null; status.setText("Manévr neschválen: " + error); approve.setEnabled(gate.pending() != null);
                }
            });
    }
    private void cancel(String message) {
        gate.cancel();
        if (authentication != null) { authentication.cancel(); authentication = null; }
        if (approve != null) approve.setEnabled(false);
        if (status != null) status.setText(message);
    }
    @Override protected void onStop() { super.onStop(); cancel("HOLD – aplikace opustila popředí"); }
}
