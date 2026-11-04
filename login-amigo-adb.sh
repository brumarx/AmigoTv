#!/usr/bin/env bash
# Grava (ou apaga) na TV o email/senha da amigo tv para a TV Atalhos entrar sozinha se a
# amigo tv perder a sessão. Os dados ficam só na TV, na memória privada da TV Atalhos.
#
# Uso:  ./login-amigo-adb.sh [ficheiro.env]      (por omissão ../.env)
#       ./login-amigo-adb.sh --apagar [ficheiro.env]
# O .env precisa de: AMIGO_EMAIL, AMIGO_PASSWORD, TV_IP (e opcional TV_ADB_PORT).
# NUNCA ponha o .env no repositório.
set -euo pipefail
cd "$(dirname "$0")"
apagar=0
if [ "${1:-}" = "--apagar" ]; then apagar=1; shift; fi
ENV_FILE=${1:-../.env}
[ -f "$ENV_FILE" ] || { echo "Não encontrei $ENV_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
TV="${TV_IP:?falta TV_IP}:${TV_ADB_PORT:-5555}"
adb connect "$TV" >/dev/null

q() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }  # aspas para o shell da TV

if [ "$apagar" = 1 ]; then
    adb -s "$TV" shell am broadcast -a pt.tvatalhos.DEFINIR_LOGIN -p pt.tvatalhos >/dev/null
    echo "Login automático apagado da TV."
else
    : "${AMIGO_EMAIL:?falta AMIGO_EMAIL no $ENV_FILE}" "${AMIGO_PASSWORD:?falta AMIGO_PASSWORD no $ENV_FILE}"
    adb -s "$TV" shell "am broadcast -a pt.tvatalhos.DEFINIR_LOGIN -p pt.tvatalhos --es user $(q "$AMIGO_EMAIL") --es pass $(q "$AMIGO_PASSWORD")" >/dev/null
    echo "Login automático gravado na TV ($TV)."
fi
adb -s "$TV" shell 'logcat -d | grep "TvAtalhos: login automático" | tail -1'
