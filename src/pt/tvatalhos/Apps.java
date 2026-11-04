package pt.tvatalhos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Configuração guardada e utilitários para listar/abrir apps. */
final class Apps {
    static final int SLOTS = 3;
    static final String AUTOSTART = "autostart";
    static final String KIOSK = "kiosk";

    private Apps() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static String slotKey(int slot) {
        return "slot" + slot;
    }

    static ComponentName slotComponent(Context c, int slot) {
        return new ComponentName(c, "pt.tvatalhos.Atalho" + slot);
    }

    /** Atribui (ou limpa, com pkg == null) um atalho direto e mostra/esconde o ícone. */
    static void setSlot(Context c, int slot, String pkg) {
        prefs(c).edit().putString(slotKey(slot), pkg).apply();
        c.getPackageManager().setComponentEnabledSetting(slotComponent(c, slot),
                pkg != null ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** true se o serviço "só esta app" está ligado nas definições de acessibilidade. */
    static boolean guardEnabled(Context c) {
        String on = android.provider.Settings.Secure.getString(c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(c.getPackageName() + "/");
    }

    /** Todas as apps que se podem abrir, incluindo as que só têm ícone de TV (leanback). */
    static List<ResolveInfo> list(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
                all.add(ri);
            }
        }
        final PackageManager fpm = pm;
        final java.util.Map<ResolveInfo, String> labelCache = new java.util.HashMap<>();
        for (ResolveInfo ri : all) {
            labelCache.put(ri, label(ri, fpm));
        }
        Collections.sort(all, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return labelCache.get(a).compareToIgnoreCase(labelCache.get(b));
            }
        });
        return all;
    }

    /** Nome da app (algumas apps de TV dão nomes genéricos como "Launch" à atividade). */
    static String label(ResolveInfo ri, PackageManager pm) {
        return ri.activityInfo.applicationInfo.loadLabel(pm).toString();
    }

    static Intent launchIntent(Context c, String pkg) {
        if (pkg == null) return null;
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** A VPN nunca é fechada ao abrir uma app (tem de ficar sempre ligada). */
    static final String WIREGUARD = "com.wireguard.android";

    /**
     * Liberta memória: fecha as apps em segundo plano, menos a que se acabou de abrir, o WireGuard
     * e a própria TV Atalhos. Corre numa thread para não atrasar a abertura da app.
     */
    static void freeMemoryExcept(final Context c, final String keepPkg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.app.ActivityManager am =
                            (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am == null) return;
                    String self = c.getPackageName();
                    for (android.content.pm.ApplicationInfo ai :
                            c.getPackageManager().getInstalledApplications(0)) {
                        String p = ai.packageName;
                        if (p.equals(keepPkg) || p.equals(self) || p.equals(WIREGUARD)) continue;
                        try {
                            am.killBackgroundProcesses(p);
                        } catch (RuntimeException ignored) {
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }, "tvatalhos-freemem").start();
    }

    static final String AMIGO = "com.vodafone.lowi.atv.pt";
    static final int AMIGO_CHANNEL = 1;
    /** Tempo para a app arrancar sem ser reaberta (o ecrã inicial pisca durante o arranque). */
    static final long START_GRACE_MS = 25000;
    static long lastStartAt = -START_GRACE_MS;

    static boolean starting() {
        return android.os.SystemClock.elapsedRealtime() - lastStartAt < START_GRACE_MS;
    }

    /**
     * Abre a app escolhida para o arranque / modo "só esta app". A amigo tv abre logo no canal 1
     * (deep link da própria app); se não der, abre normalmente.
     */
    static boolean openStart(Context c, String pkg) {
        lastStartAt = android.os.SystemClock.elapsedRealtime();
        if (AMIGO.equals(pkg)) {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("vtv://livePlayerScreen/CLUNumber/" + AMIGO_CHANNEL + "?src=tvatalhos"))
                    .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
                freeMemoryExcept(c, pkg);
                return true;
            } catch (RuntimeException e) {
                android.util.Log.w("TvAtalhos", "canal direto falhou, a abrir normalmente", e);
            }
        }
        return open(c, pkg);
    }

    static boolean open(Context c, String pkg) {
        Intent i = launchIntent(c, pkg);
        if (i == null) {
            Toast.makeText(c, "App não encontrada: " + pkg, Toast.LENGTH_LONG).show();
            return false;
        }
        c.startActivity(i);
        freeMemoryExcept(c, pkg);
        return true;
    }
}
