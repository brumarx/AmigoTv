package pt.tvatalhos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Lista todas as apps (incluindo as só-TV) e permite abrir, arrancar no boot e criar atalhos. */
public class MainActivity extends Activity {
    private static final int ACCENT = 0xFF2E86DE;
    private static final String GUARD_ADB = "adb shell settings put secure enabled_accessibility_services "
            + "pt.tvatalhos/pt.tvatalhos.GuardService";

    private static final String HELP =
            "Muitas boxes Android baratas têm um ecrã inicial que não mostra apps feitas para Android TV "
            + "(ex.: amigo tv, Disney+). Elas instalam-se, mas não dá para as adicionar ao ecrã inicial.\n\n"
            + "Esta app resolve isso:\n\n"
            + "• Lista TODAS as apps. OK abre a app.\n\n"
            + "• Opções (manter OK carregado ou botão MENU do comando):\n"
            + "   – Abrir automaticamente ao ligar a box: a app escolhida abre sozinha uns segundos depois de ligar.\n"
            + "   – Só esta app (sempre aberta): ideal para pessoas idosas. A TV passa a abrir sempre esta app: "
            + "ao ligar, ao carregar em HOME ou ao sair dela. Para voltar a esta lista, carregue 3 vezes seguidas em HOME.\n"
            + "   – Pôr no Atalho 1/2/3: aparece um ícone \"Atalho N\" na lista de apps do ecrã inicial da box. "
            + "Adicione-o ao ecrã inicial e ele abre diretamente a app escolhida.\n\n"
            + "• Abrir ao ligar precisa da permissão \"Sobrepor a outras apps\". Se a box não tiver esse ecrã, "
            + "dê-a por ADB:\nadb shell appops set pt.tvatalhos SYSTEM_ALERT_WINDOW allow\n\n"
            + "O modo \"Só esta app\" precisa de ligar o serviço \"TV Atalhos – só esta app\" em "
            + "Definições → Acessibilidade. Se a TV não tiver esse ecrã, ligue-o por ADB:\n" + GUARD_ADB + "\n\n"
            + "Dica: abra esta app pelo menos uma vez depois de a instalar, senão o Android não a deixa correr no arranque.";

    private final List<ResolveInfo> apps = new ArrayList<>();
    private GridView grid;
    private TextView status;
    private Button permButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(20), dp(32), dp(12));
        root.setBackgroundColor(0xFF15171C);

        TextView title = new TextView(this);
        title.setText("TV Atalhos");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("OK = abrir   •   Manter OK carregado ou botão MENU = opções (abrir ao ligar, atalho no ecrã inicial)");
        hint.setTextColor(0xFFAAB0BC);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(hint);

        status = new TextView(this);
        status.setTextColor(0xFFE8E8E8);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setPadding(0, dp(8), 0, dp(4));
        root.addView(status);

        Button help = new Button(this);
        help.setText("Ajuda");
        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                info("Ajuda — TV Atalhos", HELP);
            }
        });
        root.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        permButton = new Button(this);
        permButton.setText("Dar permissão para abrir apps ao ligar a box");
        permButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        root.addView(permButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(150));
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(0, dp(12), 0, 0);
        grid.setClipToPadding(false);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(ACCENT);
        sel.setCornerRadius(dp(10));
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Apps.open(MainActivity.this, pkgAt(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showOptions(pos);
                return true;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps.clear();
        apps.addAll(Apps.list(this));
        adapter.notifyDataSetChanged();
        refreshStatus();
        grid.requestFocus();
        if (grid.getSelectedItemPosition() < 0 && !apps.isEmpty()) grid.setSelection(0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !apps.isEmpty()) {
            showOptions(Math.max(0, grid.getSelectedItemPosition()));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private String pkgAt(int pos) {
        return apps.get(pos).activityInfo.packageName;
    }

    private boolean canStartOnBoot() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void refreshStatus() {
        SharedPreferences p = Apps.prefs(this);
        String auto = p.getString(Apps.AUTOSTART, null);
        StringBuilder sb = new StringBuilder("Abrir ao ligar: ");
        sb.append(auto != null ? Apps.label(this, auto) : "nenhuma");
        for (int s = 1; s <= Apps.SLOTS; s++) {
            String pkg = p.getString(Apps.slotKey(s), null);
            if (pkg != null) sb.append("   •   Atalho ").append(s).append(": ").append(Apps.label(this, pkg));
        }
        String kiosk = p.getString(Apps.KIOSK, null);
        if (kiosk != null) {
            sb.append("\nSó esta app: ").append(Apps.label(this, kiosk));
            if (!Apps.guardEnabled(this)) sb.append("  (falta ligar o serviço em Acessibilidade)");
        }
        status.setText(sb);
        permButton.setVisibility(auto != null && !canStartOnBoot() ? View.VISIBLE : View.GONE);
    }

    private void showOptions(int pos) {
        final String pkg = pkgAt(pos);
        final SharedPreferences p = Apps.prefs(this);
        final boolean isAuto = pkg.equals(p.getString(Apps.AUTOSTART, null));

        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Abrir");
        actions.add(new Runnable() { public void run() { Apps.open(MainActivity.this, pkg); } });
        labels.add(isAuto ? "Não abrir ao ligar a box" : "Abrir automaticamente ao ligar a box");
        actions.add(new Runnable() {
            public void run() {
                p.edit().putString(Apps.AUTOSTART, isAuto ? null : pkg).apply();
                refreshStatus();
                if (!isAuto && !canStartOnBoot()) requestOverlayPermission();
            }
        });
        final boolean isKiosk = pkg.equals(p.getString(Apps.KIOSK, null));
        labels.add(isKiosk ? "Desligar modo \"só esta app\"" : "Só esta app (sempre aberta)");
        actions.add(new Runnable() {
            public void run() {
                p.edit().putString(Apps.KIOSK, isKiosk ? null : pkg).apply();
                if (!isKiosk) {
                    p.edit().putString(Apps.AUTOSTART, pkg).apply();
                    if (!canStartOnBoot()) requestOverlayPermission();
                    if (!Apps.guardEnabled(MainActivity.this)) askGuard(pkg);
                }
                refreshStatus();
            }
        });
        for (int s = 1; s <= Apps.SLOTS; s++) {
            final int slot = s;
            final boolean mine = pkg.equals(p.getString(Apps.slotKey(s), null));
            String cur = p.getString(Apps.slotKey(s), null);
            labels.add(mine ? "Remover do Atalho " + s
                    : "Pôr no Atalho " + s + (cur != null ? " (substitui " + Apps.label(this, cur) + ")" : ""));
            actions.add(new Runnable() {
                public void run() {
                    Apps.setSlot(MainActivity.this, slot, mine ? null : pkg);
                    refreshStatus();
                    if (!mine) info("Atalho " + slot + " criado",
                            "O ícone \"Atalho " + slot + "\" já aparece na lista de apps do ecrã inicial da box. "
                            + "Adicione-o ao ecrã inicial: ao abrir, vai direto a " + Apps.label(MainActivity.this, pkg) + ".\n\n"
                            + "Se não aparecer logo, reinicie a box.");
                }
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(Apps.label(apps.get(pos), getPackageManager()))
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    /** Explica e abre as definições de acessibilidade para ligar o serviço "só esta app". */
    private void askGuard(String pkg) {
        final String name = Apps.label(this, pkg);
        new AlertDialog.Builder(this)
                .setTitle("Modo \"só esta app\"")
                .setMessage("Falta um passo: em Acessibilidade, ligue o serviço \"TV Atalhos – só esta app\".\n\n"
                        + "Depois disso, " + name + " abre sempre: ao ligar, ao carregar em HOME e ao sair dela.\n"
                        + "Para voltar aqui: carregue 3 vezes seguidas em HOME.\n\n"
                        + "Se a TV não tiver o ecrã de Acessibilidade, ligue por ADB:\n" + GUARD_ADB)
                .setPositiveButton("Abrir Acessibilidade", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (ActivityNotFoundException ignored) {
                        }
                    }
                })
                .setNegativeButton("Depois", null)
                .show();
    }

    private void requestOverlayPermission() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        info("Permissão necessária",
                "Esta box não tem o ecrã de permissão \"Sobrepor a outras apps\". "
                + "Sem ela, o Android não deixa abrir apps sozinho no arranque.\n\n"
                + "Dê a permissão a partir de um computador com ADB:\n\n"
                + "adb shell appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
    }

    private void info(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int pos) {
            return apps.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convert;
            if (cell == null) {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(8), dp(12), dp(8), dp(10));
                cell.setBackground(new ColorDrawable(Color.TRANSPARENT));
                ImageView icon = new ImageView(MainActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
                TextView label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, dp(6), 0, 0);
                cell.addView(label);
            }
            ResolveInfo ri = apps.get(pos);
            PackageManager pm = getPackageManager();
            String pkg = ri.activityInfo.packageName;
            SharedPreferences p = Apps.prefs(MainActivity.this);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(ri.loadIcon(pm));
            String text = Apps.label(ri, pm);
            if (pkg.equals(p.getString(Apps.AUTOSTART, null))) text = "★ " + text;
            ((TextView) cell.getChildAt(1)).setText(text);
            return cell;
        }
    };
}
