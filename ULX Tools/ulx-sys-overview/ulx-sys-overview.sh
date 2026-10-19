#!/usr/bin/env bash
#
# ulx-sys-overview.sh
# ---------------------------------------------------------------------------
# Muestra un resumen rápido y DE SOLO LECTURA del sistema: kernel y distro,
# uptime, CPU, memoria y uso de las particiones montadas. No modifica nada.
#
# Es la herramienta de ejemplo de la estructura "ULX Tools": cada subcarpeta
# contiene el ejecutable principal (.sh), un metadata.json (nombre, descripción
# y web) y su documentación. La app la detecta y registra automáticamente como
# herramienta del sistema (no borrable desde la interfaz).
#
# Uso:
#   ./ulx-sys-overview.sh
# ---------------------------------------------------------------------------

set -u

# ---------- colores (solo si la salida es una terminal) --------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    CIAN=$'\033[36m'; NEGRITA=$'\033[1m'; RESET=$'\033[0m'
else
    CIAN=''; NEGRITA=''; RESET=''
fi

tit() { printf '\n%s%s%s\n' "$NEGRITA" "$*" "$RESET"; }

# ---------- sistema --------------------------------------------------------
tit "=== Resumen del sistema ==="
printf '%sKernel:%s  %s\n' "$CIAN" "$RESET" "$(uname -srm)"

if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    printf '%sDistro:%s  %s\n' "$CIAN" "$RESET" "${PRETTY_NAME:-desconocida}"
fi

printf '%sUptime:%s  %s\n' "$CIAN" "$RESET" "$(uptime -p 2>/dev/null || uptime)"

# ---------- CPU ------------------------------------------------------------
tit "--- CPU ---"
if [ -r /proc/cpuinfo ]; then
    modelo=$(grep -m1 'model name' /proc/cpuinfo 2>/dev/null | cut -d: -f2- | sed 's/^ *//')
    hilos=$(grep -c '^processor' /proc/cpuinfo 2>/dev/null)
    printf '  %s (%s hilos)\n' "${modelo:-desconocido}" "${hilos:-?}"
else
    printf '  (no se pudo leer /proc/cpuinfo)\n'
fi

# ---------- memoria --------------------------------------------------------
tit "--- Memoria ---"
if command -v free >/dev/null 2>&1; then
    free -h | awk 'NR==1 || /^Mem:/ || /^Swap:/'
else
    printf '  (no está disponible "free")\n'
fi

# ---------- discos montados ------------------------------------------------
tit "--- Discos montados ---"
df -h -x tmpfs -x devtmpfs -x squashfs 2>/dev/null | awk 'NR==1 || $6 ~ /^\//'

printf '\n'
