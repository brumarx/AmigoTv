package pt.tvatalhos;

import android.content.Context;
import android.content.Intent;

import java.io.DataOutputStream;

/** Desligar (sem standby) e reiniciar a box. Usa root se existir; senão, o vigia de acessibilidade. */
final class Power {
    private Power() {}

    /** Corre um comando como root. Devolve true se o su existe e aceitou o comando. */
    private static boolean su(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
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

    /** Desliga a sério (não standby). true se conseguiu iniciar o processo de desligar. */
    static boolean shutdown(Context c) {
        if (su("reboot -p") || su("svc power shutdown")) return true;
        // Sem root: pede ao vigia de acessibilidade para abrir o menu e carregar em "Desligar".
        if (Apps.guardEnabled(c)) {
            c.sendBroadcast(new Intent(GuardService.ACTION_SHUTDOWN).setPackage(c.getPackageName()));
            return true;
        }
        return false;
    }

    /** Reinicia a box. Precisa de root. */
    static boolean restart() {
        return su("reboot") || su("svc power reboot");
    }
}
