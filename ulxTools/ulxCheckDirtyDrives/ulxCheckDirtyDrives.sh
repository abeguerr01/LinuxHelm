#!/usr/bin/env bash
#
# revisar-dirty.sh
# ---------------------------------------------------------------------------
# Revisa las particiones NTFS del equipo y, si alguna está marcada como
# "dirty" (típicamente por apagar Linux a la fuerza con el disco montado) o
# hibernada por Windows (hiberfil.sys / arranque rápido), pregunta si quieres
# arreglarla. Si dices que sí:
#
#     1) desmonta la partición        (si estaba montada)
#     2) limpia la hibernación        (si existe hiberfil.sys)
#     3) limpia el flag dirty         (ntfsfix -d)
#     4) vuelve a montarla            (con las opciones originales)
#
# Al final indica si se ha podido quitar el flag dirty y da un consejo para
# que no vuelva a ocurrir.
#
# Uso:
#   ./revisar-dirty.sh              revisa todas las particiones NTFS
#   ./revisar-dirty.sh /dev/sdb3    revisa solo esa partición (o imagen .img)
#   ./revisar-dirty.sh -y           no pregunta: arregla todo lo que pueda
#
# ---------------------------------------------------------------------------

set -u

# ---------- colores (solo si la salida es una terminal) --------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    ROJO=$'\033[31m'; VERDE=$'\033[32m'; AMARILLO=$'\033[33m'
    CIAN=$'\033[36m'; NEGRITA=$'\033[1m'; RESET=$'\033[0m'
else
    ROJO=''; VERDE=''; AMARILLO=''; CIAN=''; NEGRITA=''; RESET=''
fi

info()  { printf '%b\n' "$*"; }
ok()    { printf '%b\n' "${VERDE}$*${RESET}"; }
aviso() { printf '%b\n' "${AMARILLO}$*${RESET}"; }
err()   { printf '%b\n' "${ROJO}$*${RESET}" >&2; }

_motivo=''

# ---------- ayuda ----------------------------------------------------------
usar() {
    cat <<EOF
Uso: $(basename "$0") [-y] [-h] [particion...]

  (sin argumentos)   revisa todas las particiones NTFS del equipo
  particion          revisa solo esa partición NTFS (o una imagen .img)
  -y, --yes          no pregunta: arregla todo lo que pueda
  -h, --help         muestra esta ayuda

Ejemplos:
  sudo $(basename "$0")
  sudo $(basename "$0") /dev/sdb3
EOF
}

# ---------- herramientas necesarias ---------------------------------------
faltan=()
for c in ntfsinfo ntfsfix ntfsls lsblk findmnt; do
    command -v "$c" >/dev/null 2>&1 || faltan+=("$c")
done
if [ "${#faltan[@]}" -gt 0 ]; then
    err "Faltan herramientas: ${faltan[*]}"
    err "Instálalas con:  sudo apt install ntfs-3g"
    exit 1
fi

# ---------- fase 1: ¿necesitamos root? -------------------------------------
# Sin argumentos revisamos los discos del sistema (necesita root).
# Con argumentos, solo hace falta root si alguno es un dispositivo de bloques.
requiere_root=0
[ "$#" -eq 0 ] && requiere_root=1
for a in "$@"; do
    case "$a" in
        -y|--yes|-h|--help) ;;
        *) [ -b "$a" ] && requiere_root=1 ;;
    esac
done

if [ "$requiere_root" -eq 1 ] && [ "$(id -u)" -ne 0 ]; then
    if ! command -v sudo >/dev/null 2>&1; then
        err "Este script necesita permisos de administrador y no encuentro 'sudo'."
        err "Ejecútalo como root:   sudo $0 $*"
        exit 1
    fi
    info "Necesito permisos de administrador; voy a pedir la contraseña de sudo..."
    exec sudo -- "$0" "$@"
fi

# ---------- fase 2: argumentos --------------------------------------------
AUTO=0
OBJETIVOS=()
for a in "$@"; do
    case "$a" in
        -y|--yes) AUTO=1 ;;
        -h|--help) usar; exit 0 ;;
        -*) err "Opción desconocida: $a"; usar; exit 1 ;;
        *) OBJETIVOS+=("$a") ;;
    esac
done

# ---------- detección ------------------------------------------------------

# detectar_dirty <dispositivo>
#   devuelve: 0 = limpio   1 = dirty   2 = no se puede comprobar
#   y deja el motivo explicado en la variable global $_motivo
#   (ojo: hay que llamarla como "detectar_dirty X; estado=$?",
#    NO con $(), porque un subshell perdería $_motivo)
detectar_dirty() {
    local dev=$1 out rc hex
    _motivo=''
    out=$(ntfsinfo -m "$dev" 2>&1); rc=$?

    # El volumen ni siquiera se abre: típicamente "scheduled for check"
    if printf '%s' "$out" | grep -qi 'scheduled for check'; then
        _motivo='está marcado como "dirty" (pendiente de chkdsk en Windows)'
        return 1
    fi

    if [ "$rc" -eq 0 ]; then
        hex=$(printf '%s\n' "$out" | sed -nE \
              's/.*Volume Flags:[[:space:]]*0[xX]([0-9a-fA-F]+).*/\1/p' | head -n1)
        if [ -n "$hex" ] && [ "$((16#$hex))" -ne 0 ]; then
            _motivo="los flags del volumen valen 0x$hex"
            return 1
        fi
        return 0
    fi

    # Sin permisos o dispositivo inexistente: no es un problema de dirty
    if printf '%s' "$out" | grep -Eqi 'permission denied|no such file|does not exist'; then
        _motivo=$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-140)
        return 2
    fi

    # Cualquier otra falla (NTFS inconsistente...): también se puede reparar
    _motivo=$(printf '%s' "$out" | grep -vi '^[[:space:]]*$' | tr '\n' ' ' | cut -c1-160)
    [ -z "$_motivo" ] && _motivo='el volumen no se puede abrir (NTFS inconsistente)'
    return 1
}

# detectar_hiber <dispositivo>  -> 0 si existe hiberfil.sys (Windows hibernado)
# (-f es necesario: un volumen dirty no se lista sin él)
detectar_hiber() {
    ntfsls -f "$1" 2>/dev/null | grep -qi 'hiberfil\.sys'
}

etiqueta() {
    lsblk -dnro LABEL "$1" 2>/dev/null | head -n1
}

# ---------- reparación -----------------------------------------------------
# arreglar <dispositivo> <¿hiberfil? 1/0>
#   devuelve 0 si al final el volumen queda limpio (y remontado si hace falta)
arreglar() {
    local dev=$1 hiber=$2
    local mp opts fst mp_fstab montado=0 en_fstab=0
    local tmp salida rc estado paso=0 previo=1

    mp=$(findmnt -n -o TARGET -S "$dev" 2>/dev/null) || mp=""
    opts=$(findmnt -n -o OPTIONS -S "$dev" 2>/dev/null) || opts=""
    fst=$(findmnt -n -o FSTYPE -S "$dev" 2>/dev/null) || fst="auto"
    mp_fstab=$(findmnt --fstab -n -o TARGET -S "$dev" 2>/dev/null) || mp_fstab=""
    [ -n "$mp" ] && montado=1
    [ -n "$mp_fstab" ] && en_fstab=1

    # estado previo: ¿estaba dirty o solo hibernado?
    detectar_dirty "$dev"; previo=$?

    # 1) desmontar --------------------------------------------------------
    paso=$((paso + 1))
    if [ "$montado" -eq 1 ]; then
        info "  ${CIAN}${paso}.${RESET} Desmontando ${NEGRITA}${mp}${RESET} ..."
        if ! umount -- "$mp" 2>/dev/null; then
            err "  ✗ No se pudo desmontar $mp (¿algún programa usándolo?)."
            err "    Ciérralo y vuelve a ejecutar el script."
            return 1
        fi
    else
        info "  ${CIAN}${paso}.${RESET} No estaba montada, no hay que desmontar."
    fi

    # 2) quitar la hibernación de Windows ---------------------------------
    if [ "$hiber" -eq 1 ]; then
        paso=$((paso + 1))
        info "  ${CIAN}${paso}.${RESET} Quitando el archivo de hibernación (hiberfil.sys) ..."
        tmp=$(mktemp -d)
        ntfs-3g -o force,remove_hiberfile "$dev" "$tmp" >/dev/null 2>&1 || true
        umount -- "$tmp" 2>/dev/null \
            || { command -v fusermount >/dev/null 2>&1 && fusermount -u "$tmp" 2>/dev/null; } \
            || true
        rmdir "$tmp" 2>/dev/null || true

        if detectar_hiber "$dev"; then
            err "  ✗ No se pudo eliminar hiberfil.sys."
        else
            ok "  ✔ hiberfil.sys eliminado."
        fi
    fi

    # limpiar el flag dirty ------------------------------------------------
    paso=$((paso + 1))
    info "  ${CIAN}${paso}.${RESET} Limpiando el flag dirty (ntfsfix -d) ..."
    salida=$(ntfsfix -d "$dev" 2>&1); rc=$?
    if [ "$rc" -ne 0 ]; then
        printf '%s\n' "$salida" | grep -vi '^[[:space:]]*$' | tail -n4 \
            | sed 's/^/      /' >&2
    fi

    # verificar ------------------------------------------------------------
    detectar_dirty "$dev"; estado=$?
    if [ "$estado" -ne 0 ]; then
        err "  ✗ No se ha podido quitar el flag dirty de $dev."
        [ -n "$_motivo" ] && err "    ($_motivo)"
        if [ "$montado" -eq 1 ]; then
            paso=$((paso + 1))
            aviso "  ${CIAN}${paso}.${RESET} Volviendo a montar $mp (aunque falle la limpieza)..."
            if [ "$en_fstab" -eq 1 ]; then mount -- "$mp" 2>/dev/null || true
            elif [ -n "$opts" ]; then mount -t "$fst" -o "$opts" "$dev" "$mp" 2>/dev/null || true
            else mount -- "$dev" "$mp" 2>/dev/null || true; fi
        fi
        return 1
    fi
    if [ "$previo" -eq 1 ]; then
        ok "  ✔ flag dirty eliminado."
    else
        ok "  ✔ el volumen no estaba dirty, ya queda correcto."
    fi

    # volver a montar ------------------------------------------------------
    if [ "$montado" -eq 1 ]; then
        paso=$((paso + 1))
        info "  ${CIAN}${paso}.${RESET} Volviendo a montar en ${NEGRITA}${mp}${RESET} ..."
        if [ "$en_fstab" -eq 1 ]; then
            mount -- "$mp" 2>/dev/null || mount -- "$dev" 2>/dev/null || true
        elif [ -n "$opts" ]; then
            mount -t "$fst" -o "$opts" "$dev" "$mp" 2>/dev/null || true
        else
            mount -- "$dev" "$mp" 2>/dev/null || true
        fi
        if ! findmnt -n -S "$dev" >/dev/null 2>&1; then
            err "  ✗ No se ha podido volver a montar $mp."
            err "    Monta a mano con:  mount $dev $mp"
            return 1
        fi
        ok "  ✔ remontada en $mp."
    fi

    return 0
}

# ---------- principal ------------------------------------------------------

# Lista de discos
DISCOS=()
if [ "${#OBJETIVOS[@]}" -gt 0 ]; then
    DISCOS=("${OBJETIVOS[@]}")
else
    while IFS= read -r d; do
        [ -n "$d" ] && DISCOS+=("$d")
    done < <(lsblk -P -p -o PATH,FSTYPE \
             | grep 'FSTYPE="ntfs"' \
             | sed -E 's/.*PATH="([^"]+)".*/\1/')
fi

if [ "${#DISCOS[@]}" -eq 0 ]; then
    info "No se ha encontrado ninguna partición NTFS. Nada que comprobar."
    exit 0
fi

info ""
info "${NEGRITA}=== Comprobación de volúmenes NTFS ===${RESET}"
info "Particiones encontradas: ${NEGRITA}${#DISCOS[@]}${RESET}"
info ""

SUCIOS=()      # dispositivos con problemas (dirty o hibernados)
DIRTY_DE=()    # 1 si ese dispositivo estaba dirty
HIBER_DE=()    # 1 si ese dispositivo tiene hiberfil.sys

for dev in "${DISCOS[@]}"; do
    if [ ! -e "$dev" ]; then
        aviso "  • $dev: no existe, se omite."
        continue
    fi

    lab=$(etiqueta "$dev"); [ -n "$lab" ] && lab=" ($lab)"
    mp=$(findmnt -n -o TARGET -S "$dev" 2>/dev/null) || mp=""

    if [ -n "$mp" ]; then
        info "  • ${NEGRITA}${dev}${RESET}${lab}  →  montada en ${CIAN}${mp}${RESET}"
    else
        info "  • ${NEGRITA}${dev}${RESET}${lab}  →  no montada"
    fi

    detectar_dirty "$dev"; estado=$?
    hiber=0; detectar_hiber "$dev" && hiber=1

    if [ "$estado" -eq 2 ]; then
        aviso "      ✗ no se puede comprobar: $_motivo"
        continue
    fi

    problemas=0
    dirty_flag=0
    if [ "$estado" -eq 1 ]; then
        err "      ✗ DIRTY: $_motivo"
        problemas=1
        dirty_flag=1
    fi
    if [ "$hiber" -eq 1 ]; then
        aviso "      ✗ HIBERNADA: Windows dejó hiberfil.sys (arranque rápido / hibernación)"
        problemas=1
    fi
    if [ "$problemas" -eq 0 ]; then
        ok "      ✓ correcta"
    fi

    if [ "$problemas" -eq 1 ]; then
        SUCIOS+=("$dev")
        DIRTY_DE+=("$dirty_flag")
        HIBER_DE+=("$hiber")
    fi
    info ""
done

# ---------- nada que arreglar ---------------------------------------------
if [ "${#SUCIOS[@]}" -eq 0 ]; then
    info "${NEGRITA}RESULTADO:${RESET} ${VERDE}ninguna partición está dirty${RESET}."
    info ""
    info "${NEGRITA}CONSEJO para que no vuelva a ocurrir:${RESET}"
    info "  • Apaga Linux de forma normal (no cortes la alimentación) mientras"
    info "    haya discos NTFS montados; un apagado brusco los deja 'dirty'."
    info "  • En Windows, desactiva el arranque rápido: Panel de control →"
    info "    Opciones de energía → elegir comportamiento de los botones →"
    info "    desactivar \"Activar arranque rápido\"."
    info ""
    exit 0
fi

# ---------- pedir permiso --------------------------------------------------
info "${NEGRITA}Se han encontrado ${#SUCIOS[@]} partición(es) con problemas.${RESET}"
for i in "${!SUCIOS[@]}"; do
    caso=""
    [ "${DIRTY_DE[$i]}" -eq 1 ] && caso="dirty"
    [ "${HIBER_DE[$i]}"  -eq 1 ] && caso="${caso:+$caso + }hibernada"
    caso="${caso:-sin limpiar}"
    extra=""
    [ "${HIBER_DE[$i]}" -eq 1 ] && extra="; se perderá la sesión de Windows hibernada"
    aviso "  - ${SUCIOS[$i]}  ($caso$extra)"
done
info ""

if [ "$AUTO" -eq 1 ]; then
    respuesta='s'
    info "${NEGRITA}Modo -y:${RESET} arreglando sin preguntar..."
else
    if [ ! -t 0 ]; then
        aviso "Sin terminal interactiva: no se arregla nada (usa -y para forzar)."
        exit 0
    fi
    printf '%b' "${NEGRITA}¿Quieres arreglarla(s)? [s/N]: ${RESET}"
    read -r respuesta || respuesta=''
fi

case "$respuesta" in
    s|S|si|sí|SI|Si|y|Y|yes|YES|Yes) ;;
    *)
        aviso "De acuerdo, no se toca nada."
        exit 0
        ;;
esac

# ---------- arreglar -------------------------------------------------------
info ""
ok_fix=0
ko_fix=0

for i in "${!SUCIOS[@]}"; do
    dev="${SUCIOS[$i]}"
    info "${NEGRITA}Arreglando ${dev} ...${RESET}"
    if arreglar "$dev" "${HIBER_DE[$i]}"; then
        ok_fix=$((ok_fix + 1))
    else
        ko_fix=$((ko_fix + 1))
    fi
    info ""
done

# ---------- resultado final ------------------------------------------------
info "${NEGRITA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
if [ "$ko_fix" -eq 0 ]; then
    ok "${NEGRITA}RESULTADO:${RESET} se ha podido arreglar el flag dirty en"
    ok "las ${ok_fix} partición(es) comprobadas. TODO CORRECTO."
elif [ "$ok_fix" -eq 0 ]; then
    err "${NEGRITA}RESULTADO:${RESET} NO se ha podido arreglar el flag dirty"
    err "en ninguna de las ${ko_fix} partición(es)."
else
    aviso "${NEGRITA}RESULTADO:${RESET} arregladas ${ok_fix}, fallidas ${ko_fix}."
fi
info ""

info "${NEGRITA}CONSEJO para que no vuelva a ocurrir:${RESET}"
info "  ${CIAN}1.${RESET} La causa nº 1 es Windows quedándose \"hibernado\" (arranque"
info "     rápido). En Windows, abre un Símbolo del sistema como"
info "     administrador y ejecuta:"
info ""
info "         ${NEGRITA}powercfg /h off${RESET}"
info ""
info "     Con eso Windows apagará de verdad y ya no dejará hiberfil.sys"
info "     ni el volumen en estado sucio. (Si usas la hibernación, mejor"
info "     desactiva solo el arranque rápido en Panel de control →"
info "     Opciones de energía → comportamiento de los botones de apagado.)"
info ""
info "  ${CIAN}2.${RESET} No apagues Linux a la fuerza con un disco NTFS montado:"
info "     desmonta antes o apaga con un apagado normal."
info ""
info "  ${CIAN}3.${RESET} Si un día Windows vuelve a quejarse de que el disco está"
info "     \"dirty\", vuelve a ejecutar este script."
info ""
