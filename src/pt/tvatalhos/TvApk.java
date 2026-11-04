package pt.tvatalhos;

/** Uma app de TV que a Manutenção sabe instalar a partir de uma fonte pública, com a assinatura fixada. */
final class TvApk {
    final String label;
    final String pkg;
    /** SHA-256 (minúsculas, sem ":") do certificado de assinatura esperado. Protege contra APKs falsos. */
    final String certSha256;

    TvApk(String label, String pkg, String certSha256) {
        this.label = label;
        this.pkg = pkg;
        this.certSha256 = certSha256.toLowerCase();
    }

    /** Catálogo. A assinatura de cada app foi confirmada com `apksigner verify --print-certs`. */
    static final TvApk[] ALL = {
        // Netflix, Inc. (CN=PPD Builder, O="Netflix, Inc.")
        new TvApk("Netflix (TV)", "com.netflix.ninja",
                "363863596ea99241eb71b1a985553aa604de3ea3c5f0c546742390e682164e6b"),
    };
}
