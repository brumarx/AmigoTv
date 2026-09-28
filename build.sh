#!/usr/bin/env bash
# Constrói e assina TvAtalhos.apk sem Android Studio/Gradle.
# Requer (Debian/Ubuntu): aapt apksigner dalvik-exchange android-sdk-platform-23 openjdk-17-jdk-headless zipalign python3-pil
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
KEYSTORE=${KEYSTORE:-release.jks}
KS_PASS=${KS_PASS:-tvatalhos}

rm -rf build && mkdir -p build/obj
python3 tools/icons.py res/drawable

aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F build/unsigned.apk
javac -Xlint:-options --release 8 -cp "$ANDROID_JAR" -d build/obj $(find src -name '*.java')
dalvik-exchange --dex --output=build/classes.dex build/obj
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -alias tvatalhos -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TV Atalhos" >/dev/null
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --out TvAtalhos.apk build/aligned.apk
apksigner verify TvAtalhos.apk
echo "OK: $(pwd)/TvAtalhos.apk"
