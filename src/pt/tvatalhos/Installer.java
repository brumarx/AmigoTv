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
                    post(ui, cb, "A procurar a versão…", false, null);
                    File apk;
                    if (app.url != null) {
                        post(ui, cb, "A transferir " + app.label + "…", false, null);
                        File dl = download(c, app.url, app.pkg + (app.tarGz ? ".tgz" : ".apk"));
                        apk = app.tarGz ? extractApk(c, dl, app.pkg) : dl;
                    } else {
                        String url = resolveUrl(app.pkg);
                        if (url == null) throw new Exception("Não foi possível obter o link de transferência.");
                        post(ui, cb, "A transferir " + app.label + "…", false, null);
                        apk = download(c, url, app.pkg + ".apk");
                    }

                    post(ui, cb, "A confirmar a assinatura…", false, null);
                    String got = signerSha256(c, apk);
                    if (got == null || !got.equals(app.certSha256)) {
                        apk.delete();
                        throw new Exception("Assinatura NÃO corresponde à oficial. Instalação cancelada.\n"
                                + "Esperada: " + app.certSha256 + "\nObtida: " + got);
                    }

                    post(ui, cb, "A abrir o instalador do sistema…", false, null);
                    commit(c, apk);
                    post(ui, cb, null, true, app.note != null ? app.note
                            : "Confirme a instalação no ecrã do sistema.");
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

    private static File download(Context c, String url, String fname) throws Exception {
        HttpURLConnection con = open(url);
        File out = new File(c.getCacheDir(), fname);
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

    /** Extrai o primeiro ficheiro .apk de dentro de um .tar.gz (ex.: releases Android do Plezy). */
    private static File extractApk(Context c, File targz, String pkg) throws Exception {
        File out = new File(c.getCacheDir(), pkg + ".apk");
        java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                new java.io.FileInputStream(targz), 1 << 16);
        try {
            byte[] hdr = new byte[512];
            while (readFully(gz, hdr, 512)) {
                // Nome: 100 bytes a partir do offset 0; tamanho: octal ASCII, 12 bytes no offset 124.
                int end = 0;
                while (end < 100 && hdr[end] != 0) end++;
                String name = new String(hdr, 0, end, "UTF-8");
                long size = 0;
                for (int i = 124; i < 124 + 11; i++) {
                    int d = hdr[i];
                    if (d >= '0' && d <= '7') size = size * 8 + (d - '0');
                }
                if (size == 0 && name.isEmpty()) continue; // bloco vazio (fim)
                long padded = (size + 511) & ~511L;
                if (name.toLowerCase().endsWith(".apk")) {
                    OutputStream os = new FileOutputStream(out);
                    copyN(gz, os, size);
                    os.close();
                    skipN(gz, padded - size);
                    targz.delete();
                    return out;
                }
                skipN(gz, padded);
            }
        } finally {
            gz.close();
        }
        targz.delete();
        throw new Exception("Não encontrei o APK dentro do pacote.");
    }

    private static boolean readFully(InputStream in, byte[] b, int len) throws Exception {
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) return off > 0 && off == len;
            off += r;
        }
        return true;
    }

    private static void copyN(InputStream in, OutputStream os, long n) throws Exception {
        byte[] buf = new byte[1 << 16];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) break;
            os.write(buf, 0, r);
            n -= r;
        }
    }

    private static void skipN(InputStream in, long n) throws Exception {
        byte[] buf = new byte[1 << 16];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) break;
            n -= r;
        }
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
