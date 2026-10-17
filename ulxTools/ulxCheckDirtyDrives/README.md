> ### [Documentation in english](README_EN.md)

# ulxCheckDirtyDrives.sh

Script de Bash para **comprobar y reparar particiones NTFS "sucias" (dirty)** desde Linux.

Es habitual que tras instalar Linux junto a Windows, o tras un apagado forzoso, Windows
deje sus particiones NTFS marcadas como *dirty* o "pendientes de chkdsk". En ese estado el
disco puede dar errores, aparecen ventanas de "comprobando disco" al arrancar Windows, o
simplemente no se puede confiar en él. Este script las detecta, te pregunta y las arregla.

> Nombre interno en la cabecera del script: `revisar-dirty.sh`

---

## ¿Qué hace?

1. **Busca** todas las particiones NTFS del equipo (o las que tú le indiques).
2. **Comprueba** cuáles están en mal estado:
   - **dirty**: el flag de volumen NTFS está activo (`ntfsinfo`).
   - **hibernada**: existe `hiberfil.sys` (arranque rápido / hibernación de Windows).
3. **Pregunta** si quieres arreglarlas (o lo hace directamente con `-y`).
4. **Repara** cada una en 4 pasos:
   1. Desmonta la partición (si estaba montada).
   2. Elimina la hibernación de Windows (`ntfs-3g -o force,remove_hiberfile`).
   3. Limpia el flag dirty (`ntfsfix -d`).
   5. Vuelve a montarla con las opciones originales (o las del `/etc/fstab`).
5. **Verifica** el resultado y muestra un consejo para que no vuelva a ocurrir.

---

## ¿Cómo funciona?

### Detección de "dirty"

La función `detectar_dirty` ejecuta `ntfsinfo -m <dispositivo>` y mira:

| Salida de `ntfsinfo`                     | Resultado |
|------------------------------------------|-----------|
| contiene `scheduled for check`           | DIRTY (el volumen ni se puede abrir) |
| `Volume Flags: 0x…` distinto de `0x0`    | DIRTY (se muestra el valor hexadecimal) |
| `permission denied` / `no such file`     | No se puede comprobar → se omite |
| cualquier otro error                     | Se trata como DIRTY (NTFS inconsistente) |

### Detección de hibernación

`detectar_hiber` lista los archivos de la raíz con `ntfsls -f` y busca `hiberfil.sys`.
La opción `-f` es necesaria porque un volumen dirty no se lista sin ella.

### Reparación (`arreglar`)

Antes de tocar nada se guarda el estado del montaje (punto de montaje, opciones, tipo de
sistema y si está en `/etc/fstab`), de forma que se puede restaurar después:

- Si el volumen **no se puede desmontar** (algún programa lo usa), se aborta ese volumen
  y no se toca nada.
- La hibernación se borra montando temporalmente con `ntfs-3g -o force,remove_hiberfile`
  en un directorio temporal y desmontándolo acto seguido.
- El flag dirty se limpia con `ntfsfix -d`, y **se vuelve a comprobar** que quedó limpio.
- Si el remontaje falla, se indica el comando manual para hacerlo.

### Permisos

- Sin argumentos necesita root (revisa discos del sistema) y se **re-ejecuta solo** con
  `sudo` pidiendo la contraseña.
- Con argumentos solo pide root si alguno es un dispositivo de bloques (`/dev/sdb3`);
  si le pasas una imagen `.img` no hace falta.

---

## Requisitos

- Bash (Linux).
- Paquete **ntfs-3g**, que aporta `ntfsinfo`, `ntfsfix`, `ntfsls` y `ntfs-3g`:

  ```bash
  sudo apt install ntfs-3g
  ```

- `lsblk` y `findmnt` (de `util-linux`, ya vienen en casi todas las distribuciones).
- Root o `sudo` (para acceder a los discos).

Si falta alguna herramienta, el script avisa y termina antes de tocar nada.

---

## Uso

```bash
./ulxCheckDirtyDrives.sh              # revisa todas las particiones NTFS
./ulxCheckDirtyDrives.sh /dev/sdb3    # revisa solo esa partición (o una imagen .img)
./ulxCheckDirtyDrives.sh -y           # no pregunta: arregla todo lo que pueda
./ulxCheckDirtyDrives.sh -h           # muestra la ayuda
```

Opciones:

| Opción     | Efecto                                              |
|------------|-----------------------------------------------------|
| *(ninguna)*| Revisa todas las particiones NTFS del equipo        |
| `partición`| Revisa solo esa partición NTFS (o imagen `.img`)    |
| `-y`, `--yes` | No pregunta, arregla todo lo que pueda          |
| `-h`, `--help` | Muestra la ayuda                                 |

### Ejemplo de salida

```
=== Comprobación de volúmenes NTFS ===
Particiones encontradas: 2

  • /dev/sdb3 (Datos)  →  montada en /mnt/datos
      ✗ DIRTY: los flags del volumen valen 0x1
      ✗ HIBERNADA: Windows dejó hiberfil.sys (arranque rápido / hibernación)

  • /dev/sdc1 (Backup)  →  no montada
      ✓ correcta

Se han encontrado 1 partición(es) con problemas.
  - /dev/sdb3  (dirty + hibernada; se perderá la sesión de Windows hibernada)

¿Quieres arreglarla(s)? [s/N]: s

Arreglando /dev/sdb3 ...
  1. Desmontando /mnt/datos ...
  2. Quitando el archivo de hibernación (hiberfil.sys) ...
  3. Limpiando el flag dirty (ntfsfix -d) ...
  ✔ flag dirty eliminado.
  4. Volviendo a montar en /mnt/datos ...
  ✔ remontada en /mnt/datos.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESULTADO: se ha podido arreglar el flag dirty en
las 1 partición(es) comprobadas. TODO CORRECTO.
```

---

## Comportamiento y notas

- **Modo interactivo**: si no hay terminal y no se pasa `-y`, no arregla nada y sale
  (para no borrar una sesión de Windows hibernada sin avisar).
- **Perder la sesión hibernada**: al eliminar `hiberfil.sys` se pierde el estado que
  Windows tenía guardado (lo que pasa al usar el arranque rápido). El script te avisa
  antes de preguntar.
- **Salida con color**: solo si la salida es una terminal. Se desactiva con la variable
  de entorno `NO_COLOR` o redirigiendo la salida a un fichero.
- **Sin particiones NTFS**: simplemente lo indica y sale sin error.
- **Códigos de salida**: `0` = todo correcto o nada que reparar; `1` = faltan herramientas,
  opción desconocida o falta de permisos.

---

## Consejos para evitar que vuelva a ocurrir

1. **Desactiva el arranque rápido en Windows** (es la causa nº 1). Abre un Símbolo del
   sistema como administrador y ejecuta:

   ```
   powercfg /h off
   ```

   Si prefieres conservar la hibernación, desactiva solo el arranque rápido en
   *Panel de control → Opciones de energía → elegir comportamiento de los botones de
   apagado*.

2. **No apagues Linux a la fuerza** con un disco NTFS montado: desmonta antes o apaga
   de forma normal.

3. Si un día Windows vuelve a quejarse de que el disco está "dirty", vuelve a ejecutar
   este script.

---

## Estructura del script

| Sección / función | Función |
|---|---|
| `usar()` | Muestra la ayuda |
| (comprobación de herramientas) | Verifica `ntfsinfo`, `ntfsfix`, `ntfsls`, `lsblk`, `findmnt` |
| (fase 1: root) | Re-ejecuta con `sudo` si hace falta |
| (fase 2: argumentos) | Parsea `-y`, `-h` y las particiones objetivo |
| `detectar_dirty()` | Devuelve 0 = limpio, 1 = dirty, 2 = no comprobable; deja el motivo en `$_motivo` |
| `detectar_hiber()` | Detecta `hiberfil.sys` |
| `etiqueta()` | Obtiene la etiqueta del volumen con `lsblk` |
| `arreglar()` | Desmonta → limpia hibernación → `ntfsfix -d` → verifica → remonta |
| (principal) | Enumera discos, informa, pide permiso y ejecuta la reparación |
