package pt.tvatalhos;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Ecrã de manutenção: instalar apps de TV (ex.: Netflix) e libertar memória. */
public class Maintenance extends Activity {
    private LinearLayout list;
    private AlertDialog busy;

    private final BroadcastReceiver installResult = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirm);
                }
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                toast("Instalado com sucesso.");
                rebuild();
            } else {
                String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                toast("Instalação falhou" + (m != null ? ": " + m : "."));
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF15171C);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(32), dp(20), dp(32), dp(20));
        sv.addView(list);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(installResult, new IntentFilter(Installer.ACTION_RESULT));
        rebuild();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(installResult); } catch (RuntimeException ignored) {}
    }

    private void rebuild() {
        list.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Manutenção");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        list.addView(title);

        section("Instalar apps de TV");
        for (final TvApk app : TvApk.ALL) {
            String v = Installer.installedVersion(this, app.pkg);
            String sub = v != null ? "Instalada (" + v + ") — toque para atualizar" : "Não instalada";
            item((v != null ? "Atualizar " : "Instalar ") + app.label, sub, new Runnable() {
                @Override public void run() { startInstall(app); }
            });
        }

        section("Desempenho");
        item("Libertar memória", "Fecha apps em segundo plano e mostra a memória livre", new Runnable() {
            @Override public void run() { freeMemory(); }
        });
        item("Definições de armazenamento", "Ver espaço e limpar caches", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); }
        });
        item("Definições de apps", "Desinstalar ou forçar paragem de apps", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_APPLICATION_SETTINGS); }
        });
    }

    private void startInstall(TvApk app) {
        showBusy("A preparar…");
        Installer.install(this, app, new Installer.Progress() {
            @Override public void status(String msg) { if (busy != null) busy.setMessage(msg); }
            @Override public void done(boolean ok, String msg) {
                hideBusy();
                if (msg != null) {
                    if (ok) toast(msg);
                    else new AlertDialog.Builder(Maintenance.this)
                            .setTitle("Não foi possível instalar")
                            .setMessage(msg).setPositiveButton("OK", null).show();
                }
            }
        });
    }

    private void freeMemory() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int killed = 0;
        for (android.content.pm.ApplicationInfo ai :
                getPackageManager().getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName())) continue;
            try { am.killBackgroundProcesses(ai.packageName); killed++; } catch (RuntimeException ignored) {}
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        toast("Memória livre: " + (mi.availMem / (1024 * 1024)) + " MB  •  apps fechadas: " + killed);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (RuntimeException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException ignored) { toast("Esta box não tem esse ecrã de definições."); }
        }
    }

    // ---- UI helpers ----

    private void section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8FA0B5);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(22), 0, dp(6));
        list.addView(t);
    }

    private void item(String title, String sub, final Runnable onClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(18), dp(14), dp(18), dp(14));
        cell.setFocusable(true);
        cell.setClickable(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF1E2129);
        bg.setCornerRadius(dp(10));
        cell.setBackground(bg);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setColor(f ? 0xFF2E86DE : 0xFF1E2129);
                d.setCornerRadius(dp(10));
                v.setBackground(d);
            }
        });
        TextView tt = new TextView(this);
        tt.setText(title);
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        cell.addView(tt);
        TextView ts = new TextView(this);
        ts.setText(sub);
        ts.setTextColor(0xFFAAB0BC);
        ts.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cell.addView(ts);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        list.addView(cell, lp);
    }

    private void showBusy(String msg) {
        busy = new AlertDialog.Builder(this).setMessage(msg).setCancelable(false).create();
        busy.show();
    }

    private void hideBusy() {
        if (busy != null) { busy.dismiss(); busy = null; }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
