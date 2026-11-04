package pt.tvatalhos;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarrega o APK oficial de uma app de TV, confirma que a assinatura é a esperada e instala-o
 * pelo instalador do sistema (PackageInstaller) — mostra o ecrã normal de confirmação do Android.
 * Não precisa de root; funciona em qualquer box.
 */
final class Installer {

    interface Progress {
        void status(String msg);
        void done(boolean ok, String msg);
    }

    static final String ACTION_RESULT = "pt.tvatalhos.INSTALL_RESULT";
    private static final String APKPURE =
            "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name=";
    private static final Pattern DL =
            Pattern.compile("https://data\\.winudf\\.com/APK/[^\\s\"']+?mu=1-1");

    private Installer() {}

    /** Já instalada? Devolve o versionName, ou null. */
    static String installedVersion(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Corre em thread própria; os callbacks voltam na thread principal. */
    static void install(final Context c, final TvApk app, final Progress cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    post(ui, cb, "A procurar a versão oficial…", false, null);
                    String url = resolveUrl(app.pkg);
                    if (url == null) throw new Exception("Não foi possível obter o link de transferência.");

                    post(ui, cb, "A transferir " + app.label + "…", false, null);
                    File apk = download(c, url, app.pkg);

                    post(ui, cb, "A confirmar a assinatura…", false, null);
                    String got = signerSha256(c, apk);
                    if (got == null || !got.equals(app.certSha256)) {
                        apk.delete();
                        throw new Exception("Assinatura NÃO corresponde à oficial. Instalação cancelada.\n"
                                + "Esperada: " + app.certSha256 + "\nObtida: " + got);
                    }

                    post(ui, cb, "A abrir o instalador do sistema…", false, null);
                    commit(c, apk);
                    post(ui, cb, null, true, "Confirme a instalação no ecrã do sistema.");
                } catch (Exception e) {
                    post(ui, cb, null, false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }, "tvatalhos-install").start();
    }

    private static void post(Handler ui, final Progress cb, final String status,
                             final boolean done, final String doneMsg) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (status != null) cb.status(status);
                if (done || doneMsg != null && status == null) cb.done(done, doneMsg);
            }
        });
    }

    private static String resolveUrl(String pkg) throws Exception {
        HttpURLConnection con = open(APKPURE + pkg);
        con.setRequestProperty("x-cv", "3172501");
        con.setRequestProperty("x-sv", "29");
        con.setRequestProperty("x-abis", "armeabi-v7a,armeabi");
        con.setRequestProperty("x-gp", "1");
        try {
            InputStream in = con.getInputStream();
            // A resposta é binária (protobuf); varremos os bytes à procura do link de transferência.
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String body = new String(bos.toByteArray(), "ISO-8859-1");
            Matcher m = DL.matcher(body);
            return m.find() ? m.group() : null;
        } finally {
            con.disconnect();
        }
    }

    private static File download(Context c, String url, String pkg) throws Exception {
        HttpURLConnection con = open(url);
        File out = new File(c.getCacheDir(), pkg + ".apk");
        InputStream in = con.getInputStream();
        OutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        os.close();
        in.close();
        con.disconnect();
        return out;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", "TvAtalhos");
        return con;
    }

    /** SHA-256 do certificado de assinatura do APK (igual ao "certificate SHA-256 digest" do apksigner). */
    @SuppressWarnings("deprecation")
    private static String signerSha256(Context c, File apk) throws Exception {
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (pi == null || pi.signatures == null || pi.signatures.length == 0) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(pi.signatures[0].toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void commit(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        OutputStream os = s.openWrite("base.apk", 0, apk.length());
        InputStream is = new java.io.FileInputStream(apk);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        s.fsync(os);
        os.close();
        is.close();
        Intent cb = new Intent(ACTION_RESULT).setPackage(c.getPackageName());
        PendingIntent pe = PendingIntent.getBroadcast(c, id, cb, PendingIntent.FLAG_UPDATE_CURRENT);
        s.commit(pe.getIntentSender());
        s.close();
    }
}
