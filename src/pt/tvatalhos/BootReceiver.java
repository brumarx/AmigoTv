package pt.tvatalhos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * No arranque da box, abre a app escolhida depois de uma pequena espera (para o ecrã inicial
 * de fábrica não ficar por cima). Se o Wi-Fi ainda não ligou, mostra "A ligar à internet…" e
 * só abre a app quando houver internet. Não usa AlarmManager: algumas TVs (ex.: Xiaomi) adiam os
 * alarmes de apps de terceiros no arranque até 24 h.
 */
public class BootReceiver extends BroadcastReceiver {
    static final long DELAY_MS = 8000;

    @Override
    public void onReceive(Context c, Intent intent) {
        final Context app = c.getApplicationContext();
        // Se ficou uma limpeza da box pendente (verity já desligada), termina-a: apaga o MDM/lixo.
        BoxCleaner.finishClean(app);
        final String pkg = Apps.prefs(app).getString(Apps.AUTOSTART, null);
        if (Apps.launchIntent(app, pkg) == null) return;
        final PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    WaitActivity.openWhenOnline(app, pkg);
                } finally {
                    pending.finish();
                }
            }
        }, DELAY_MS);
    }
}
