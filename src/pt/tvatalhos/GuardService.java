package pt.tvatalhos;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Modo "só esta app": sempre que o ecrã inicial da TV aparece (HOME, sair da app, arranque),
 * volta a abrir a app escolhida. Funciona com qualquer ecrã inicial, incluindo a Google TV,
 * que não deixa ser substituída. Carregar 3 vezes em HOME (em menos de 8 s) abre a TV Atalhos.
 *
 * Desligar/ligar a sério: muitas TVs (ex.: Xiaomi) só vão para standby no botão de desligar e a app
 * fica aberta dias seguidos até encravar. Ao desligar, volta ao ecrã inicial e fecha a app; ao
 * ligar, abre-a de novo do zero (à espera da internet, se for preciso).
 */
public class GuardService extends AccessibilityService {
    private static final long RELAUNCH_DELAY_MS = 700;
    private static final long IGNORE_AFTER_RELAUNCH_MS = 1500;
    private static final long ESCAPE_WINDOW_MS = 8000;
    private static final int ESCAPE_COUNT = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> launchers = new HashSet<>();
    private long firstHomeAt;
    private int homeCount;
    private long lastHomeAt;
    private boolean screenOff;
    private boolean shuttingDown;
    private static final String[] POWER_OFF = {"Desligar", "Power off", "Shut down", "Apagar", "OK"};

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            final String kiosk = Apps.prefs(GuardService.this).getString(Apps.KIOSK, null);
            if (kiosk == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                if (shuttingDown) return;
                handler.removeCallbacksAndMessages(null);
                shuttingDown = true;
                screenOff = true;
                Log.i("TvAtalhos", "botão desligar: a desligar a TV a sério");
                startActivity(new Intent(GuardService.this, ShutdownActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("TvAtalhos", "menu de desligar: " + performGlobalAction(GLOBAL_ACTION_POWER_DIALOG));
                    }
                }, 1200);
                // Carrega em "Desligar" assim que o menu aparecer (tenta várias vezes).
                for (int k = 0; k < 15; k++) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            clickPowerOff();
                        }
                    }, 1800 + k * 600L);
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Não conseguiu desligar: deixa a TV voltar ao normal.
                        Log.w("TvAtalhos", "não deu para desligar a sério");
                        shuttingDown = false;
                        screenOff = false;
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        sendBroadcast(new Intent(ShutdownActivity.ACTION_CLOSE).setPackage(getPackageName()));
                        WaitActivity.openWhenOnline(GuardService.this, kiosk);
                    }
                }, 15000);
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                if (shuttingDown) return; // fomos nós a acender o ecrã para desligar
                handler.removeCallbacksAndMessages(null);
                screenOff = false;
                Log.i("TvAtalhos", "TV ligada: a abrir " + kiosk);
                lastHomeAt = SystemClock.elapsedRealtime();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        WaitActivity.openWhenOnline(GuardService.this, kiosk);
                    }
                }, RELAUNCH_DELAY_MS);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        launchers.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            launchers.add(ri.activityInfo.packageName);
        }
        launchers.remove(getPackageName());
        launchers.remove("com.android.tv.settings"); // FallbackHome também serve as Definições
        Log.i("TvAtalhos", "vigia ligado; ecrãs iniciais: " + launchers);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, f);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screen);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        if (shuttingDown) {
            if ("com.android.systemui".equals(e.getPackageName().toString())) clickPowerOff();
            return;
        }
        if (screenOff || !launchers.contains(e.getPackageName().toString())) return;
        SharedPreferences p = Apps.prefs(this);
        final String kiosk = p.getString(Apps.KIOSK, null);
        if (kiosk == null) return;

        // Um HOME gera vários eventos, e o ecrã inicial ainda manda outro depois de a app reabrir:
        // ignora-os para não contarem como HOME a mais.
        long now = SystemClock.elapsedRealtime();
        if (now - lastHomeAt < IGNORE_AFTER_RELAUNCH_MS + RELAUNCH_DELAY_MS) return;
        lastHomeAt = now;
        if (now - firstHomeAt > ESCAPE_WINDOW_MS) {
            firstHomeAt = now;
            homeCount = 0;
        }
        homeCount++;

        handler.removeCallbacksAndMessages(null);
        if (homeCount >= ESCAPE_COUNT) {
            homeCount = 0;
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                WaitActivity.openWhenOnline(GuardService.this, kiosk);
            }
        }, RELAUNCH_DELAY_MS);
    }

    /** No menu de desligar da TV, carrega em "Desligar" (e em "OK" se pedir confirmação). */
    private void clickPowerOff() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        for (String label : POWER_OFF) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(label)) {
                if (n.getText() == null || !label.equalsIgnoreCase(n.getText().toString().trim())) continue;
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i("TvAtalhos", "carreguei em " + label);
                    return;
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
