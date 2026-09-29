package pt.tvatalhos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * No arranque da box, abre a app escolhida depois de uma pequena espera (para o ecrã inicial
 * de fábrica não ficar por cima). Não usa AlarmManager: algumas TVs (ex.: Xiaomi) adiam os
 * alarmes de apps de terceiros no arranque até 24 h.
 */
public class BootReceiver extends BroadcastReceiver {
    static final long DELAY_MS = 8000;

    @Override
    public void onReceive(Context c, Intent intent) {
        final Context app = c.getApplicationContext();
        final Intent launch = Apps.launchIntent(app, Apps.prefs(app).getString(Apps.AUTOSTART, null));
        if (launch == null) return;
        final PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    app.startActivity(launch);
                } finally {
                    pending.finish();
                }
            }
        }, DELAY_MS);
    }
}
