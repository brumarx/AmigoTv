package pt.tvatalhos;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.DataOutputStream;

/**
 * Remove o MDM/spyware e o lixo de fábrica desta box (X96Q H313 "eros-p1"), que não saem de
 * outra forma por estarem na firmware protegida. Método (precisa de root, que estas boxes já têm):
 *  Fase 1 — desliga a verificação AVB (patch de 4 bytes no vbmeta) e reinicia.
 *  Fase 2 — no arranque seguinte (verificação já desligada), monta /system em escrita, apaga o MDM
 *           e o lixo, limpa o device-owner, e reinicia.
 * SEGURANÇA: só corre se o modelo for exatamente o testado; noutro aparelho recusa (evita brick).
 */
final class BoxCleaner {
    static final String PENDING = "box_clean_pending";

    /** Modelos validados. A box tem de ser igual à que foi testada, senão NÃO mexe. */
    private static final String[] OK_DEVICES = {"eros-p1", "eros_p1"};

    /**
     * Pastas de lixo a apagar de /system (em priv-app/ e app/). MDM = o spyware device-owner.
     * CUIDADO: NUNCA incluir priv-app/AppStore — nesta firmware é a PLAY STORE (com.android.vending),
     * não a loja de lixo; apagá-la faz o Plex (e outros) crashar. A loja de lixo é app/AtvStore.
     */
    private static final String[] JUNK = {
        "priv-app/MDM", "priv-app/Tubesky",
        "priv-app/Yxtaccessibility", "priv-app/SettingsAssist",
        "app/Abupdate", "app/AtvStore", "app/DragonAgingTV", "app/DragonBox",
        "app/FactoryTestQZ", "app/MemoryClean", "app/MiracastReceiver",
        "app/TvdVideo", "app/Update", "app/VVBrowser",
    };

    private BoxCleaner() {}

    /** true se esta box é um dos modelos validados. */
    static boolean modelOk() {
        String d = android.os.Build.DEVICE;
        for (String ok : OK_DEVICES) if (ok.equalsIgnoreCase(d)) return true;
        return false;
    }

    /** Corre comandos como root. Devolve true se o su existe e os aceitou (código 0). */
    static boolean su(String... cmds) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            for (String c : cmds) os.writeBytes(c + "\n");
            os.writeBytes("exit\n");
            os.flush();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    static boolean hasRoot() {
        return su("id");
    }

    /**
     * Fase 1: desliga a verificação (vbmeta flag = 2 = VERIFICATION_DISABLED, offset 120 big-endian),
     * marca para limpar no próximo arranque e reinicia. Devolve false se o root falhar.
     */
    static boolean startClean(Context c) {
        boolean ok = su(
            "VB=$(ls /dev/block/by-name/vbmeta 2>/dev/null || echo /dev/block/mmcblk0p8)",
            "printf '\\x00\\x00\\x00\\x02' | dd of=$VB bs=1 seek=120 count=4 conv=notrunc 2>/dev/null",
            "sync");
        if (!ok) return false;
        Apps.prefs(c).edit().putBoolean(PENDING, true).apply();
        su("sync", "setprop sys.powerctl reboot");
        return true;
    }

    /**
     * Fase 2 (chamada pelo BootReceiver): se há limpeza pendente e a verificação já está desligada,
     * monta /system em escrita, apaga o MDM/lixo, limpa o device-owner e reinicia uma última vez.
     */
    static void finishClean(Context c) {
        SharedPreferences prefs = Apps.prefs(c);
        if (!prefs.getBoolean(PENDING, false)) return;
        prefs.edit().putBoolean(PENDING, false).apply(); // nunca entrar em ciclo de reinícios

        StringBuilder sb = new StringBuilder();
        sb.append("mount -o rw,remount /system 2>/dev/null; ");
        sb.append("mount -o rw,remount / 2>/dev/null; ");
        for (String j : JUNK) sb.append("rm -rf /system/").append(j).append("; ");
        sb.append("rm -f /data/system/device_owner_2.xml /data/system/device_policies.xml; ");
        sb.append("sync");

        su(sb.toString(), "sync", "setprop sys.powerctl reboot");
    }
}
