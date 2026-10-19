> ### [Documentation in english](README_EN.md)

# ulxFixDiskOwnership.sh

Script de Bash para **comprobar y corregir el propietario de los discos y particiones
secundarios montados** en Linux.

Es muy habitual que un disco de datos montado en `/mnt/HDD`, `/mnt/datos` o similar quede
con propietario `root:root` (por montarlo desde el arranque, por un `mount` manual o por
venir de un arranque anterior), con lo que tu usuario no puede escribir en él. Este script
detecta esos discos, te muestra quién es el dueño actual y, si tú lo confirmas, se lo
asigna a tu usuario.

> Nombre anterior del script: `ulxFixFolderProperty.sh`

---

## ¿Qué hace?

1. **Comprueba** que no lo has lanzado como `root` (debe correr con tu usuario normal).
2. **Busca** los puntos de montaje que sean discos/particiones **secundarios reales**.
3. **Comprueba** el propietario actual de cada uno (`stat -c '%U:%G'`).
4. **Informa**: `Correcto` si ya eres el dueño, `Conflicto` si no lo eres.
5. **Pregunta** si quieres cambiarlo (por defecto: **No**) y, si aceptas, ejecuta
   `sudo chown -R usuario:grupo <punto de montaje>`.
6. **Espera a que pulses Intro** al final para que puedas leer la salida.

---

## ¿Cómo funciona?

### Detección de discos secundarios (el filtro)

Primero se identifica el **disco raíz**:

```bash
root_source=$(findmnt -n -o SOURCE / | sed -E 's/\[.*$//')   # p.ej. /dev/sdb6
```

El `sed` quita el sufijo de subvolumen btrfs (`/dev/sdb6[/@]` → `/dev/sdb6`), y después
cada montaje pasa dos filtros:

| Filtro | Regla | Ejemplo descartado |
|---|---|---|
| `awk` | El origen debe ser un **dispositivo de bloque real** (`/dev/…`) **distinto del disco raíz** | subvolúmenes btrfs de la raíz (`/@home`, `/@log`), `tmpfs`, `proc`, `sysfs` |
| `grep -v` | Y no estar en una **ruta del sistema**: `/`, `/sys`, `/proc`, `/dev`, `/run`, `/snap`, `/boot`, `/tmp`, `/var`, `/usr`, `/etc` | `/boot` (EFI), `/var/log`, `/run/media/...` |

Así solo quedan cosas como `/mnt/HDD` (NTFS), `/mnt/datos` (ext4) o `/media/usuario/USB`.

> ⚠️ Este filtro es a propósito: sin él, en un equipo con btrfs el script "detectaría"
> `/home`, `/root` y `/var/log` como discos con conflicto y te ofrecería un
> `chown -R` sobre el árbol del sistema.

### Comprobación del propietario

Para cada punto se leen `stat -c '%U'` y `stat -c '%G'`. Si coincide con tu usuario y tu
grupo, se marca `Correcto` y sigue al siguiente; si no, se muestra `Conflicto` y se
pregunta.

### Corrección

La respuesta acepta `s`, `S`, `si` o `SI` (cualquier otra cosa, incluido Enter, se omite):

```bash
sudo chown -R "$CURRENT_USER:$CURRENT_GROUP" "$mount_point"
```

Se comprueba el código de salida del `chown` y se informa del éxito o del error.

### Permisos

- **Revisar** los discos no necesita root: basta con leer `findmnt` y `stat`.
- **Corregir** sí pide `sudo` (solo en ese momento, y solo por cada disco al que aceptes
  arreglar).
- Si se ejecuta **como root directamente**, el script se niega y sale con código `1`.

---

## Requisitos

- Bash (Linux).
- `findmnt`, `stat`, `awk`, `sed` y `grep` (de `util-linux`/`coreutils`/`gawk`/`sed`/
  `grep`, ya vienen en casi todas las distribuciones).
- `sudo` (únicamente para el paso de corrección).

No necesita paquetes adicionales ni root para funcionar en modo lectura.

---

## Uso

```bash
./ulxFixDiskOwnership.sh     # revisa los discos secundarios y corrige si se lo pides
```

Ejecútalo **con tu usuario normal** (no con `sudo ./…`, el script lo rechaza a propósito),
de preferencia desde una terminal abierta para que funcione la pausa final de "Enter".

| Argumento | Efecto |
|---|---|
| *(ninguno)* | Modo interactivo: revisa, informa y pregunta |

### Ejemplo de salida

```
Comprobador de permisos de discos/particiones para: alejandro


Se han detectado los siguientes puntos de montaje:
--------------------------------------------------------------------------

 Disco/Ruta: /mnt/HDD
 Propietario actual: root:root
 Estado: Conflicto (El propietario es 'root' y tu usuario es 'alejandro').
   ¿Deseas cambiar el propietario de '/mnt/HDD' a 'alejandro'? (s/N): s
 Aplicando cambios con permisos de administrador...
 ¡Permisos corregidos con éxito en /mnt/HDD!
--------------------------------------------------------------------------


Proceso finalizado.

Pulsa Intro (Enter) para salir...
```

Cuando todo está bien no pregunta:

```
 Disco/Ruta: /mnt/HDD
 Propietario actual: alejandro:alejandro
 Estado: Correcto (Ya eres el propietario).
```

---

## Comportamiento y notas

- **Modo interactivo**: la respuesta por defecto es **No**; si no hay terminal (o no
  contestas), no se cambia nada.
- **Pausa final**: espera a que pulses Intro en los tres caminos de salida (ejecutado como
  root, sin discos secundarios y al final normal).
- **`chown -R` recursivo**: cambia el propietario de todo el contenido del disco, no solo
  de la raíz. Si prefieres cambiar solo la raíz, ejecuta el `chown` a mano sin la `-R`.
- **Sistemas de archivo sin permisos Unix**: en **FAT/exFAT** (`/dev/sdX1` montado con
  `vfat`/`exfat`) y en **NTFS** (`ntfs3`) no existen propietarios por archivo: el `chown`
  puede devolver éxito o fallar sin consecuencias reales. Ahí lo que manda son las opciones
  de montaje (`uid=`, `fmask=`, `dmask=`). Ver los consejos de abajo.
- **Discos USB de udisks**: los montajes automáticos en `/run/media/$USUARIO/…` ya salen
  con tu usuario, y además el filtro los descarta por ser rutas de sistema.
- **Sin salida con color**: es texto plano, se puede redirigir a un fichero sin perder nada.
- **Códigos de salida**: `0` = todo correcto o no hay discos secundarios; `1` = ejecutado
  como root.

---

## Consejos

1. **En FAT/exFAT/NTFS no uses `chown`**: monta con las opciones correctas. Por ejemplo, en
   `/etc/fstab`:

   ```
   /dev/sdb2   /mnt/HDD   ntfs3   uid=alejandro,gid=alejandro,umask=0022,nofail   0   0
   ```

   Así el disco sale ya con tu usuario sin tocar nada más.

2. **Si el disco se monta siempre en el mismo sitio**, apúntalo en `/etc/fstab` (con
   `nofail` si puede no estar conectado): ganas control de propietario, permisos y punto de
   montaje fijo.

3. **Para discos ext4/btrfs propios**, una sola vez:

   ```bash
   sudo chown -R "$USER:$USER" /mnt/datos
   ```

4. **¿`root:root` vuelve a aparecer en un NTFS?** Casi siempre es el `uid=` de montaje que
   falta, no un problema de permisos: revisa el fstab, no el chown.

---

## Estructura del script

| Sección / función | Función |
|---|---|
| `pausar()` | Espera a Intro antes de salir (los tres caminos de salida) |
| (comprobación de root) | Se niega a correr como `root` → `exit 1` |
| (filtro de montajes) | `findmnt` + `sed` (disco raíz) → `awk` (dispositivos `/dev/…` distintos de la raíz) → `grep` (excluye rutas del sistema) |
| (bucle principal) | Para cada punto de montaje: `stat` del propietario → informa → pregunta → `sudo chown -R` |
| (principal) | Saludo, aviso si no hay discos y despedida con pausa |
