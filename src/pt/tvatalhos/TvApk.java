package pt.tvatalhos;

/** Uma app de TV que a Manutenção sabe instalar a partir de uma fonte pública, com a assinatura fixada. */
final class TvApk {
    final String label;
    final String pkg;
    /** SHA-256 (minúsculas, sem ":") do certificado de assinatura esperado. Protege contra APKs falsos. */
    final String certSha256;
    /** Se != null, descarrega deste URL direto em vez da fonte por omissão (APKPure). */
    String url;
    /** true se o {@link #url} é um .tar.gz que contém o APK lá dentro (ex.: releases do GitHub). */
    boolean tarGz;
    /** Aviso mostrado depois de instalar (ex.: uma definição a mudar). */
    String note;

    TvApk(String label, String pkg, String certSha256) {
        this.label = label;
        this.pkg = pkg;
        this.certSha256 = certSha256.toLowerCase();
    }

    TvApk from(String url, boolean tarGz) {
        this.url = url;
        this.tarGz = tarGz;
        return this;
    }

    TvApk withNote(String note) {
        this.note = note;
        return this;
    }

    /** Launcher de Android TV (autor Mickael Despesse). Usado também pela limpeza da box. */
    static final TvApk PROJECTIVY = new TvApk("Projectivy Launcher", "com.spocky.projengmenu",
            "f6697bf4082ee97511e4de07863193884a015b7ab5860430321bda1042b0aadd");
    static final String PROJECTIVY_HOME = "com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity";

    /** Catálogo. A assinatura de cada app foi confirmada com `apksigner verify --print-certs`. */
    static final TvApk[] ALL = {
        // Netflix, Inc. (CN=PPD Builder, O="Netflix, Inc.")
        new TvApk("Netflix (TV)", "com.netflix.ninja",
                "363863596ea99241eb71b1a985553aa604de3ea3c5f0c546742390e682164e6b"),
        // WireGuard LLC (O=WireGuard LLC, CN=WireGuard Android Infrastructure)
        new TvApk("WireGuard (VPN)", "com.wireguard.android",
                "84a13fa2c4e0064b0c11654b8a86574b7a9b9352a3834cee32455b061c3d4127"),
        PROJECTIVY,
        // Plezy — cliente leve de Plex/Jellyfin/Emby (Edvard Wikhall / edde746), do GitHub (.tar.gz).
        new TvApk("Plezy (Plex)", "com.edde746.plezy",
                "903b862cf5e3a0bfbad9b5e049ec3de703f83422bba9c5559a7b019716316e72")
                .from("https://github.com/edde746/plezy/releases/latest/download/plezy-android-armeabi-v7a.tar.gz", true)
                .withNote("Para teres SOM no Plezy: Configurações → Reprodução de Vídeo → desliga "
                        + "\"Passagem direta de áudio\" (esta box não reproduz surround por passthrough)."),
        // X-plore File Manager (O=Lonely Cat Games) — gestor de ficheiros com interface de TV.
        new TvApk("X-plore (Ficheiros)", "com.lonelycatgames.Xplore",
                "d46cd5fa44ae52141346da0e2c21d0cea7c6e0587b1ee5ad121d909031efec57"),
    };
}
