package pt.tvatalhos;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Modo "só esta app": sempre que o ecrã inicial da TV aparece (HOME, sair da app, arranque),
 * volta a abrir a app escolhida. Funciona com qualquer ecrã inicial, incluindo a Google TV,
 * que não deixa ser substituída. Carregar 3 vezes em HOME (em menos de 8 s) abre a TV Atalhos.
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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        if (!launchers.contains(e.getPackageName().toString())) return;
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
                Apps.open(GuardService.this, kiosk);
            }
        }, RELAUNCH_DELAY_MS);
    }

    @Override
    public void onInterrupt() {
    }
}
