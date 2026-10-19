#!/usr/bin/env bash
# =============================================================================
# empaquetar.sh — Compila LinuxHelm en TODOS los formatos de distribución:
#
#   • .jar      uber-jar: se ejecuta con cualquier Java 17+ instalado
#   • .deb      Debian / Ubuntu / Mint…
#   • .rpm      Fedora / RHEL / openSUSE / AlmaLinux…
#   • .tar.gz   portátil: trae su propio Java recortado (no necesita Java)
#   • .AppImage archivo único ejecutable en casi cualquier distro
#   • (la carpeta autocontenida con su JRE —la "app-image"— queda dentro del
#     .tar.gz y también en desktopApp/build/compose/binaries/main/app/)
#
# Todo acaba en la carpeta `dist/` del proyecto. Filosofía del proyecto:
# NADA se da por bueno sin comprobarlo — cada formato se verifica (el fichero
# existe y pesa más de cero) y el resumen final dice qué se generó, qué no y
# por qué. Si falta una herramienta opcional (rpmbuild, appimagetool), ese
# formato se omite con aviso y el resto se compila igual.
#
# Arquitectura: la actual (x86_64 u otra); jpackage no cross-compila.
#
# Uso:  ./empaquetar.sh
# =============================================================================
set -u
cd "$(dirname "$0")"

GRADLE=./gradlew
DIST=dist
ARQUITECTURA=$(uname -m)

# Colores solo si la salida es una terminal.
if [ -t 1 ]; then
    C_VERDE=$'\033[32m'; C_ROJO=$'\033[31m'; C_AMARILLO=$'\033[33m'; C_RESET=$'\033[0m'
else
    C_VERDE=""; C_ROJO=""; C_AMARILLO=""; C_RESET=""
fi

RESUMEN=()   # cada entrada: "formato | fichero | estado"
FALLOS=0     # contador de formatos que no se pudieron comprobar

# ── Comprobaciones previas ──────────────────────────────────────────────────
if [ ! -x "$GRADLE" ]; then
    echo "${C_ROJO}No se encuentra $GRADLE: ejecuta esta script desde la raíz del proyecto.${C_RESET}"
    exit 1
fi

# appimagetool: si está en ~/.local/bin pero no en el PATH (p. ej. al lanzar
# desde IntelliJ), se exporta APPIMAGETOOL para que Gradle lo encuentre igual
# (build.gradle.kts lo lee de esa variable).
if [ -z "${APPIMAGETOOL:-}" ] && [ -x "$HOME/.local/bin/appimagetool" ]; then
    export APPIMAGETOOL="$HOME/.local/bin/appimagetool"
fi

# ¿Qué podemos compilar hoy? (las ausencias solo afectan a su formato)
HAY_RPMBUILD=1;  command -v rpmbuild   >/dev/null 2>&1 || HAY_RPMBUILD=0
HAY_APPIMAGE=1
command -v "${APPIMAGETOOL:-appimagetool}" >/dev/null 2>&1 || HAY_APPIMAGE=0
HAY_FAKEROOT=1;  command -v fakeroot   >/dev/null 2>&1 || HAY_FAKEROOT=0

[ "$HAY_RPMBUILD" = 0 ] && echo "${C_AMARILLO}Aviso: no hay 'rpmbuild' → se omitirá el .rpm (instala el paquete 'rpm' de tu distro).${C_RESET}"
[ "$HAY_APPIMAGE" = 0 ] && echo "${C_AMARILLO}Aviso: no hay 'appimagetool' → se omitirá el .AppImage (https://github.com/AppImage/appimagetool).${C_RESET}"
[ "$HAY_FAKEROOT" = 0 ] && echo "${C_AMARILLO}Aviso: no hay 'fakeroot' → el .deb podría fallar (instálalo e inténtalo de nuevo).${C_RESET}"

rm -rf "$DIST"
mkdir -p "$DIST"

# ── intentar <etiqueta> <tarea-gradle> <glob-de-salida>… ────────────────────
# Ejecuta una tarea, busca su fichero de salida y lo copia a dist/.
# Registra el resultado en RESUMEN. Devuelve 0 solo si el artefacto existe y
# no está vacío — nunca se marca como correcto algo que no pudo comprobarse.
intentar() {
    local etiqueta=$1; local tarea=$2; shift 2
    echo ""
    echo "▶ ${C_AMARILLO}${etiqueta}${C_RESET}  (tarea: $tarea)"
    if ! "$GRADLE" "$tarea" --console=plain; then
        RESUMEN+=("${etiqueta}|—|${C_ROJO}FALLÓ la tarea ${tarea}${C_RESET}")
        FALLOS=$((FALLOS + 1))
        return 1
    fi
    local patron f
    for patron in "$@"; do
        # Sin comillas a propósito: queremos expandir el comodín.
        for f in $patron; do
            [ -f "$f" ] || continue
            if [ ! -s "$f" ]; then
                RESUMEN+=("${etiqueta}|$(basename "$f")|${C_ROJO}VACÍO: no es válido${C_RESET}")
                FALLOS=$((FALLOS + 1))
                return 1
            fi
            cp -f "$f" "$DIST/"
            echo "   ${C_VERDE}✓ $(basename "$f") ($(du -h "$f" | cut -f1))${C_RESET}"
            RESUMEN+=("${etiqueta}|$(basename "$f")|${C_VERDE}OK ($(du -h "$f" | cut -f1))${C_RESET}")
            return 0
        done
    done
    RESUMEN+=("${etiqueta}|—|${C_ROJO}La tarea fue bien pero no apareció el fichero${C_RESET}")
    FALLOS=$((FALLOS + 1))
    return 1
}

# ── Compilaciones ───────────────────────────────────────────────────────────
# Todas las tareas dependen de createDistributable (la app-image), así que
# Gradle la construye una vez y el resto la reutiliza.
intentar "Uber JAR"  packageUberJarForCurrentOS "desktopApp/build/compose/jars/*.jar"
intentar "Deb"       packageDeb    "desktopApp/build/compose/binaries/main/deb/*.deb"

if [ "$HAY_RPMBUILD" = 1 ]; then
    intentar "Rpm"   packageRpm    "desktopApp/build/compose/binaries/main/rpm/*.rpm"
else
    RESUMEN+=("Rpm|—|${C_AMARILLO}OMITIDO: falta rpmbuild${C_RESET}")
fi

intentar "Tar.gz"    packageTarGz  "desktopApp/build/compose/binaries/main/tar/*.tar.gz"

if [ "$HAY_APPIMAGE" = 1 ]; then
    intentar "AppImage" packageAppImage "desktopApp/build/compose/binaries/main/appimage/*.AppImage"
else
    RESUMEN+=("AppImage|—|${C_AMARILLO}OMITIDO: falta appimagetool${C_RESET}")
fi

# La carpeta autocontenida (launcher + JRE recortado) queda en build/; el
# .tar.gz la incluye, así que no se copia a dist/ para no duplicar ~200 MB.
APPCARPETA="desktopApp/build/compose/binaries/main/app"
if [ -d "$APPCARPETA" ]; then
    APPDIR=$(find "$APPCARPETA" -mindepth 1 -maxdepth 1 -type d | head -1)
    RESUMEN+=("Carpeta|$(basename "${APPDIR:-…}") (en build/, dentro del tar.gz)|${C_VERDE}OK${C_RESET}")
else
    RESUMEN+=("Carpeta|—|${C_ROJO}No se generó la app-image${C_RESET}")
    FALLOS=$((FALLOS + 1))
fi

# ── Resumen final ───────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════════════"
echo " Resumen ($ARQUITECTURA) — todo copiado en ./$DIST/"
echo "════════════════════════════════════════════════════════════════"
for linea in "${RESUMEN[@]}"; do
    IFS='|' read -r formato fichero estado <<< "$linea"
    printf " %-11s %-58s %b\n" "$formato" "$fichero" "$estado"
done
echo "════════════════════════════════════════════════════════════════"
if [ "$FALLOS" -gt 0 ]; then
    echo "${C_ROJO}Hubo ${FALLOS} formato(s) con problemas (ver arriba).${C_RESET}"
    exit 1
fi
echo "${C_VERDE}Todos los formatos disponibles se generaron y verificaron correctamente.${C_RESET}"
