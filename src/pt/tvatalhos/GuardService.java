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

    /** Pedido da Manutenção para desligar a TV a sério (sem root). */
    static final String ACTION_SHUTDOWN = "pt.tvatalhos.DESLIGAR";

    private final BroadcastReceiver shutdown = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            realShutdown();
        }
    };

    // --- Login automático da amigo tv (se perder a sessão) ---
    static final String ACTION_SET_LOGIN = "pt.tvatalhos.DEFINIR_LOGIN";
    private static final String LOGIN_USER = "login_user", LOGIN_PASS = "login_pass";
    private static final long LOGIN_WINDOW_MS = 10 * 60 * 1000;
    private static final int LOGIN_MAX_TRIES = 2;
    private long loginWindowStart;
    private int loginTries;
    private long lastLoginAt;
    private long lastLoginCheckAt;

    private final BroadcastReceiver saveLogin = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String u = i.getStringExtra("user"), pw = i.getStringExtra("pass");
            SharedPreferences.Editor ed = Apps.prefs(GuardService.this).edit();
            if (u == null || u.isEmpty() || pw == null || pw.isEmpty()) {
                ed.remove(LOGIN_USER).remove(LOGIN_PASS).apply();
                Log.i("TvAtalhos", "login automático apagado");
            } else {
                ed.putString(LOGIN_USER, u).putString(LOGIN_PASS, pw).apply();
                loginTries = 0;
                Log.i("TvAtalhos", "login automático gravado");
            }
        }
    };

    /** Se a amigo tv mostrar o ecrã de login, preenche utilizador e senha e carrega em Avançar. */
    private void autoLogin() {
        SharedPreferences p = Apps.prefs(this);
        String u = p.getString(LOGIN_USER, null), pw = p.getString(LOGIN_PASS, null);
        if (u == null || pw == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastLoginCheckAt < 2000 || now - lastLoginAt < 5000) return; // poupar a TV
        lastLoginCheckAt = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String id = Apps.AMIGO + ":id/";
        AccessibilityNodeInfo user = first(root, id + "et_username");
        AccessibilityNodeInfo pass = first(root, id + "et_password");
        AccessibilityNodeInfo go = first(root, id + "btn_advance");
        if (user == null || pass == null || go == null) return;
        if (now - loginWindowStart > LOGIN_WINDOW_MS) {
            loginWindowStart = now;
            loginTries = 0;
        }
        if (loginTries >= LOGIN_MAX_TRIES) return; // não insistir: a senha pode estar errada
        loginTries++;
        lastLoginAt = now;
        setText(user, u);
        setText(pass, pw);
        boolean ok = go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i("TvAtalhos", "login automático da amigo tv (tentativa " + loginTries + "): " + ok);
    }

    private static AccessibilityNodeInfo first(AccessibilityNodeInfo root, String viewId) {
        java.util.List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(viewId);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private static void setText(AccessibilityNodeInfo n, String text) {
        android.os.Bundle b = new android.os.Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            final String kiosk = Apps.prefs(GuardService.this).getString(Apps.KIOSK, null);
            if (kiosk == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                realShutdown();
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
        // Só o ADB (shell, que tem DUMP) pode gravar o login; outras apps não.
        registerReceiver(saveLogin, new IntentFilter(ACTION_SET_LOGIN), "android.permission.DUMP", null);
        registerReceiver(shutdown, new IntentFilter(ACTION_SHUTDOWN));
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(saveLogin);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(screen);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(shutdown);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        if (Apps.AMIGO.equals(e.getPackageName().toString())) {
            autoLogin();
            return;
        }
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (shuttingDown) {
            if ("com.android.systemui".equals(e.getPackageName().toString())) clickPowerOff();
            return;
        }
        if (screenOff || !launchers.contains(e.getPackageName().toString())) return;
        if (Apps.starting()) return; // a app ainda está a arrancar e o ecrã inicial pisca
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

    /**
     * Desliga a TV a sério (sem standby): abre o menu de desligar e carrega em "Desligar".
     * Serve tanto para o botão físico (SCREEN_OFF) como para o botão "Desligar" da Manutenção.
     */
    void realShutdown() {
        if (shuttingDown) return;
        final String kiosk = Apps.prefs(this).getString(Apps.KIOSK, null);
        handler.removeCallbacksAndMessages(null);
        shuttingDown = true;
        screenOff = true;
        Log.i("TvAtalhos", "a desligar a TV a sério");
        startActivity(new Intent(this, ShutdownActivity.class)
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
                if (kiosk != null) WaitActivity.openWhenOnline(GuardService.this, kiosk);
            }
        }, 15000);
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
