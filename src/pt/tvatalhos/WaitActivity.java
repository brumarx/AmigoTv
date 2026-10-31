package pt.tvatalhos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ecrã "A ligar à internet…": espera que a TV tenha internet e só depois abre a app escolhida.
 * Usado no arranque (o Wi-Fi demora a ligar) e no modo "só esta app" quando não há internet.
 * Se o Wi-Fi estiver desligado, volta a ligá-lo.
 */
public class WaitActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    private static final long POLL_MS = 1000;
    /** Ligado mas a verificação de internet do Android ainda não passou: ao fim disto abre na mesma. */
    private static final long ACCEPT_UNVALIDATED_MS = 20000;
    private static final long SLOW_MS = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pkg;
    private TextView title;
    private TextView detail;
    private long startedAt;
    private long connectedSince;
    private boolean wifiTurnedOn;

    /** Abre a app já se houver internet; senão mostra este ecrã até haver. */
    static void openWhenOnline(Context c, String pkg) {
        if (pkg == null) return;
        if (online(c, true)) {
            Apps.openStart(c, pkg);
            return;
        }
        c.startActivity(new Intent(c, WaitActivity.class).putExtra(EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /** true se há rede ligada (e, se validated, com a internet confirmada pelo Android 6+). */
    static boolean online(Context c, boolean validated) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return false;
        if (!validated || Build.VERSION.SDK_INT < 23) return true;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkg = getIntent().getStringExtra(EXTRA_PKG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(48), dp(48), dp(48), dp(48));
        root.setBackgroundColor(0xFF15171C);

        title = new TextView(this);
        title.setText("A ligar à internet…");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        detail = new TextView(this);
        detail.setTextColor(0xFFC8CDD6);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(20), 0, dp(32));
        root.addView(detail);

        Button now = new Button(this);
        now.setText("Abrir já");
        now.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        now.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch();
            }
        });
        root.addView(now, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showWaiting(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String p = intent.getStringExtra(EXTRA_PKG);
        if (p != null) pkg = p;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startedAt = SystemClock.elapsedRealtime();
        connectedSince = 0;
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (online(WaitActivity.this, true)) {
                launch();
                return;
            }
            if (online(WaitActivity.this, false)) {
                if (connectedSince == 0) connectedSince = now;
                if (now - connectedSince >= ACCEPT_UNVALIDATED_MS) {
                    launch();
                    return;
                }
            } else {
                connectedSince = 0;
                ensureWifiOn();
            }
            showWaiting(now - startedAt >= SLOW_MS);
            handler.postDelayed(this, POLL_MS);
        }
    };

    private void ensureWifiOn() {
        if (wifiTurnedOn) return;
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null || wm.isWifiEnabled()) return;
        wifiTurnedOn = true;
        try {
            wm.setWifiEnabled(true);
            Log.i("TvAtalhos", "Wi-Fi estava desligado: a ligar");
        } catch (RuntimeException e) {
            Log.w("TvAtalhos", "não deu para ligar o Wi-Fi", e);
        }
    }

    private void showWaiting(boolean slow) {
        String name = pkg == null ? "A app" : Apps.label(this, pkg);
        if (slow) {
            title.setText("Sem internet");
            detail.setText("Verifique se o router está ligado.\n" + name + " abre sozinha assim que a internet voltar.");
        } else {
            title.setText("A ligar à internet…");
            detail.setText(name + " abre sozinha daqui a pouco.");
        }
    }

    private void launch() {
        handler.removeCallbacks(poll);
        if (pkg != null) Apps.openStart(this, pkg);
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
