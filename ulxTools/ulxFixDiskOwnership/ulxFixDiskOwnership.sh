#!/usr/bin/env bash
#
# ulxFixDiskOwnership.sh
# ---------------------------------------------------------------------------
# Script para verificar y corregir la propiedad de puntos de montaje/discos
# (propietario de discos/particiones secundarias montados)
# ---------------------------------------------------------------------------

# Espera a que el usuario pulse Enter antes de salir (para poder leer el output)
pausar() {
    echo ""
    echo -n "Pulsa Intro (Enter) para salir..."
    read -r _
    echo ""
}

# Comprobar que no se ejecute directamente como root sin $USER
if [ "$USER" = "root" ]; then
    echo "Por favor, ejecuta este script con tu usuario normal (no como root)."
    echo "El script te pedirá la contraseña de 'sudo' únicamente cuando necesite corregir permisos."
    pausar
    exit 1
fi

CURRENT_USER="$USER"
CURRENT_GROUP="$(id -gn "$USER")"

echo ""
echo "Comprobador de permisos de discos/particiones para: $CURRENT_USER"
echo ""
echo ""

# Obtener puntos de montaje de DISCOS/PARTICIONES SECUNDARIAS:
#  1) Solo montajes cuyo origen sea un dispositivo de bloque real (/dev/...),
#     descartando subvolúmenes del disco raíz y sistemas de archivos virtuales.
#  2) Excluyendo además rutas del sistema (/var, /boot, /tmp, ...).
root_source=$(findmnt -n -o SOURCE / | sed -E 's/\[.*$//')

mount_points=$(findmnt -rn -o TARGET,SOURCE \
    | awk -v root="$root_source" '{
        src = $2
        sub(/\[.*/, "", src)
        if (src ~ /^\/dev\// && src != root) print $1
      }' \
    | grep -vE '^/($|sys|proc|dev|run|snap|boot|tmp|var|usr|etc)')

if [ -z "$mount_points" ]; then
    echo "No se encontraron discos o particiones secundarias montadas."
    pausar
    exit 0
fi

echo "Se han detectado los siguientes puntos de montaje:"
echo "--------------------------------------------------------------------------"

IFS=$'\n'
for mount_point in $mount_points; do
    # Verificar si la ruta existe realmente
    if [ ! -d "$mount_point" ]; then
        continue
    fi

    # Obtener el propietario actual del punto de montaje
    owner=$(stat -c '%U' "$mount_point" 2>/dev/null)
    group=$(stat -c '%G' "$mount_point" 2>/dev/null)

    echo ""
    echo " Disco/Ruta: $mount_point"
    echo " Propietario actual: $owner:$group"

    if [ "$owner" = "$CURRENT_USER" ]; then
        echo " Estado: Correcto (Ya eres el propietario)."
    else
        echo " Estado: Conflicto (El propietario es '$owner' y tu usuario es '$CURRENT_USER')."

        # Preguntar al usuario si desea corregirlo
        read -p "   ¿Deseas cambiar el propietario de '$mount_point' a '$CURRENT_USER'? (s/N): " response
        case "$response" in
            [sS][iI]|[sS])
                echo " Aplicando cambios con permisos de administrador..."
                if sudo chown -R "$CURRENT_USER:$CURRENT_GROUP" "$mount_point"; then
                    echo " ¡Permisos corregidos con éxito en $mount_point!"
                else
                    echo " Hubo un error al intentar cambiar los permisos."
                fi
                ;;
            *)
                echo " Se ha omitido esta unidad."
                ;;
        esac
    fi
    echo "--------------------------------------------------------------------------"
done

echo ""
echo "Proceso finalizado."
