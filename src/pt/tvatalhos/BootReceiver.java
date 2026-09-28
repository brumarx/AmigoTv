package pt.tvatalhos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** No arranque da box, agenda a abertura da app escolhida (espera o launcher de fábrica carregar). */
public class BootReceiver extends BroadcastReceiver {
    static final long DELAY_MS = 8000;

    @Override
    public void onReceive(Context c, Intent intent) {
        Intent launch = Apps.launchIntent(c, Apps.prefs(c).getString(Apps.AUTOSTART, null));
        if (launch == null) return;
        PendingIntent pi = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + DELAY_MS, pi);
    }
}
