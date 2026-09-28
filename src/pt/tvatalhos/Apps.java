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
        Collections.sort(all, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return label(a, fpm).compareToIgnoreCase(label(b, fpm));
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

    static boolean open(Context c, String pkg) {
        Intent i = launchIntent(c, pkg);
        if (i == null) {
            Toast.makeText(c, "App não encontrada: " + pkg, Toast.LENGTH_LONG).show();
            return false;
        }
        c.startActivity(i);
        return true;
    }
}
