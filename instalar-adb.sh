#!/usr/bin/env bash
# Instala a TV Atalhos numa TV/box pela rede (ADB) e dá a permissão para abrir apps ao ligar.
# Uso: ./instalar-adb.sh IP_DA_TV [ficheiro.apk]
# Na TV: Opções de programador → Depuração USB/ADB ligada. Na 1.ª ligação aceite o aviso no ecrã.
set -euo pipefail
cd "$(dirname "$0")"

IP=${1:?"Uso: $0 IP_DA_TV [ficheiro.apk]"}
APK=${2:-TvAtalhos.apk}
DEV="$IP:5555"
[[ "$IP" == *:* ]] && DEV="$IP"

command -v adb >/dev/null || { echo "Falta o adb (Debian/Ubuntu: sudo apt install adb)"; exit 1; }
[ -f "$APK" ] || { echo "Não encontro $APK (descarregue-o da página Releases ou corra ./build.sh)"; exit 1; }

adb connect "$DEV" >/dev/null || true
for _ in $(seq 1 30); do
    state=$(adb -s "$DEV" get-state 2>/dev/null || true)
    [ "$state" = device ] && break
    echo "À espera de ligação a $DEV… (se aparecer um aviso na TV, escolha \"Permitir sempre\")"
    sleep 3
    adb connect "$DEV" >/dev/null 2>&1 || true
done
[ "$(adb -s "$DEV" get-state 2>/dev/null)" = device ] || { echo "Não consegui ligar a $DEV"; exit 1; }

echo "Ligado: $(adb -s "$DEV" shell getprop ro.product.model | tr -d '\r') (Android $(adb -s "$DEV" shell getprop ro.build.version.release | tr -d '\r'))"
adb -s "$DEV" install --no-incremental -r "$APK"
adb -s "$DEV" shell appops set pt.tvatalhos SYSTEM_ALERT_WINDOW allow
adb -s "$DEV" shell am start -n pt.tvatalhos/.MainActivity >/dev/null
echo
echo "Pronto. A TV Atalhos está aberta na TV:"
echo "  escolha a app, mantenha OK carregado (ou MENU) → \"Abrir automaticamente ao ligar a box\"."
